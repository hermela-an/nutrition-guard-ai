package com.nutritionguard.app.service;

import com.nutritionguard.app.repository.NotificationRepository;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class NotificationService {
  public static final String TYPE_ACTIVITY = "ACTIVITY";
  public static final String TYPE_ALERT = "ALERT";

  private final NotificationRepository notifications;

  public NotificationService(NotificationRepository notifications) {
    this.notifications = notifications;
  }

  public Future<JsonObject> create(String userId, String type, String title, String message, JsonObject data) {
    return notifications.create(userId, type, title, message, data);
  }

  public Future<JsonObject> createOnce(String userId, String key, String type, String title, String message, JsonObject data) {
    JsonObject payload = data == null ? new JsonObject() : data.copy();
    payload.put("key", key);
    return notifications.existsByKey(userId, key).compose(exists -> {
      if (exists) {
        return Future.succeededFuture(new JsonObject().put("skipped", true).put("key", key));
      }
      return notifications.create(userId, type, title, message, payload);
    });
  }

  public Future<JsonArray> list(String userId, int limit, int offset, boolean unreadOnly) {
    return notifications.list(userId, limit, offset, unreadOnly);
  }

  public Future<Integer> unreadCount(String userId) {
    return notifications.unreadCount(userId);
  }

  public Future<Integer> markRead(String userId, JsonArray ids) {
    return notifications.markRead(userId, ids);
  }

  public Future<Integer> markAllRead(String userId) {
    return notifications.markAllRead(userId);
  }
}
