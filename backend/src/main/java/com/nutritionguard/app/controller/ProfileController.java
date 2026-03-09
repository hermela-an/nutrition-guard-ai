package com.nutritionguard.app.controller;

import com.nutritionguard.app.service.ProfileService;
import io.vertx.ext.web.Router;

public class ProfileController {
  private final ProfileService profiles;

  public ProfileController(ProfileService profiles) {
    this.profiles = profiles;
  }

  public void mount(Router router) {
    router.get("/api/profile").handler(ctx -> {
      String userId = ctx.get("user_id");
      profiles.getProfile(userId)
        .onSuccess(profile -> {
          if (profile == null) {
            ctx.response().setStatusCode(404).end("Profile not found");
          } else {
            ctx.response().putHeader("Content-Type", "application/json").end(profile.encode());
          }
        })
        .onFailure(err -> ctx.response().setStatusCode(500).end(err.getMessage()));
    });

    router.put("/api/profile").handler(ctx -> {
      String userId = ctx.get("user_id");
      profiles.saveProfile(userId, ctx.body().asJsonObject())
        .onSuccess(saved -> ctx.response().putHeader("Content-Type", "application/json").end(saved.encode()))
        .onFailure(err -> {
          String message = err.getMessage() == null ? err.toString() : err.getMessage();
          ctx.response().setStatusCode(400).end(message);
        });
    });
  }
}
