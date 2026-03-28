package com.nutritionguard.app.controller;

import com.nutritionguard.app.service.FoodSearchService;
import com.nutritionguard.app.service.NotificationService;
import com.nutritionguard.app.service.ProfileService;
import com.nutritionguard.app.util.EventAddresses;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

public class ChatController {
  private final Vertx vertx;
  private final ProfileService profiles;
  private final FoodSearchService foods;
  private final NotificationService notifications;

  public ChatController(Vertx vertx, ProfileService profiles, FoodSearchService foods, NotificationService notifications) {
    this.vertx = vertx;
    this.profiles = profiles;
    this.foods = foods;
    this.notifications = notifications;
  }

  public void mount(Router router) {
    router.post("/api/chat/ask").handler(ctx -> {
      String userId = ctx.get("user_id");
      JsonObject body = ctx.body().asJsonObject();
      String question = body == null ? "" : body.getString("question", "");

      maybeNotifyChatAlert(userId, question);

      vertx.eventBus().<JsonObject>request(EventAddresses.CHAT_ASK, body)
        .onSuccess(msg -> ctx.response().putHeader("Content-Type", "application/json").end(msg.body().encode()))
        .onFailure(err -> ctx.response().setStatusCode(400).end(err.getMessage()));
    });
  }

  private void maybeNotifyChatAlert(String userId, String question) {
    String hint = extractHint(question);
    if (hint.isEmpty()) return;

    profiles.getProfile(userId).onSuccess(profile -> {
      if (profile == null) return;
      JsonObject health = profile.getJsonObject("health_metadata", new JsonObject()).copy();
      health.put("goal", profile.getString("goal", ""));
      foods.search(hint, health).onSuccess(results -> {
        if (results == null || results.isEmpty()) return;

        int red = 0;
        JsonArray examples = new JsonArray();
        for (int i = 0; i < results.size(); i++) {
          JsonObject item = results.getJsonObject(i);
          if (item == null) continue;
          String light = item.getString("traffic_light");
          if ("AVOID".equalsIgnoreCase(light) || "RED".equalsIgnoreCase(light)) {
            red++;
            if (examples.size() < 2) {
              examples.add(item.getString("food_name", "Food"));
            }
          }
        }
        if (red <= 0) return;

        notifications.create(
          userId,
          NotificationService.TYPE_ALERT,
          "Chat food alert",
          "A food you asked about may be flagged as Avoid for your health profile.",
          new JsonObject().put("hint", hint).put("red_count", red).put("examples", examples)
        ).onFailure(err -> err.printStackTrace());
      }).onFailure(err -> err.printStackTrace());
    }).onFailure(err -> err.printStackTrace());
  }

  private String extractHint(String q) {
    if (q == null) return "";
    String lower = q.toLowerCase();
    if (lower.contains("spinach") || lower.contains("iron")) return "spinach";
    if (lower.contains("egg")) return "egg";
    if (lower.contains("salmon") || lower.contains("fish")) return "salmon";
    if (lower.contains("chicken")) return "chicken";

    String cleaned = lower.replaceAll("[^a-z\\s]", " ").replaceAll("\\s+", " ").trim();
    if (!cleaned.isBlank() && cleaned.split("\\s").length == 1) {
      return cleaned;
    }
    return "";
  }
}
