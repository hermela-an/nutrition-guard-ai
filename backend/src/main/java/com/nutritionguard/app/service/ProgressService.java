package com.nutritionguard.app.service;

import com.nutritionguard.app.repository.ProgressRepository;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ProgressService {
  private final ProgressRepository progress;

  public ProgressService(ProgressRepository progress) {
    this.progress = progress;
  }

  public Future<Void> log(String userId, JsonObject payload) {
    return progress.logDaily(userId, payload);
  }

  public Future<JsonArray> history(String userId) {
    return progress.history(userId);
  }
}
