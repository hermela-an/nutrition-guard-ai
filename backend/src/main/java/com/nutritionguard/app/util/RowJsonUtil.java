package com.nutritionguard.app.util;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;

import java.util.Map;

public final class RowJsonUtil {
  private RowJsonUtil() {
  }

  public static JsonObject getJsonObject(Row row, String column) {
    if (row == null || column == null || column.isBlank()) return new JsonObject();

    Object value = row.getValue(column);
    if (value == null) return new JsonObject();

    if (value instanceof JsonObject jo) return jo;

    if (value instanceof Buffer b) {
      try {
        return b.toJsonObject();
      } catch (RuntimeException ignored) {
        return new JsonObject();
      }
    }

    if (value instanceof String s) {
      String trimmed = s.trim();
      if (trimmed.isEmpty()) return new JsonObject();
      if (!trimmed.startsWith("{")) return new JsonObject();
      try {
        return new JsonObject(trimmed);
      } catch (RuntimeException ignored) {
        return new JsonObject();
      }
    }

    if (value instanceof Map<?, ?> m) {
      JsonObject obj = new JsonObject();
      for (Map.Entry<?, ?> entry : m.entrySet()) {
        if (entry.getKey() != null) {
          obj.put(String.valueOf(entry.getKey()), entry.getValue());
        }
      }
      return obj;
    }

    return new JsonObject();
  }
}
