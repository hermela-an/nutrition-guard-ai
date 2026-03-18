package com.nutritionguard.app.controller;

import com.nutritionguard.app.service.ProfileService;
import com.nutritionguard.app.util.EventAddresses;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.ReplyException;
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
        .onFailure(err -> {
          err.printStackTrace();
          int status = statusFrom(err, 400);
          ctx.response().setStatusCode(status).end(messageFrom(err));
        });
    });
  }

  private int statusFrom(Throwable err, int fallback) {
    if (err instanceof ReplyException re) {
      return re.failureCode();
    }
    String msg = err.getMessage();
    if (msg != null && msg.equalsIgnoreCase("Profile not found")) {
      return 404;
    }
    return fallback;
  }

  private String messageFrom(Throwable err) {
    if (err instanceof ReplyException re) {
      String msg = re.getMessage();
      if (msg != null && !msg.isBlank()) {
        return msg;
      }
    }
    String msg = err.getMessage();
    return msg == null ? "Request failed" : msg;
  }
}
