package com.nutritionguard.app.controller;

import com.nutritionguard.app.service.FoodSearchService;
import com.nutritionguard.app.service.NotificationService;
import com.nutritionguard.app.service.ProfileService;
import com.nutritionguard.app.repository.FoodSearchEventRepository;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

public class FoodSearchController {
  private final FoodSearchService foodSearch;
  private final ProfileService profiles;
  private final NotificationService notifications;
  private final FoodSearchEventRepository events;

  public FoodSearchController(FoodSearchService foodSearch, ProfileService profiles, NotificationService notifications, FoodSearchEventRepository events) {
    this.foodSearch = foodSearch;
    this.profiles = profiles;
    this.notifications = notifications;
    this.events = events;
  }

  public void mount(Router router) {
    router.get("/api/foods/search").handler(ctx -> {
      String query = ctx.request().getParam("q");
      String userId = ctx.get("user_id");
      profiles.getProfile(userId).compose(profile -> {
        if (profile == null) {
          return io.vertx.core.Future.failedFuture("Profile not found");
        }
        return foodSearch.search(query == null ? "" : query, profile);
      }).onSuccess(result -> {
          maybeNotifySearch(userId, query, result);
          recordSearch(userId, query, result);
          ctx.response().putHeader("Content-Type", "application/json").end(result.encode());
        })
        .onFailure(err -> {
          err.printStackTrace();
          int status = statusFrom(err, 400);
          ctx.response().setStatusCode(status).end(messageFrom(err));
        });
    });
  }

  private void recordSearch(String userId, String query, JsonArray result) {
    if (userId == null || userId.isBlank()) return;
    int resultsCount = result == null ? 0 : result.size();
    int redCount = 0;
    if (result != null) {
      for (int i = 0; i < result.size(); i++) {
        JsonObject item = result.getJsonObject(i);
        if (item == null) continue;
        String light = item.getString("traffic_light");
        if ("AVOID".equalsIgnoreCase(light) || "RED".equalsIgnoreCase(light)) {
          redCount++;
        }
      }
    }
    events.record(userId, query, resultsCount, redCount).onFailure(err -> err.printStackTrace());
  }

  private void maybeNotifySearch(String userId, String query, JsonArray result) {
    if (result == null || result.isEmpty()) return;
    int redCount = 0;
    JsonArray examples = new JsonArray();

    for (int i = 0; i < result.size(); i++) {
      JsonObject item = result.getJsonObject(i);
      if (item == null) continue;
      String light = item.getString("traffic_light");
      if ("AVOID".equalsIgnoreCase(light) || "RED".equalsIgnoreCase(light)) {
        redCount++;
        if (examples.size() < 3) {
          examples.add(item.getString("food_name", "Food"));
        }
      }
    }

    if (redCount <= 0) return;

    String q = (query == null ? "" : query).trim();
    String title = "Food safety alert";
    String message = q.isEmpty()
      ? "Some foods in your results are flagged as Avoid for your health profile."
      : "Some foods matching \"" + q + "\" are flagged as Avoid for your health profile.";

    notifications.create(
      userId,
      NotificationService.TYPE_ALERT,
      title,
      message,
      new JsonObject()
        .put("query", q)
        .put("red_count", redCount)
        .put("examples", examples)
    ).onFailure(err -> err.printStackTrace());
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
