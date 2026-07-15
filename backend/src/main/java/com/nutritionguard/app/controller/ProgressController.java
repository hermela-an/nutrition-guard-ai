package com.nutritionguard.app.controller;

import com.nutritionguard.app.service.ProgressService;
import io.vertx.ext.web.Router;

public class ProgressController {
  private final ProgressService progress;

  public ProgressController(ProgressService progress) {
    this.progress = progress;
  }

  public void mount(Router router) {
     // Saving a New Progress Log
    router.post("/api/progress/logs").handler(ctx -> {
      String userId = ctx.get("user_id");
      progress.log(userId, ctx.body().asJsonObject())
        .onSuccess(v -> ctx.response().setStatusCode(201).end())
        .onFailure(err -> ctx.response().setStatusCode(400).end(err.getMessage()));
    });
        // Fetching Past Progress History
    router.get("/api/progress/history").handler(ctx -> {
      String userId = ctx.get("user_id");
      progress.history(userId)
        .onSuccess(history -> ctx.response().putHeader("Content-Type", "application/json").end(history.encode()))
        .onFailure(err -> ctx.response().setStatusCode(400).end(err.getMessage()));
    });
  }
}
