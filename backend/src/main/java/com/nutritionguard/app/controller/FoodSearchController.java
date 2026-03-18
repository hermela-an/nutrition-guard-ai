package com.nutritionguard.app.controller;

import com.nutritionguard.app.service.FoodSearchService;
import com.nutritionguard.app.service.ProfileService;
import io.vertx.core.eventbus.ReplyException;
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
