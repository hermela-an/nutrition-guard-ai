package com.nutritionguard.app.controller;

import com.nutritionguard.app.repository.FoodSearchEventRepository;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

public class AdminAnalyticsController {
  private final FoodSearchEventRepository foodSearchEvents;

  public AdminAnalyticsController(FoodSearchEventRepository foodSearchEvents) {
    this.foodSearchEvents = foodSearchEvents;
  }

  public void mount(Router router) {
    router.get("/api/admin/analytics/food-searches").handler(ctx -> {
      int days = parseInt(ctx.request().getParam("days"), 14, 3, 60);
      foodSearchEvents.dailyCounts(days)
        .onSuccess(items -> ctx.response().putHeader("Content-Type", "application/json")
          .end(new JsonObject().put("days", items).put("window_days", days).encode()))
        .onFailure(err -> ctx.response().setStatusCode(400).end(err.getMessage()));
    });
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

