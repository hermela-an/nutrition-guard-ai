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
    return CompositeFuture.all(
      foods.mealCandidates("breakfast"),
      foods.mealCandidates("lunch"),
      foods.mealCandidates("dinner"),
      foods.mealCandidates("snack")
    ).map(cf -> {
      JsonObject response = new JsonObject();
      response.put("breakfast", pickSafe(cf.resultAt(0), health));
      response.put("lunch", pickSafe(cf.resultAt(1), health));
      response.put("dinner", pickSafe(cf.resultAt(2), health));
      response.put("snacks", pickSafe(cf.resultAt(3), health));
      return response;
    });
  }

  private JsonArray pickSafe(JsonArray input, JsonObject health) {
    JsonArray safe = new JsonArray();
    for (int i = 0; i < input.size() && safe.size() < 3; i++) {
      JsonObject item = input.getJsonObject(i);
      String light = TrafficLightUtil.evaluate(health, item);
      if (!"RED".equals(light)) {
        item.put("traffic_light", light);
        safe.add(item);
      }
    }
    return safe;
  }
}
