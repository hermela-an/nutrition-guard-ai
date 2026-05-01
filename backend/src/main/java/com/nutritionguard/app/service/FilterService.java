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
      response.put("breakfast", processMeal(cf.resultAt(0), health, preferHighCalories, preferLowCalories, sides));
      response.put("lunch", processMeal(cf.resultAt(1), health, preferHighCalories, preferLowCalories, sides));
      response.put("dinner", processMeal(cf.resultAt(2), health, preferHighCalories, preferLowCalories, sides));
      response.put("snacks", pickSafe(cf.resultAt(3), health, preferHighCalories, preferLowCalories));
      return response;
    });
  }

  private JsonArray processMeal(JsonArray candidates, JsonObject health, boolean preferHigh, boolean preferLow, JsonArray allSides) {
    JsonArray selected = pickSafe(candidates, health, preferHigh, preferLow);
    for (int i = 0; i < selected.size(); i++) {
      JsonObject food = selected.getJsonObject(i);
      if (needsSideFood(food)) {
        food.put("sides", allSides);
      }
    }
    return selected;
  }

  private boolean needsSideFood(JsonObject food) {
    String name = food.getString("food_name", "").toLowerCase();
    String role = food.getString("food_role", "").toLowerCase();
    
    // Only main foods typically need sides
    if (!"main".equals(role) && !"main_food".equals(role)) {
      return false;
    }
    
    // Ethiopian stews typically end with "Wat"
    if (name.endsWith(" wat")) {
      return true;
    }
    
    return false;
  }

  private JsonArray pickSafe(JsonArray input, JsonObject health, boolean preferHigh, boolean preferLow) {
    JsonArray safe = new JsonArray();
    int start = 0;
    int end = input.size() - 1;
    int step = 1;

    if (preferHigh && !preferLow) {
      start = end;
      end = 0;
      step = -1;
    }

    for (int i = start; step > 0 ? i <= end : i >= end; i += step) {
      if (safe.size() >= 3) {
        break;
      }
      JsonObject item = input.getJsonObject(i);
      String light = TrafficLightUtil.evaluate(health, item);
      if ("SAFE".equalsIgnoreCase(light)) {
        item.put("traffic_light", light);
        safe.add(item);
      }
    }
    return safe;
  }
}
