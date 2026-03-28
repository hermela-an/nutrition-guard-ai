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
      foods.mealCandidates("snack")
    ).map(cf -> {
      JsonObject response = new JsonObject();
      response.put("goal", goal);
      response.put("breakfast", pickSafe(cf.resultAt(0), health, preferHighCalories, preferLowCalories));
      response.put("lunch", pickSafe(cf.resultAt(1), health, preferHighCalories, preferLowCalories));
      response.put("dinner", pickSafe(cf.resultAt(2), health, preferHighCalories, preferLowCalories));
      response.put("snacks", pickSafe(cf.resultAt(3), health, preferHighCalories, preferLowCalories));
      return response;
    });
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
