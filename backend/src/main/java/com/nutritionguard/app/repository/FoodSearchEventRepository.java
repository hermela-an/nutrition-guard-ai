package com.nutritionguard.app.repository;

import com.nutritionguard.app.db.Database;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

public class FoodSearchEventRepository {
  private final Database db;
  private Future<Void> bootstrap;

  public FoodSearchEventRepository(Database db) {
    this.db = db;
  }

  private Future<Void> ensureSchema() {
    if (bootstrap != null) return bootstrap;
    synchronized (this) {
      if (bootstrap != null) return bootstrap;

      String ddl = """
      CREATE TABLE IF NOT EXISTS food_search_events (
        id BIGSERIAL PRIMARY KEY,
        user_id TEXT NOT NULL,
        query TEXT,
        results_count INT NOT NULL DEFAULT 0,
        red_count INT NOT NULL DEFAULT 0,
        created_at TIMESTAMPTZ NOT NULL DEFAULT now()
      );

      CREATE INDEX IF NOT EXISTS idx_food_search_events_created_at ON food_search_events (created_at);
      CREATE INDEX IF NOT EXISTS idx_food_search_events_user_id ON food_search_events (user_id);
      """;

      bootstrap = db.execute(ddl);
      return bootstrap;
    }
  }

  public Future<Void> record(String userId, String query, int resultsCount, int redCount) {
    String sql = """
      INSERT INTO food_search_events (user_id, query, results_count, red_count)
      VALUES ($1, $2, $3, $4)
      """;
    String q = query == null ? null : query.trim();
    int safeResults = Math.max(0, resultsCount);
    int safeRed = Math.max(0, redCount);
    return ensureSchema().compose(ignored ->
      db.query(sql, Tuple.of(userId, q, safeResults, safeRed)).mapEmpty()
    );
  }

  public Future<JsonArray> dailyCounts(int days) {
    int safeDays = Math.max(1, Math.min(days, 60));
    String sql = """
      WITH params AS (
        SELECT $1::int AS days
      ),
      day_list AS (
        SELECT (current_date - offs)::date AS day
        FROM params, generate_series(0, (SELECT days - 1 FROM params)) AS offs
      ),
      agg AS (
        SELECT
          created_at::date AS day,
          COUNT(*)::int AS count,
          COALESCE(SUM(red_count), 0)::int AS red_count
        FROM food_search_events
        WHERE created_at >= (current_date - ((SELECT days - 1 FROM params))::int)
        GROUP BY 1
      )
      SELECT
        d.day::text AS day,
        COALESCE(a.count, 0)::int AS count,
        COALESCE(a.red_count, 0)::int AS red_count
      FROM day_list d
      LEFT JOIN agg a ON a.day = d.day
      ORDER BY d.day ASC
      """;

    return ensureSchema().compose(ignored ->
      db.query(sql, Tuple.of(safeDays)).map(rows -> {
        JsonArray out = new JsonArray();
        for (Row row : rows) {
          out.add(new JsonObject()
            .put("day", row.getString("day"))
            .put("count", row.getInteger("count"))
            .put("red_count", row.getInteger("red_count")));
        }
        return out;
      })
    );
  }
}

