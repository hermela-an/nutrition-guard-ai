package com.nutritionguard.app.repository;

import com.nutritionguard.app.db.Database;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

public class MealTrackingRepository {
  private final Database db;
  private Future<Void> bootstrap;

  public MealTrackingRepository(Database db) {
    this.db = db;
  }

  private Future<Void> ensureSchema() {
    if (bootstrap != null) return bootstrap;
    synchronized (this) {
      if (bootstrap != null) return bootstrap;

      String ddl = """
      CREATE TABLE IF NOT EXISTS daily_meal_recommendations (
        user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        plan_date DATE NOT NULL,
        meal_type TEXT NOT NULL,
        food_item_id INT NOT NULL REFERENCES food_items(id) ON DELETE CASCADE,
        created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
        PRIMARY KEY (user_id, plan_date, meal_type, food_item_id)
      );

      CREATE INDEX IF NOT EXISTS idx_daily_meal_recommendations_user_date
        ON daily_meal_recommendations (user_id, plan_date);

      CREATE TABLE IF NOT EXISTS daily_meal_intake (
        user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        intake_date DATE NOT NULL,
        meal_type TEXT NOT NULL,
        food_item_id INT NOT NULL REFERENCES food_items(id) ON DELETE CASCADE,
        eaten BOOLEAN NOT NULL DEFAULT FALSE,
        eaten_at TIMESTAMPTZ,
        updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
        PRIMARY KEY (user_id, intake_date, meal_type, food_item_id)
      );

      CREATE INDEX IF NOT EXISTS idx_daily_meal_intake_user_date
        ON daily_meal_intake (user_id, intake_date);
      """;

      bootstrap = db.execute(ddl);
      return bootstrap;
    }
  }

  public Future<Void> saveRecommendations(String userId, LocalDate date, JsonObject plan) {
    if (plan == null) return Future.succeededFuture();

    JsonArray mealTypes = new JsonArray();
    JsonArray foodIds = new JsonArray();
    appendIds("breakfast", plan.getJsonArray("breakfast"), mealTypes, foodIds);
    appendIds("lunch", plan.getJsonArray("lunch"), mealTypes, foodIds);
    appendIds("dinner", plan.getJsonArray("dinner"), mealTypes, foodIds);
    appendIds("snack", plan.getJsonArray("snacks"), mealTypes, foodIds);
    appendIds("snack", plan.getJsonArray("snack"), mealTypes, foodIds);

    if (foodIds.isEmpty()) return Future.succeededFuture();

    String sql = """
      INSERT INTO daily_meal_recommendations (user_id, plan_date, meal_type, food_item_id)
      SELECT $1, $2::date, x.meal_type, x.food_item_id
      FROM UNNEST($3::text[], $4::int[]) AS x(meal_type, food_item_id)
      ON CONFLICT DO NOTHING
      """;

    java.util.ArrayList<String> mealTypeList = new java.util.ArrayList<>();
    flattenStrings(mealTypes, mealTypeList);

    java.util.ArrayList<Integer> foodIdList = new java.util.ArrayList<>();
    flattenIntegers(foodIds, foodIdList);

    int pairs = Math.min(mealTypeList.size(), foodIdList.size());
    if (pairs <= 0) return Future.succeededFuture();

    String[] mealTypeArray = mealTypeList.subList(0, pairs).toArray(new String[0]);
    Integer[] foodIdArray = foodIdList.subList(0, pairs).toArray(new Integer[0]);

    return ensureSchema()
      .compose(ignored -> db.query(sql, Tuple.of(userId, date, mealTypeArray, foodIdArray)).mapEmpty());
  }

  public Future<Map<String, Boolean>> intakeMap(String userId, LocalDate date) {
    String sql = """
      SELECT meal_type, food_item_id, eaten
      FROM daily_meal_intake
      WHERE user_id = $1 AND intake_date = $2::date
      """;
    return ensureSchema().compose(ignored ->
      db.query(sql, Tuple.of(userId, date)).map(rows -> {
        Map<String, Boolean> map = new HashMap<>();
        for (Row row : rows) {
          String key = key(row.getString("meal_type"), row.getInteger("food_item_id"));
          map.put(key, row.getBoolean("eaten"));
        }
        return map;
      })
    );
  }

