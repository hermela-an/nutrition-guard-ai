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
    JsonObject health = profile.getJsonObject("health_metadata", new JsonObject());
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
      response.put("snacks", pickSafe(cf.resultAt(3), profile, preferHighCalories, preferLowCalories));
      return response;
    });
  }

  private JsonArray processMeal(JsonArray candidates, JsonObject profile, boolean preferHigh, boolean preferLow, JsonArray allSides) {
    JsonArray selected = pickSafe(candidates, profile, preferHigh, preferLow);
    for (int i = 0; i < selected.size(); i++) {
      JsonObject food = selected.getJsonObject(i);
      if (needsSideFood(food)) {
        food.put("sides", allSides);
      }
    }
    return selected;
  }

  private boolean needsSideFood(JsonObject food) {
    String role = food.getString("food_role", "").toLowerCase();
    return "main".equals(role) || "main_food".equals(role);
  }

  private JsonArray pickSafe(JsonArray input, JsonObject profile, boolean preferHigh, boolean preferLow) {
    JsonArray selected = new JsonArray();
    
    // 1️⃣ Green foods (primary recommendation)
    findForLight(input, profile, "SAFE", selected, preferHigh, preferLow);
    
    // 2️⃣ Yellow foods (limited / optional)
    // Only add caution foods if we couldn't find enough safe options
    if (selected.size() < 3) {
      findForLight(input, profile, "CAUTION", selected, preferHigh, preferLow);
    }
    
    // 3️⃣ Red foods (never auto-recommend)
    // "AVOID" foods are intentionally excluded and will never be added to the selected array.
    
    return selected;
  }

  private void findForLight(JsonArray input, JsonObject profile, String targetLight, JsonArray results, boolean preferHigh, boolean preferLow) {
    int start = 0;
    int end = input.size() - 1;
    int step = 1;

    if (preferHigh && !preferLow) {
      start = end;
      end = 0;
      step = -1;
    }

    for (int i = start; step > 0 ? i <= end : i >= end; i += step) {
      if (results.size() >= 3) break;
      
      JsonObject item = input.getJsonObject(i);
      // Skip if already added
      if (containsId(results, item.getInteger("id"))) continue;

      String light = TrafficLightUtil.evaluateWithProfile(profile, item);
      if (targetLight.equalsIgnoreCase(light)) {
        item.put("traffic_light", light);
        results.add(item);
      }
    }
  }

  private boolean containsId(JsonArray array, Integer id) {
    if (id == null) return false;
    for (int i = 0; i < array.size(); i++) {
      if (id.equals(array.getJsonObject(i).getInteger("id"))) return true;
    }
    return false;
  }
}
