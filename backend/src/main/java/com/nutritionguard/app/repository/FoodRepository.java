package com.nutritionguard.app.repository;

import com.nutritionguard.app.db.Database;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;

public class FoodRepository {
  private final Database db;

  public FoodRepository(Database db) {
    this.db = db;
  }

  public Future<JsonArray> search(String query) {
    String sql = """
      WITH normalized AS (
        SELECT
          id,
          COALESCE(
            to_jsonb(food_items)->>'food_name',
            to_jsonb(food_items)->>'name'
          ) AS food_name,
          to_jsonb(food_items)->>'meal_type' AS meal_type,
          COALESCE(
            (to_jsonb(food_items)->>'portion_amount')::numeric::double precision,
            (to_jsonb(food_items)->>'portion_value')::numeric::double precision
          ) AS portion_amount,
          to_jsonb(food_items)->>'portion_unit' AS portion_unit,
          COALESCE(
            (to_jsonb(food_items)->>'calories')::numeric::int,
            (to_jsonb(food_items)->>'calories_kcal')::numeric::int,
            0
          ) AS calories,
          COALESCE((to_jsonb(food_items)->>'protein_g')::numeric::double precision, 0) AS protein_g,
          COALESCE((to_jsonb(food_items)->>'carbs_g')::numeric::double precision, 0) AS carbs_g,
          COALESCE((to_jsonb(food_items)->>'fat_g')::numeric::double precision, 0) AS fat_g,
          COALESCE((to_jsonb(food_items)->>'sugar_g')::numeric::double precision, 0) AS sugar_g,
          COALESCE((to_jsonb(food_items)->>'sodium_mg')::numeric::double precision, 0) AS sodium_mg,
          COALESCE((to_jsonb(food_items)->>'cholesterol_mg')::numeric::double precision, 0) AS cholesterol_mg,
          COALESCE(to_jsonb(food_items)->'vitamins', '{}'::jsonb) AS vitamins
        FROM food_items
      )
      SELECT *
      FROM normalized
      WHERE LOWER(COALESCE(food_name, '')) LIKE LOWER($1)
      ORDER BY food_name
      LIMIT 50
      """;

    return db.query(sql, Tuple.of("%" + query + "%")).map(this::rowsToFoods);
  }

  public Future<JsonArray> mealCandidates(String mealType) {
    String sql = """
      WITH normalized AS (
        SELECT
          id,
          COALESCE(
            to_jsonb(food_items)->>'food_name',
            to_jsonb(food_items)->>'name'
          ) AS food_name,
          to_jsonb(food_items)->>'meal_type' AS meal_type,
          COALESCE(
            (to_jsonb(food_items)->>'portion_amount')::numeric::double precision,
            (to_jsonb(food_items)->>'portion_value')::numeric::double precision
          ) AS portion_amount,
          to_jsonb(food_items)->>'portion_unit' AS portion_unit,
          COALESCE(
            (to_jsonb(food_items)->>'calories')::numeric::int,
            (to_jsonb(food_items)->>'calories_kcal')::numeric::int,
            0
          ) AS calories,
          COALESCE((to_jsonb(food_items)->>'protein_g')::numeric::double precision, 0) AS protein_g,
          COALESCE((to_jsonb(food_items)->>'carbs_g')::numeric::double precision, 0) AS carbs_g,
          COALESCE((to_jsonb(food_items)->>'fat_g')::numeric::double precision, 0) AS fat_g,
          COALESCE((to_jsonb(food_items)->>'sugar_g')::numeric::double precision, 0) AS sugar_g,
          COALESCE((to_jsonb(food_items)->>'sodium_mg')::numeric::double precision, 0) AS sodium_mg,
          COALESCE((to_jsonb(food_items)->>'cholesterol_mg')::numeric::double precision, 0) AS cholesterol_mg,
          COALESCE(to_jsonb(food_items)->'vitamins', '{}'::jsonb) AS vitamins
        FROM food_items
      )
      SELECT *
      FROM normalized
      WHERE meal_type = $1
      ORDER BY calories ASC
      LIMIT 30
      """;

    return db.query(sql, Tuple.of(mealType)).map(this::rowsToFoods);
  }

  public Future<JsonArray> vitaminLeaderboard(String vitaminKey, int limit) {
    int safeLimit = Math.max(1, Math.min(limit, 100));

    String sql = """
      WITH scored AS (
        SELECT
          id,
          food_name,
          vitamins,
          vitamins->>$1 AS vitamin_value,
          NULLIF(regexp_replace(COALESCE(vitamins->>$1, ''), '[^0-9\\\\.]', '', 'g'), '')::numeric AS vitamin_score
        FROM food_items
        WHERE vitamins ? $1
      )
      SELECT id, food_name, vitamins, vitamin_value
      FROM scored
      ORDER BY vitamin_score DESC NULLS LAST, food_name ASC
      LIMIT $2
      """;

    return db
      .query(sql, Tuple.of(vitaminKey, safeLimit))
      .map(rows -> {
        JsonArray items = new JsonArray();
        for (Row row : rows) {
          items.add(new JsonObject()
            .put("id", row.getInteger("id"))
            .put("food_name", row.getString("food_name"))
            .put("vitamins", row.getJsonObject("vitamins"))
            .put("vitamin_value", row.getString("vitamin_value"))
          );
        }
        return items;
      });
  }

  private JsonArray rowsToFoods(RowSet<Row> rows) {
    JsonArray items = new JsonArray();
    for (Row row : rows) {
      items.add(toFood(row));
    }
    return items;
  }

  private JsonObject toFood(Row row) {
    return new JsonObject()
      .put("id", row.getInteger("id"))
      .put("food_name", row.getString("food_name"))
      .put("meal_type", row.getString("meal_type"))
      .put("portion_amount", row.getDouble("portion_amount"))
      .put("portion_unit", row.getString("portion_unit"))
      .put("calories", row.getInteger("calories"))
      .put("protein_g", row.getDouble("protein_g"))
      .put("carbs_g", row.getDouble("carbs_g"))
      .put("fat_g", row.getDouble("fat_g"))
      .put("sugar_g", row.getDouble("sugar_g"))
      .put("sodium_mg", row.getDouble("sodium_mg"))
      .put("cholesterol_mg", row.getDouble("cholesterol_mg"))
      .put("vitamins", row.getJsonObject("vitamins"));
  }
}
