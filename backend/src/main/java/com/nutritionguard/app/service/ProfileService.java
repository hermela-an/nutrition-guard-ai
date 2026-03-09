package com.nutritionguard.app.service;

import com.nutritionguard.app.repository.ProfileRepository;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class ProfileService {
  private final ProfileRepository profiles;

  public ProfileService(ProfileRepository profiles) {
    this.profiles = profiles;
  }

  public Future<JsonObject> saveProfile(String userId, JsonObject payload) {
    return profiles.upsertProfile(userId, payload);
  }

  public Future<JsonObject> getProfile(String userId) {
    return profiles.getProfile(userId);
  }
}
