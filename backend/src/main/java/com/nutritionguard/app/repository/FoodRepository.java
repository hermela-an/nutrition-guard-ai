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
          to_jsonb(food_items)->>'traffic_light' AS traffic_light,
          COALESCE(
            to_jsonb(food_items)->>'food_name',
            to_jsonb(food_items)->>'name'
          ) AS food_name,
          COALESCE(
            to_jsonb(food_items)->>'definition',
            to_jsonb(food_items)->>'description',
            to_jsonb(food_items)->>'short_description'
          ) AS definition,
          COALESCE(
            to_jsonb(food_items)->>'image_url',
            to_jsonb(food_items)->>'imageUrl',
            to_jsonb(food_items)->>'image'
          ) AS image_url,
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

  public Future<JsonArray> list(String query, int limit, int offset) {
    int safeLimit = Math.max(1, Math.min(limit, 200));
    int safeOffset = Math.max(0, Math.min(offset, 10_000));
    String q = query == null ? "" : query;

    String sql = """
      WITH normalized AS (
        SELECT
          id,
          to_jsonb(food_items)->>'traffic_light' AS traffic_light,
          COALESCE(
            to_jsonb(food_items)->>'food_name',
            to_jsonb(food_items)->>'name'
          ) AS food_name,
          COALESCE(
            to_jsonb(food_items)->>'definition',
            to_jsonb(food_items)->>'description',
            to_jsonb(food_items)->>'short_description'
          ) AS definition,
          COALESCE(
            to_jsonb(food_items)->>'image_url',
            to_jsonb(food_items)->>'imageUrl',
            to_jsonb(food_items)->>'image'
          ) AS image_url,
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
      WHERE ($1 = '' OR LOWER(COALESCE(food_name, '')) LIKE LOWER($2))
      ORDER BY food_name
      LIMIT $3 OFFSET $4
      """;

    return db
      .query(sql, Tuple.of(q, "%" + q + "%", safeLimit, safeOffset))
      .map(this::rowsToFoods);
  }

  // Admin-friendly ordering (id order). Keeps the user-facing list() alphabetical.
  public Future<JsonArray> listAdmin(String query, int limit, int offset) {
    int safeLimit = Math.max(1, Math.min(limit, 200));
    int safeOffset = Math.max(0, Math.min(offset, 10_000));
    String q = query == null ? "" : query;

    String sql = """
      WITH normalized AS (
        SELECT
          id,
          to_jsonb(food_items)->>'traffic_light' AS traffic_light,
          COALESCE(
            to_jsonb(food_items)->>'food_name',
            to_jsonb(food_items)->>'name'
          ) AS food_name,
          COALESCE(
            to_jsonb(food_items)->>'definition',
            to_jsonb(food_items)->>'description',
            to_jsonb(food_items)->>'short_description'
          ) AS definition,
          COALESCE(
            to_jsonb(food_items)->>'image_url',
            to_jsonb(food_items)->>'imageUrl',
            to_jsonb(food_items)->>'image'
          ) AS image_url,
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
      WHERE ($1 = '' OR LOWER(COALESCE(food_name, '')) LIKE LOWER($2))
      ORDER BY id ASC
      LIMIT $3 OFFSET $4
      """;

    return db
      .query(sql, Tuple.of(q, "%" + q + "%", safeLimit, safeOffset))
      .map(this::rowsToFoods);
  }

  public Future<JsonObject> create(JsonObject input) {
    JsonObject safe = input == null ? new JsonObject() : input;
    double portionValue = doubleFrom(safe, "portion_value", doubleFrom(safe, "portion_amount", 1.0));
    int caloriesKcal = intFrom(safe, "calories_kcal", intFrom(safe, "calories", 0));

    String sql = """
      INSERT INTO food_items (
        food_name,
        meal_type,
        portion_value,
        portion_unit,
        calories_kcal,
        protein_g,
        carbs_g,
        fat_g,
        sugar_g,
        sodium_mg,
        cholesterol_mg,
        vitamins,
        image_url,
        definition,
        description,
        traffic_light
      )
      VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12::jsonb,$13,$14,$15,$16)
      RETURNING
        id,
        traffic_light,
        food_name,
        definition,
        image_url,
        meal_type,
        (portion_value)::double precision AS portion_amount,
        portion_unit,
        (calories_kcal)::numeric::int AS calories,
        (protein_g)::double precision AS protein_g,
        (carbs_g)::double precision AS carbs_g,
        (fat_g)::double precision AS fat_g,
        (sugar_g)::double precision AS sugar_g,
        (sodium_mg)::double precision AS sodium_mg,
        (cholesterol_mg)::double precision AS cholesterol_mg,
        COALESCE(vitamins, '{}'::jsonb) AS vitamins
      """;

    return db
      .query(sql, Tuple.of(
        safe.getString("food_name", ""),
        safe.getString("meal_type", ""),
        portionValue,
        safe.getString("portion_unit", "serving"),
        caloriesKcal,
        safe.getDouble("protein_g", 0.0),
        safe.getDouble("carbs_g", 0.0),
        safe.getDouble("fat_g", 0.0),
        safe.getDouble("sugar_g", 0.0),
        safe.getDouble("sodium_mg", 0.0),
        safe.getDouble("cholesterol_mg", 0.0),
        (safe.getJsonObject("vitamins", new JsonObject())).encode(),
        safe.getString("image_url"),
        safe.getString("definition"),
        safe.getString("description"),
        normalizeLight(safe.getString("traffic_light", "GREEN"))
      ))
      .map(rows -> toFood(rows.iterator().next()));
  }

  public Future<JsonObject> update(int id, JsonObject input) {
    JsonObject safe = input == null ? new JsonObject() : input;

    Double portionValue = null;
    if (safe.containsKey("portion_value")) {
      portionValue = doubleOrNull(safe.getValue("portion_value"));
    } else if (safe.containsKey("portion_amount")) {
      portionValue = doubleOrNull(safe.getValue("portion_amount"));
    }

    Integer caloriesKcal = null;
    if (safe.containsKey("calories_kcal")) {
      caloriesKcal = intOrNull(safe.getValue("calories_kcal"));
    } else if (safe.containsKey("calories")) {
      caloriesKcal = intOrNull(safe.getValue("calories"));
    }

    String sql = """
      UPDATE food_items
      SET
        food_name = COALESCE($2, food_name),
        meal_type = COALESCE($3, meal_type),
        portion_value = COALESCE($4, portion_value),
        portion_unit = COALESCE($5, portion_unit),
        calories_kcal = COALESCE($6, calories_kcal),
        protein_g = COALESCE($7, protein_g),
        carbs_g = COALESCE($8, carbs_g),
        fat_g = COALESCE($9, fat_g),
        sugar_g = COALESCE($10, sugar_g),
        sodium_mg = COALESCE($11, sodium_mg),
        cholesterol_mg = COALESCE($12, cholesterol_mg),
        vitamins = COALESCE($13::jsonb, vitamins),
        image_url = COALESCE($14, image_url),
        definition = COALESCE($15, definition),
        description = COALESCE($16, description),
        traffic_light = COALESCE($17, traffic_light)
      WHERE id = $1
      RETURNING
        id,
        traffic_light,
        food_name,
        definition,
        image_url,
        meal_type,
        (portion_value)::double precision AS portion_amount,
        portion_unit,
        (calories_kcal)::numeric::int AS calories,
        (protein_g)::double precision AS protein_g,
        (carbs_g)::double precision AS carbs_g,
        (fat_g)::double precision AS fat_g,
        (sugar_g)::double precision AS sugar_g,
        (sodium_mg)::double precision AS sodium_mg,
        (cholesterol_mg)::double precision AS cholesterol_mg,
        COALESCE(vitamins, '{}'::jsonb) AS vitamins
      """;

    return db
      .query(sql, Tuple.of(
        id,
        safe.containsKey("food_name") ? safe.getString("food_name") : null,
        safe.containsKey("meal_type") ? safe.getString("meal_type") : null,
        portionValue,
        safe.containsKey("portion_unit") ? safe.getString("portion_unit") : null,
        caloriesKcal,
        safe.containsKey("protein_g") ? safe.getDouble("protein_g") : null,
        safe.containsKey("carbs_g") ? safe.getDouble("carbs_g") : null,
        safe.containsKey("fat_g") ? safe.getDouble("fat_g") : null,
        safe.containsKey("sugar_g") ? safe.getDouble("sugar_g") : null,
        safe.containsKey("sodium_mg") ? safe.getDouble("sodium_mg") : null,
        safe.containsKey("cholesterol_mg") ? safe.getDouble("cholesterol_mg") : null,
        safe.containsKey("vitamins") ? safe.getJsonObject("vitamins").encode() : null,
        safe.containsKey("image_url") ? safe.getString("image_url") : null,
        safe.containsKey("definition") ? safe.getString("definition") : null,
        safe.containsKey("description") ? safe.getString("description") : null,
        safe.containsKey("traffic_light") ? normalizeLight(safe.getString("traffic_light")) : null
      ))
      .map(rows -> {
        if (!rows.iterator().hasNext()) {
          return null;
        }
        return toFood(rows.iterator().next());
      });
  }

  private double doubleFrom(JsonObject obj, String key, double fallback) {
    Double v = doubleOrNull(obj.getValue(key));
    return v == null ? fallback : v;
  }

  private int intFrom(JsonObject obj, String key, int fallback) {
    Integer v = intOrNull(obj.getValue(key));
    return v == null ? fallback : v;
  }

  private Double doubleOrNull(Object raw) {
    if (raw == null) return null;
    if (raw instanceof Number n) return n.doubleValue();
    if (raw instanceof String s && !s.isBlank()) {
      try {
        return Double.parseDouble(s.trim());
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  private Integer intOrNull(Object raw) {
    if (raw == null) return null;
    if (raw instanceof Number n) return n.intValue();
    if (raw instanceof String s && !s.isBlank()) {
      try {
        return Integer.parseInt(s.trim());
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  public Future<Integer> delete(int id) {
    String sql = "DELETE FROM food_items WHERE id = $1";
    return db.query(sql, Tuple.of(id)).map(rows -> rows.rowCount());
  }

  public Future<Integer> countAll() {
    String sql = "SELECT COUNT(*)::int AS c FROM food_items";
    return db.query(sql, Tuple.tuple()).map(rows -> rows.iterator().hasNext() ? rows.iterator().next().getInteger("c") : 0);
  }

  public Future<Integer> countByTrafficLight(String trafficLight) {
    String light = trafficLight == null ? "" : trafficLight.trim().toUpperCase();
    if (light.isBlank()) {
      return Future.succeededFuture(0);
    }
    String sql = "SELECT COUNT(*)::int AS c FROM food_items WHERE UPPER(COALESCE(traffic_light, '')) = $1";
    return db.query(sql, Tuple.of(light)).map(rows -> rows.iterator().hasNext() ? rows.iterator().next().getInteger("c") : 0);
  }

  public Future<JsonArray> mealCandidates(String mealType) {
    String sql = """
      WITH normalized AS (
        SELECT
          id,
          to_jsonb(food_items)->>'traffic_light' AS traffic_light,
          COALESCE(
            to_jsonb(food_items)->>'food_name',
            to_jsonb(food_items)->>'name'
          ) AS food_name,
          COALESCE(
            to_jsonb(food_items)->>'definition',
            to_jsonb(food_items)->>'description',
            to_jsonb(food_items)->>'short_description'
          ) AS definition,
          COALESCE(
            to_jsonb(food_items)->>'image_url',
            to_jsonb(food_items)->>'imageUrl',
            to_jsonb(food_items)->>'image'
          ) AS image_url,
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
      WITH normalized AS (
        SELECT
          id,
          COALESCE(
            to_jsonb(food_items)->>'food_name',
            to_jsonb(food_items)->>'name'
          ) AS food_name,
          COALESCE(to_jsonb(food_items)->'vitamins', '{}'::jsonb) AS vitamins
        FROM food_items
      ),
      scored AS (
        SELECT
          id,
          food_name,
          vitamins,
          vitamins->>$1 AS vitamin_value,
          NULLIF(regexp_replace(COALESCE(vitamins->>$1, ''), '[^0-9\\\\.]', '', 'g'), '')::numeric AS vitamin_score
        FROM normalized
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
      .put("traffic_light", row.getString("traffic_light"))
      .put("food_name", row.getString("food_name"))
      .put("definition", row.getString("definition"))
      .put("image_url", row.getString("image_url"))
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

  private String normalizeLight(String raw) {
    if (raw == null) return "GREEN";
    String normalized = raw.trim().toUpperCase();
    if ("SAFE".equals(normalized)) return "GREEN";
    if ("CAUTION".equals(normalized) || "WARN".equals(normalized) || "WARNING".equals(normalized)) return "YELLOW";
    if ("AVOID".equals(normalized) || "DANGER".equals(normalized)) return "RED";
    if ("GREEN".equals(normalized) || "YELLOW".equals(normalized) || "RED".equals(normalized)) return normalized;
    return "GREEN";
  }
}
