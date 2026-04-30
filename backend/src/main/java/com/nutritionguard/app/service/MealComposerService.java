package com.nutritionguard.app.service;

import com.nutritionguard.app.repository.FoodItemRepository;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;

public class MealComposerService {
  private final FoodItemRepository foodItems;

  public MealComposerService(FoodItemRepository foodItems) {
    this.foodItems = foodItems;
  }

  public Future<JsonObject> createMealAndCalculate(String name, String mealType, JsonArray componentsJson) {
    JsonArray components = normalizeComponents(componentsJson);
    return foodItems.createMeal(name, mealType, components)
      .compose(meal -> calculateNutrition(components).map(calc -> meal.put("nutrition", calc)));
  }

  public Future<JsonObject> getMealWithNutrition(int id) {
    return foodItems.getMeal(id).compose(meal -> {
      Object raw = meal.getValue("components_json");
      if (raw instanceof JsonArray components) {
        return calculateNutrition(components).map(calc -> meal.put("nutrition", calc));
      }

      // Support non-array meal payloads (e.g., side-option meals). We don't have enough
      // information here to compute nutrition, but we should still return the meal.
      return Future.succeededFuture(meal.put("nutrition", (Object) null));
    });
  }

  public Future<JsonObject> calculateNutrition(JsonArray componentsJson) {
    JsonArray components = normalizeComponents(componentsJson);
    if (components.isEmpty()) {
      return Future.failedFuture("Meal components must include at least one item with quantity_g > 0");
    }

    LinkedHashSet<Integer> ids = new LinkedHashSet<>();
    for (int i = 0; i < components.size(); i++) {
      JsonObject c = components.getJsonObject(i);
      Integer id = c.getInteger("food_id");
      if (id != null) ids.add(id);
    }

    Integer[] idArray = ids.toArray(new Integer[0]);
    return foodItems.fetchFoodsForNutrition(idArray).compose(map -> {
      ArrayList<String> missing = new ArrayList<>();
      for (Integer id : ids) {
        if (!map.containsKey(id)) {
          missing.add(String.valueOf(id));
        }
      }
      if (!missing.isEmpty()) {
        return Future.failedFuture("Unknown food_id(s): " + String.join(", ", missing));
      }

      double totalCalories = 0;
      double totalProtein = 0;
      double totalCarbs = 0;
      double totalFat = 0;

      JsonArray breakdown = new JsonArray();
      for (int i = 0; i < components.size(); i++) {
        JsonObject c = components.getJsonObject(i);
        if (c == null) continue;

        int foodId = c.getInteger("food_id");
        double grams = c.getDouble("quantity_g", 0.0);
        if (grams <= 0) continue;

        JsonObject food = map.get(foodId);
        if (food == null) continue;

        String type = (food.getString("type", "") == null ? "" : food.getString("type", "")).trim();
        if (!type.isEmpty() && !"food".equalsIgnoreCase(type)) {
          return Future.failedFuture("Meal components must reference base foods only. Invalid food_id=" + foodId);
        }

        Per100 per100 = per100From(food);
        if (!per100.ok) {
          return Future.failedFuture("Cannot compute per-100g nutrition for food_id=" + foodId + " (" + food.getString("name", "food") + "). Ensure it has calories_per_100g or portion_unit='g'.");
        }

        double calories = per100.calories * grams / 100.0;
        double protein = per100.protein * grams / 100.0;
        double carbs = per100.carbs * grams / 100.0;
        double fat = per100.fat * grams / 100.0;

        totalCalories += calories;
        totalProtein += protein;
        totalCarbs += carbs;
        totalFat += fat;

        breakdown.add(new JsonObject()
          .put("food_id", foodId)
          .put("name", food.getString("name"))
          .put("quantity_g", grams)
          .put("calories_kcal", roundInt(calories))
          .put("protein_g", round1(protein))
          .put("carbs_g", round1(carbs))
          .put("fat_g", round1(fat)));
      }

      JsonObject totals = new JsonObject()
        .put("calories_kcal", roundInt(totalCalories))
        .put("protein_g", round1(totalProtein))
        .put("carbs_g", round1(totalCarbs))
        .put("fat_g", round1(totalFat))
        .put("components", breakdown);

      return Future.succeededFuture(totals);
    });
  }

  private JsonArray normalizeComponents(JsonArray raw) {
    JsonArray out = new JsonArray();
    if (raw == null) return out;

    for (int i = 0; i < raw.size(); i++) {
      JsonObject c = raw.getJsonObject(i);
      if (c == null) continue;

      Integer foodId = c.getInteger("food_id", c.getInteger("id"));
      double grams = c.getDouble("quantity_g", c.getDouble("grams", 0.0));
      if (foodId == null || grams <= 0) continue;

      out.add(new JsonObject()
        .put("food_id", foodId)
        .put("quantity_g", grams));
    }
    return out;
  }

  private record Per100(boolean ok, double calories, double protein, double carbs, double fat) {
  }

  private Per100 per100From(JsonObject food) {
    Double caloriesPer100 = doubleOrNull(food.getValue("calories_per_100g"));
    Double proteinPer100 = doubleOrNull(food.getValue("protein"));
    Double carbsPer100 = doubleOrNull(food.getValue("carbs"));
    Double fatPer100 = doubleOrNull(food.getValue("fat"));

    if (caloriesPer100 != null && caloriesPer100 >= 0) {
      return new Per100(true,
        caloriesPer100,
        proteinPer100 == null ? 0 : Math.max(0, proteinPer100),
        carbsPer100 == null ? 0 : Math.max(0, carbsPer100),
        fatPer100 == null ? 0 : Math.max(0, fatPer100)
      );
    }

    String unit = (food.getString("portion_unit", "") == null ? "" : food.getString("portion_unit", "")).trim().toLowerCase();
    Double portionAmount = doubleOrNull(food.getValue("portion_amount"));
    Integer caloriesKcal = intOrNull(food.getValue("calories_kcal"));
    Double proteinG = doubleOrNull(food.getValue("protein_g"));
    Double carbsG = doubleOrNull(food.getValue("carbs_g"));
    Double fatG = doubleOrNull(food.getValue("fat_g"));

    if (!"g".equals(unit) || portionAmount == null || portionAmount <= 0 || caloriesKcal == null) {
      return new Per100(false, 0, 0, 0, 0);
    }

    double factor = 100.0 / portionAmount;
    return new Per100(true,
      Math.max(0, caloriesKcal) * factor,
      (proteinG == null ? 0 : Math.max(0, proteinG)) * factor,
      (carbsG == null ? 0 : Math.max(0, carbsG)) * factor,
      (fatG == null ? 0 : Math.max(0, fatG)) * factor
    );
  }

  private Double doubleOrNull(Object value) {
    if (value == null) return null;
    if (value instanceof Double d) return d;
    if (value instanceof Float f) return (double) f;
    if (value instanceof Integer i) return (double) i;
    if (value instanceof Long l) return (double) l;
    if (value instanceof Number n) return n.doubleValue();
    try {
      return Double.parseDouble(value.toString());
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private Integer intOrNull(Object value) {
    if (value == null) return null;
    if (value instanceof Integer i) return i;
    if (value instanceof Long l) return l.intValue();
    if (value instanceof Number n) return n.intValue();
    try {
      return Integer.parseInt(value.toString());
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private int roundInt(double v) {
    return (int) Math.round(v);
  }

  private double round1(double v) {
    return Math.round(v * 10.0) / 10.0;
  }
}
