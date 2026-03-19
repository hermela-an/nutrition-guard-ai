package com.nutritionguard.app.controller;

import com.nutritionguard.app.service.ProfileService;
import com.nutritionguard.app.service.NotificationService;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

public class ProfileController {
  private final ProfileService profiles;
  private final NotificationService notifications;

  public ProfileController(ProfileService profiles, NotificationService notifications) {
    this.profiles = profiles;
    this.notifications = notifications;
  }

  public void mount(Router router) {
    router.get("/api/profile").handler(ctx -> {
      String userId = ctx.get("user_id");
      profiles.getProfile(userId)
        .onSuccess(profile -> {
          if (profile == null) {
            // Return empty JSON to avoid noisy 404s for new users.
            ctx.response().putHeader("Content-Type", "application/json").end(new JsonObject().encode());
            return;
          }
          ctx.response().putHeader("Content-Type", "application/json").end(profile.encode());
        })
        .onFailure(err -> ctx.response().setStatusCode(500).end(err.getMessage()));
    });

    router.put("/api/profile").handler(ctx -> {
      String userId = ctx.get("user_id");
      profiles.saveProfile(userId, ctx.body().asJsonObject())
        .onSuccess(saved -> {
          maybeNotifyHealthAlerts(userId, saved);
          ctx.response().putHeader("Content-Type", "application/json").end(saved.encode());
        })
        .onFailure(err -> {
          String message = err.getMessage() == null ? err.toString() : err.getMessage();
          ctx.response().setStatusCode(400).end(message);
        });
    });
  }

  private void maybeNotifyHealthAlerts(String userId, JsonObject profile) {
    JsonObject health = profile == null ? null : profile.getJsonObject("health_metadata");
    if (health == null) return;

    boolean any = health.getBoolean("diabetes", false)
      || health.getBoolean("high_bp", false)
      || health.getBoolean("high_cholesterol", false)
      || health.getBoolean("heart_disease", false)
      || health.getBoolean("kidney_disease", false);

    if (!any) return;

    notifications.create(
      userId,
      NotificationService.TYPE_ALERT,
      "Health alerts enabled",
      "We'll flag foods that may be high in sugar, sodium, or cholesterol based on your health profile.",
      new JsonObject().put("health_metadata", health)
    ).onFailure(err -> err.printStackTrace());
  }
}