  public Future<JsonObject> upsertIntake(String userId, LocalDate date, String mealType, int foodItemId, boolean eaten) {
    String sql = """
      INSERT INTO daily_meal_intake (user_id, intake_date, meal_type, food_item_id, eaten, eaten_at, updated_at)
      VALUES ($1, $2::date, $3, $4, $5, CASE WHEN $5 THEN now() ELSE NULL END, now())
      ON CONFLICT (user_id, intake_date, meal_type, food_item_id) DO UPDATE
      SET eaten = EXCLUDED.eaten,
          eaten_at = CASE WHEN EXCLUDED.eaten THEN COALESCE(daily_meal_intake.eaten_at, now()) ELSE NULL END,
          updated_at = now()
      RETURNING user_id, intake_date, meal_type, food_item_id, eaten, eaten_at, updated_at
      """;

    return ensureSchema().compose(ignored ->
      db.query(sql, Tuple.of(userId, date, mealType, foodItemId, eaten)).map(rows -> {
        Row row = rows.iterator().next();
        return new JsonObject()
          .put("user_id", row.getString("user_id"))
          .put("date", row.getLocalDate("intake_date").toString())
          .put("meal_type", row.getString("meal_type"))
          .put("food_item_id", row.getInteger("food_item_id"))
          .put("eaten", row.getBoolean("eaten"))
          .put("eaten_at", toStringOrNull(row.getOffsetDateTime("eaten_at")))
          .put("updated_at", toStringOrNull(row.getOffsetDateTime("updated_at")));
      })
    );
  }

  public Future<JsonObject> progress(String userId, LocalDate date) {
    String sql = """
      SELECT
        (SELECT COUNT(*)::int FROM daily_meal_recommendations
          WHERE user_id = $1 AND plan_date = $2::date) AS total,
        (SELECT COUNT(*)::int FROM daily_meal_intake
          WHERE user_id = $1 AND intake_date = $2::date AND eaten = true) AS eaten
      """;
    return ensureSchema().compose(ignored ->
      db.query(sql, Tuple.of(userId, date)).map(rows -> {
        Row row = rows.iterator().next();
        return new JsonObject()
          .put("total", row.getInteger("total"))
          .put("eaten", row.getInteger("eaten"));
      })
    );
  }

  private void appendIds(String mealType, JsonArray items, JsonArray mealTypes, JsonArray foodIds) {
    if (items == null || items.isEmpty()) return;
    for (int i = 0; i < items.size(); i++) {
      JsonObject item = items.getJsonObject(i);
      if (item == null) continue;
      Integer id = item.getInteger("id");
      if (id == null) continue;
      mealTypes.add(mealType);
      foodIds.add(id);
    }
  }

  private void flattenStrings(JsonArray src, java.util.ArrayList<String> out) {
    if (src == null || out == null) return;
    for (int i = 0; i < src.size(); i++) {
      Object v = src.getValue(i);
      if (v instanceof JsonArray nested) {
        flattenStrings(nested, out);
      } else if (v != null) {
        out.add(v.toString());
      }
    }
  }

  private void flattenIntegers(JsonArray src, java.util.ArrayList<Integer> out) {
    if (src == null || out == null) return;
    for (int i = 0; i < src.size(); i++) {
      Object v = src.getValue(i);
      if (v instanceof JsonArray nested) {
        flattenIntegers(nested, out);
      } else if (v instanceof Integer iv) {
        out.add(iv);
      } else if (v instanceof Number nv) {
        out.add(nv.intValue());
      } else if (v != null) {
        out.add(Integer.valueOf(v.toString()));
      }
    }
  }

  public static String key(String mealType, int foodItemId) {
    return mealType + ":" + foodItemId;
  }

  private String toStringOrNull(OffsetDateTime dt) {
    return dt == null ? null : dt.toString();
  }
}
