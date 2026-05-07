package com.nutritionguard.app.controller;

import com.nutritionguard.app.repository.FoodItemRepository;
import com.nutritionguard.app.service.ProfileService;
import com.nutritionguard.app.util.TrafficLightUtil;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

public class RecommendedFoodsController {
  private final FoodItemRepository foodItems;
  private final ProfileService profiles;

  public RecommendedFoodsController(FoodItemRepository foodItems, ProfileService profiles) {
    this.foodItems = foodItems;
    this.profiles = profiles;
  }

  public void mount(Router router) {
    router.get("/api/foods/recommended").handler(ctx -> {
      String userId = ctx.get("user_id");
      String q = ctx.request().getParam("q");
      int limit = parseInt(ctx.request().getParam("limit"), 50, 1, 200);
      int offset = parseInt(ctx.request().getParam("offset"), 0, 0, 10_000);

      profiles.getProfile(userId).compose(profile -> {
        JsonObject health = profile == null
          ? new JsonObject()
          : profile.getJsonObject("health_metadata", new JsonObject()).copy().put("goal", profile.getString("goal", ""));

        return foodItems.listRecommendedBaseFoods(q, limit, offset)
          .map(items -> applyTrafficLights(items, profile));
      })
        .onSuccess(items -> ctx.response().putHeader("Content-Type", "application/json").end(items.encode()))
        .onFailure(err -> ctx.response().setStatusCode(400).end(err.getMessage()));
    });
  }

  private JsonArray applyTrafficLights(JsonArray items, JsonObject profile) {
    if (items == null) return new JsonArray();
    for (int i = 0; i < items.size(); i++) {
      JsonObject item = items.getJsonObject(i);
      if (item == null) continue;
      String light = TrafficLightUtil.evaluateWithProfile(profile, item);
      item.put("traffic_light", light);
    }
    return items;
  }

  private int parseInt(String raw, int fallback, int min, int max) {
    if (raw == null || raw.isBlank()) return fallback;
    try {
      int v = Integer.parseInt(raw);
      if (v < min) return min;
      if (v > max) return max;
      return v;
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }
}

