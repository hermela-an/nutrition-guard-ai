package com.nutritionguard.app.service;

import com.nutritionguard.app.repository.FoodRepository;
import com.nutritionguard.app.util.TrafficLightUtil;
import io.vertx.core.Future;
import io.vertx.core.CompositeFuture;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class FilterService {
  private final FoodRepository foods;

  public FilterService(FoodRepository foods) {
    this.foods = foods;
  }

  public Future<JsonObject> generateMealPlan(JsonObject profile) {
    String goal = profile.getString("goal", "").toLowerCase();
    boolean preferHighCalories = "weight_gain".equals(goal);
    boolean preferLowCalories = "weight_loss".equals(goal);

    return CompositeFuture.all(
      foods.mealCandidates("breakfast"),
      foods.mealCandidates("lunch"),
      foods.mealCandidates("dinner"),
      foods.mealCandidates("snack"),
      foods.findSideFoods()
    ).map(cf -> {
      JsonArray sides = cf.resultAt(4);
      JsonObject response = new JsonObject();
      response.put("goal", goal);
      response.put("breakfast", processMeal(cf.resultAt(0), profile, preferHighCalories, preferLowCalories, sides));
      response.put("lunch", processMeal(cf.resultAt(1), profile, preferHighCalories, preferLowCalories, sides));
      response.put("dinner", processMeal(cf.resultAt(2), profile, preferHighCalories, preferLowCalories, sides));
      response.put("snacks", pickSnacks(cf.resultAt(3), profile, preferHighCalories, preferLowCalories));
      return response;
    });
  }

  /**
   * Assembles a realistic, cohesive meal:
   * 1. Selects exactly 1 Primary Main Dish (prioritizing SAFE, falling back to at most 1 CAUTION).
   * 2. Attaches optional side choices (e.g. Injera, Bread) if the dish is a main food.
   * 3. Pairs with at most 1 optional complementary vegetable or light side (if available).
   * 4. Never stacks multiple heavy main entrees (e.g. Instant Noodles + Rice and Salmon).
   * 5. Strictly excludes any food evaluated as AVOID.
   */
  private JsonArray processMeal(JsonArray candidates, JsonObject profile, boolean preferHigh, boolean preferLow, JsonArray allSides) {
    JsonArray selected = new JsonArray();
    if (candidates == null || candidates.isEmpty()) {
      return selected;
    }

    int start = 0;
    int end = candidates.size() - 1;
    int step = 1;

    if (preferHigh && !preferLow) {
      start = end;
      end = 0;
      step = -1;
    }

    // Phase 1: Select exactly 1 Primary Main Dish (SAFE first, then single best CAUTION fallback)
    JsonObject primaryMain = null;
    String primaryLight = null;

    // Search for a SAFE main dish
    for (int i = start; step > 0 ? i <= end : i >= end; i += step) {
      JsonObject item = candidates.getJsonObject(i);
      if (!isMainDish(item)) continue;

      String light = TrafficLightUtil.evaluateWithProfile(profile, item);
      if ("SAFE".equalsIgnoreCase(light)) {
        primaryMain = item.copy();
        primaryLight = "SAFE";
        break;
      }
    }

    // Fallback: If no SAFE main dish exists, select at most 1 CAUTION main dish
    if (primaryMain == null) {
      for (int i = start; step > 0 ? i <= end : i >= end; i += step) {
        JsonObject item = candidates.getJsonObject(i);
        if (!isMainDish(item)) continue;

        String light = TrafficLightUtil.evaluateWithProfile(profile, item);
        if ("CAUTION".equalsIgnoreCase(light)) {
          primaryMain = item.copy();
          primaryLight = "CAUTION";
          break;
        }
      }
    }

    // If still null (e.g. candidates only had sides/veggies), take any safe candidate
    if (primaryMain == null) {
      for (int i = start; step > 0 ? i <= end : i >= end; i += step) {
        JsonObject item = candidates.getJsonObject(i);
        String light = TrafficLightUtil.evaluateWithProfile(profile, item);
        if ("SAFE".equalsIgnoreCase(light) || "CAUTION".equalsIgnoreCase(light)) {
          primaryMain = item.copy();
          primaryLight = light;
          break;
        }
      }
    }

    // If a primary dish was identified, add it and attach side options if needed
    if (primaryMain != null) {
      primaryMain.put("traffic_light", primaryLight);
      if (needsSideFood(primaryMain) && allSides != null && !allSides.isEmpty()) {
        primaryMain.put("sides", allSides);
      }
      selected.add(primaryMain);
    }

    // Phase 2: Pair with at most 1 complementary vegetable or light side (never another heavy entree)
    if (primaryMain != null) {
      Integer mainId = primaryMain.getInteger("id");
      for (int i = start; step > 0 ? i <= end : i >= end; i += step) {
        JsonObject item = candidates.getJsonObject(i);
        Integer itemId = item.getInteger("id");
        if (mainId != null && mainId.equals(itemId)) continue;

        // Must be a complementary vegetable or side dish
        if (!isVegetableOrSide(item)) continue;

        String light = TrafficLightUtil.evaluateWithProfile(profile, item);
        if ("SAFE".equalsIgnoreCase(light)) {
          JsonObject sideItem = item.copy();
          sideItem.put("traffic_light", "SAFE");
          selected.add(sideItem);
          break; // At most 1 complementary vegetable/side
        }
      }
    }

    return selected;
  }

  /**
   * Selects snack items (1-2 light items), strictly excluding AVOID.
   */
  private JsonArray pickSnacks(JsonArray candidates, JsonObject profile, boolean preferHigh, boolean preferLow) {
    JsonArray selected = new JsonArray();
    if (candidates == null || candidates.isEmpty()) {
      return selected;
    }

    int start = 0;
    int end = candidates.size() - 1;
    int step = 1;

    if (preferHigh && !preferLow) {
      start = end;
      end = 0;
      step = -1;
    }

    // 1. Pick SAFE snacks (up to 2)
    for (int i = start; step > 0 ? i <= end : i >= end; i += step) {
      if (selected.size() >= 2) break;
      JsonObject item = candidates.getJsonObject(i);
      if (containsId(selected, item.getInteger("id"))) continue;

      String light = TrafficLightUtil.evaluateWithProfile(profile, item);
      if ("SAFE".equalsIgnoreCase(light)) {
        JsonObject safeCopy = item.copy();
        safeCopy.put("traffic_light", "SAFE");
        selected.add(safeCopy);
      }
    }

    // 2. Fallback: at most 1 CAUTION snack if no safe snack was found
    if (selected.isEmpty()) {
      for (int i = start; step > 0 ? i <= end : i >= end; i += step) {
        JsonObject item = candidates.getJsonObject(i);
        String light = TrafficLightUtil.evaluateWithProfile(profile, item);
        if ("CAUTION".equalsIgnoreCase(light)) {
          JsonObject cautionCopy = item.copy();
          cautionCopy.put("traffic_light", "CAUTION");
          selected.add(cautionCopy);
          break;
        }
      }
    }

    return selected;
  }

  private boolean isMainDish(JsonObject food) {
    if (food == null) return false;
    String role = food.getString("food_role", "").toLowerCase().trim();
    if (role.contains("side")) return false;

    String category = food.getString("category", "").toLowerCase().trim();
    int calories = food.getInteger("calories", food.getInteger("calories_kcal", 0));

    // A low-calorie standalone vegetable dish (e.g. plain spinach, gomen) is treated as a vegetable/side
    if (category.contains("vegetable") && calories < 200) {
      return false;
    }

    return true;
  }

  private boolean isVegetableOrSide(JsonObject food) {
    if (food == null) return false;
    String role = food.getString("food_role", "").toLowerCase().trim();
    if (role.contains("side")) return true;

    String category = food.getString("category", "").toLowerCase().trim();
    return category.contains("vegetable");
  }

  private boolean needsSideFood(JsonObject food) {
    String role = food.getString("food_role", "").toLowerCase();
    return "main".equals(role) || "main_food".equals(role);
  }

  private boolean containsId(JsonArray array, Integer id) {
    if (id == null) return false;
    for (int i = 0; i < array.size(); i++) {
      if (id.equals(array.getJsonObject(i).getInteger("id"))) return true;
    }
    return false;
  }
}
