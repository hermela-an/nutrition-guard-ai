package com.nutritionguard.app.controller;

import com.nutritionguard.app.service.NotificationService;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

public class NotificationController {
  private final NotificationService notifications;

  public NotificationController(NotificationService notifications) {
    this.notifications = notifications;
  }

  public void mount(Router router) {
    router.get("/api/notifications").handler(ctx -> {
      String userId = ctx.get("user_id");
      int limit = parseInt(ctx.request().getParam("limit"), 50, 1, 200);
      int offset = parseInt(ctx.request().getParam("offset"), 0, 0, 10_000);
      boolean unreadOnly = "true".equalsIgnoreCase(ctx.request().getParam("unreadOnly"));

      notifications.list(userId, limit, offset, unreadOnly)
        .onSuccess(list -> ctx.response().putHeader("Content-Type", "application/json").end(list.encode()))
        .onFailure(err -> ctx.response().setStatusCode(400).end(err.getMessage()));
    });

    router.get("/api/notifications/unread-count").handler(ctx -> {
      String userId = ctx.get("user_id");
      notifications.unreadCount(userId)
        .onSuccess(count -> ctx.response().putHeader("Content-Type", "application/json")
          .end(new JsonObject().put("count", count).encode()))
        .onFailure(err -> ctx.response().setStatusCode(400).end(err.getMessage()));
    });

    router.post("/api/notifications/mark-read").handler(ctx -> {
      String userId = ctx.get("user_id");
      JsonObject body = ctx.body().asJsonObject();
      JsonArray ids = body == null ? null : body.getJsonArray("ids");
      if (ids == null || ids.isEmpty()) {
        notifications.markAllRead(userId)
          .onSuccess(updated -> ctx.response().putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("updated", updated).encode()))
          .onFailure(err -> ctx.response().setStatusCode(400).end(err.getMessage()));
        return;
      }
      notifications.markRead(userId, ids)
        .onSuccess(updated -> ctx.response().putHeader("Content-Type", "application/json")
          .end(new JsonObject().put("updated", updated).encode()))
        .onFailure(err -> ctx.response().setStatusCode(400).end(err.getMessage()));
    });
  }

  private int parseInt(String raw, int fallback, int min, int max) {
    if (raw == null || raw.isBlank()) return fallback;
    try {
      int v = Integer.parseInt(raw);
      if (v < min) return min;
      if (v > max) return max;
      return v;
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }
}
