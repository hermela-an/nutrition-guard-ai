package com.nutritionguard.app.controller;

import com.nutritionguard.app.service.ProfileService;
import com.nutritionguard.app.util.EventAddresses;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;

public class MealPlanController {
  private final Vertx vertx;
  private final ProfileService profiles;

  public MealPlanController(Vertx vertx, ProfileService profiles) {
    this.vertx = vertx;
    this.profiles = profiles;
  }

  public void mount(Router router) {
    router.get("/api/meal-plan/daily").handler(ctx -> {
      String userId = ctx.get("user_id");
      profiles.getProfile(userId).compose(profile -> {
        if (profile == null) {
          return io.vertx.core.Future.failedFuture("Profile not found");
        }
        return vertx.eventBus().<io.vertx.core.json.JsonObject>request(EventAddresses.FILTER_MEAL_PLAN, profile)
          .map(msg -> msg.body());
      }).onSuccess(plan -> ctx.response().putHeader("Content-Type", "application/json").end(plan.encode()))
        .onFailure(err -> ctx.response().setStatusCode(400).end(err.getMessage()));
    });
  }
}
