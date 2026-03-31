package com.nutritionguard.app.repository;

import com.nutritionguard.app.db.Database;
import com.nutritionguard.app.util.RowJsonUtil;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

public class ProgressRepository {
  private final Database db;

  public ProgressRepository(Database db) {
    this.db = db;
  }

  public Future<Void> logDaily(String userId, JsonObject payload) {
    String sql = """
      INSERT INTO daily_logs (user_id, log_date, weight_kg, consumed)
      VALUES ($1, $2::date, $3, $4::jsonb)
      """;
    return db.query(sql, Tuple.of(
      userId,
      payload.getString("log_date"),
      payload.getDouble("weight_kg"),
      payload.getJsonObject("consumed", new JsonObject()).encode()
    )).mapEmpty();
  }

  public Future<JsonArray> history(String userId) {
    String sql = """
      SELECT log_date, weight_kg, consumed
      FROM daily_logs
      WHERE user_id = $1
      ORDER BY log_date ASC
      LIMIT 90
      """;
    return db.query(sql, Tuple.of(userId))
      .map(rows -> {
        JsonArray history = new JsonArray();
        for (Row row : rows) {
          history.add(new JsonObject()
            .put("log_date", row.getLocalDate("log_date").toString())
            .put("weight_kg", row.getDouble("weight_kg"))
            .put("consumed", RowJsonUtil.getJsonObject(row, "consumed")));
        }
        return history;
      });
  }
}
