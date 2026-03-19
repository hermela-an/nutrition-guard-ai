package com.nutritionguard.app.repository;

import com.nutritionguard.app.db.Database;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

public class NotificationRepository {
  private final Database db;

  public NotificationRepository(Database db) {
    this.db = db;
  }

  public Future<JsonObject> create(String userId, String type, String title, String message, JsonObject data) {
    String sql = """
      INSERT INTO notifications (user_id, type, title, message, data)
      VALUES ($1, $2, $3, $4, $5::jsonb)
      RETURNING id, user_id, type, title, message, data, is_read, created_at
      """;
    return db.query(sql, Tuple.of(
      userId,
      type,
      title,
      message,
      (data == null ? new JsonObject() : data).encode()
    )).map(rows -> map(rows.iterator().next()));
  }

  public Future<Boolean> existsByKey(String userId, String key) {
    String sql = """
      SELECT 1
      FROM notifications
      WHERE user_id = $1 AND data->>'key' = $2
      LIMIT 1
      """;
    return db.query(sql, Tuple.of(userId, key)).map(rows -> rows.iterator().hasNext());
  }

  public Future<JsonArray> list(String userId, int limit, int offset, boolean unreadOnly) {
    String base = """
      SELECT id, user_id, type, title, message, data, is_read, created_at
      FROM notifications
      WHERE user_id = $1
      """;
    String where = unreadOnly ? " AND is_read = false\n" : "\n";
    String sql = base + where + "ORDER BY created_at DESC\nLIMIT $2 OFFSET $3";
    return db.query(sql, Tuple.of(userId, limit, offset)).map(rows -> {
      JsonArray out = new JsonArray();
      for (Row row : rows) {
        out.add(map(row));
      }
      return out;
    });
  }

  public Future<Integer> unreadCount(String userId) {
    String sql = "SELECT COUNT(*)::int AS c FROM notifications WHERE user_id = $1 AND is_read = false";
    return db.query(sql, Tuple.of(userId)).map(rows -> rows.iterator().next().getInteger("c"));
  }

  public Future<Integer> markRead(String userId, JsonArray ids) {
    if (ids == null || ids.isEmpty()) {
      return Future.succeededFuture(0);
    }
    Long[] idArray = new Long[ids.size()];
    for (int i = 0; i < ids.size(); i++) {
      Object v = ids.getValue(i);
      if (v instanceof Number n) {
        idArray[i] = n.longValue();
      } else if (v instanceof String s && !s.isBlank()) {
        idArray[i] = Long.parseLong(s);
      } else {
        idArray[i] = null;
      }
    }
    String sql = """
      UPDATE notifications
      SET is_read = true
      WHERE user_id = $1 AND id = ANY($2::bigint[])
      """;
    return db.query(sql, Tuple.of(userId, idArray))
      .map(rows -> rows.rowCount());
  }

  public Future<Integer> markAllRead(String userId) {
    String sql = "UPDATE notifications SET is_read = true WHERE user_id = $1 AND is_read = false";
    return db.query(sql, Tuple.of(userId)).map(rows -> rows.rowCount());
  }

  private JsonObject map(Row row) {
    Object rawData = row.getValue("data");
    JsonObject data;
    if (rawData instanceof JsonObject obj) {
      data = obj;
    } else if (rawData instanceof String text && !text.isBlank()) {
      data = new JsonObject(text);
    } else {
      data = new JsonObject();
    }

    return new JsonObject()
      .put("id", row.getLong("id"))
      .put("user_id", row.getString("user_id"))
      .put("type", row.getString("type"))
      .put("title", row.getString("title"))
      .put("message", row.getString("message"))
      .put("data", data)
      .put("is_read", row.getBoolean("is_read"))
      .put("created_at", row.getOffsetDateTime("created_at").toString());
  }
}
