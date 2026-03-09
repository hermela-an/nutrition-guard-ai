package com.nutritionguard.app.controller;

import com.nutritionguard.app.service.FoodSearchService;
import com.nutritionguard.app.service.ProfileService;
import io.vertx.ext.web.Router;

public class FoodSearchController {
  private final FoodSearchService foodSearch;
  private final ProfileService profiles;

  public FoodSearchController(FoodSearchService foodSearch, ProfileService profiles) {
    this.foodSearch = foodSearch;
    this.profiles = profiles;
  }

  public void mount(Router router) {
    router.get("/api/foods/search").handler(ctx -> {
      String query = ctx.request().getParam("q");
      String userId = ctx.get("user_id");
      profiles.getProfile(userId).compose(profile -> {
        if (profile == null) {
          return io.vertx.core.Future.failedFuture("Profile not found");
        }
        return foodSearch.search(query == null ? "" : query, profile.getJsonObject("health_metadata"));
      }).onSuccess(result -> ctx.response().putHeader("Content-Type", "application/json").end(result.encode()))
        .onFailure(err -> ctx.response().setStatusCode(400).end(err.getMessage()));
    });
  }
}
