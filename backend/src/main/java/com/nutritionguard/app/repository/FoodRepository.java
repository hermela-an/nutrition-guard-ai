package com.nutritionguard.app.repository;

import com.nutritionguard.app.db.Database;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;

import java.util.List;
import java.util.Map;

public class FoodRepository {
  private final Database db;

  public FoodRepository(Database db) {
    this.db = db;
  }

  private String normalizeCategory(String raw) {
    if (raw == null) return "Vegetables";
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) return "Vegetables";

    return switch (trimmed.toLowerCase()) {
      case "protein" -> "Protein";
      case "fruits", "fruit" -> "Fruits";
      case "vegetables", "vegetable", "veggies", "veggie" -> "Vegetables";
      case "dairy" -> "Dairy";
      case "grains", "grain" -> "Grains";
      default -> "Vegetables";
    };
  }

  public Future<JsonArray> search(String query) {
    String sql = """
      WITH normalized AS (
        SELECT
          id,
          CASE UPPER(COALESCE(to_jsonb(food_items)->>'traffic_light', ''))
            WHEN 'GREEN' THEN 'SAFE'
            WHEN 'YELLOW' THEN 'CAUTION'
            WHEN 'RED' THEN 'AVOID'
            WHEN 'SAFE' THEN 'SAFE'
            WHEN 'CAUTION' THEN 'CAUTION'
            WHEN 'AVOID' THEN 'AVOID'
            ELSE 'SAFE'
          END AS traffic_light,
          COALESCE(
            to_jsonb(food_items)->>'food_name',
            to_jsonb(food_items)->>'name'
          ) AS food_name,
          to_jsonb(food_items)->>'category' AS category,
          COALESCE(
            to_jsonb(food_items)->>'definition',
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
          COALESCE(to_jsonb(food_items)->'vitamins', '{}'::jsonb) AS vitamins,
          COALESCE(to_jsonb(food_items)->'health_effect', '{}'::jsonb) AS health_effect
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
          CASE UPPER(COALESCE(to_jsonb(food_items)->>'traffic_light', ''))
            WHEN 'GREEN' THEN 'SAFE'
            WHEN 'YELLOW' THEN 'CAUTION'
            WHEN 'RED' THEN 'AVOID'
            WHEN 'SAFE' THEN 'SAFE'
            WHEN 'CAUTION' THEN 'CAUTION'
            WHEN 'AVOID' THEN 'AVOID'
            ELSE 'SAFE'
          END AS traffic_light,
          COALESCE(
            to_jsonb(food_items)->>'food_name',
            to_jsonb(food_items)->>'name'
          ) AS food_name,
          to_jsonb(food_items)->>'category' AS category,
          COALESCE(
            to_jsonb(food_items)->>'definition',
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
          COALESCE(to_jsonb(food_items)->'vitamins', '{}'::jsonb) AS vitamins,
          COALESCE(to_jsonb(food_items)->'health_effect', '{}'::jsonb) AS health_effect
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
          CASE UPPER(COALESCE(to_jsonb(food_items)->>'traffic_light', ''))
            WHEN 'GREEN' THEN 'SAFE'
            WHEN 'YELLOW' THEN 'CAUTION'
            WHEN 'RED' THEN 'AVOID'
            WHEN 'SAFE' THEN 'SAFE'
            WHEN 'CAUTION' THEN 'CAUTION'
            WHEN 'AVOID' THEN 'AVOID'
            ELSE 'SAFE'
          END AS traffic_light,
          COALESCE(
            to_jsonb(food_items)->>'food_name',
            to_jsonb(food_items)->>'name'
          ) AS food_name,
          to_jsonb(food_items)->>'category' AS category,
          COALESCE(
            to_jsonb(food_items)->>'definition',
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
          COALESCE(to_jsonb(food_items)->'vitamins', '{}'::jsonb) AS vitamins,
          COALESCE(to_jsonb(food_items)->'health_effect', '{}'::jsonb) AS health_effect
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
        category,
        meal_type,
        portion_amount,
        portion_unit,
        calories,
        protein_g,
        carbs_g,
        fat_g,
        sugar_g,
        sodium_mg,
        cholesterol_mg,
        vitamins,
        health_effect,
        image_url,
        definition,
        traffic_light
      )
      VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13::jsonb,$14::jsonb,$15,$16,$17)
      RETURNING
        id,
        traffic_light,
        food_name,
        category,
        definition,
        image_url,
        meal_type,
        (portion_amount)::double precision AS portion_amount,
        portion_unit,
        (calories)::numeric::int AS calories,
        (protein_g)::double precision AS protein_g,
        (carbs_g)::double precision AS carbs_g,
        (fat_g)::double precision AS fat_g,
        (sugar_g)::double precision AS sugar_g,
        (sodium_mg)::double precision AS sodium_mg,
        (cholesterol_mg)::double precision AS cholesterol_mg,
        COALESCE(vitamins, '{}'::jsonb) AS vitamins,
        COALESCE(health_effect, '{}'::jsonb) AS health_effect
      """;

    return db
      .query(sql, Tuple.of(
        safe.getString("food_name", ""),
        normalizeCategory(safe.getString("category")),
        safe.getString("meal_type", ""),
        portionValue,
        safe.getString("portion_unit", "serving"),
        caloriesKcal,
        doubleFrom(safe, "protein_g", 0.0),
        doubleFrom(safe, "carbs_g", 0.0),
        doubleFrom(safe, "fat_g", 0.0),
        doubleFrom(safe, "sugar_g", 0.0),
        doubleFrom(safe, "sodium_mg", 0.0),
        doubleFrom(safe, "cholesterol_mg", 0.0),
        encodeJsonOrDefault(safe, "vitamins", "{}"),
        encodeJsonOrDefault(safe, "health_effect", "{}"),
        safe.getString("image_url"),
        safe.getString("definition"),
        normalizeLight(safe.getString("traffic_light", "SAFE"))
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
        category = COALESCE($3, category),
        meal_type = COALESCE($4, meal_type),
        portion_amount = COALESCE($5, portion_amount),
        portion_unit = COALESCE($6, portion_unit),
        calories = COALESCE($7, calories),
        protein_g = COALESCE($8, protein_g),
        carbs_g = COALESCE($9, carbs_g),
        fat_g = COALESCE($10, fat_g),
        sugar_g = COALESCE($11, sugar_g),
        sodium_mg = COALESCE($12, sodium_mg),
        cholesterol_mg = COALESCE($13, cholesterol_mg),
        vitamins = COALESCE($14::jsonb, vitamins),
        health_effect = COALESCE($15::jsonb, health_effect),
        image_url = COALESCE($16, image_url),
        definition = COALESCE($17, definition),
        traffic_light = COALESCE($18, traffic_light)
      WHERE id = $1
      RETURNING
        id,
        traffic_light,
        food_name,
        category,
        definition,
        image_url,
        meal_type,
        (portion_amount)::double precision AS portion_amount,
        portion_unit,
        (calories)::numeric::int AS calories,
        (protein_g)::double precision AS protein_g,
        (carbs_g)::double precision AS carbs_g,
        (fat_g)::double precision AS fat_g,
        (sugar_g)::double precision AS sugar_g,
        (sodium_mg)::double precision AS sodium_mg,
        (cholesterol_mg)::double precision AS cholesterol_mg,
        COALESCE(vitamins, '{}'::jsonb) AS vitamins,
        COALESCE(health_effect, '{}'::jsonb) AS health_effect
      """;

    return db
      .query(sql, Tuple.of(
        id,
        safe.containsKey("food_name") ? safe.getString("food_name") : null,
        safe.containsKey("category") ? normalizeCategory(safe.getString("category")) : null,
        safe.containsKey("meal_type") ? safe.getString("meal_type") : null,
        portionValue,
        safe.containsKey("portion_unit") ? safe.getString("portion_unit") : null,
        caloriesKcal,
        safe.containsKey("protein_g") ? doubleOrNull(safe.getValue("protein_g")) : null,
        safe.containsKey("carbs_g") ? doubleOrNull(safe.getValue("carbs_g")) : null,
        safe.containsKey("fat_g") ? doubleOrNull(safe.getValue("fat_g")) : null,
        safe.containsKey("sugar_g") ? doubleOrNull(safe.getValue("sugar_g")) : null,
        safe.containsKey("sodium_mg") ? doubleOrNull(safe.getValue("sodium_mg")) : null,
        safe.containsKey("cholesterol_mg") ? doubleOrNull(safe.getValue("cholesterol_mg")) : null,
        encodeJsonOrNull(safe, "vitamins"),
        encodeJsonOrNull(safe, "health_effect"),
        safe.containsKey("image_url") ? safe.getString("image_url") : null,
        safe.containsKey("definition") ? safe.getString("definition") : null,
        safe.containsKey("traffic_light") ? normalizeLight(safe.getString("traffic_light")) : null
      ))
      .map(rows -> {
        if (!rows.iterator().hasNext()) {
          return null;
        }
        return toFood(rows.iterator().next());
      });
  }

  private String encodeJsonOrDefault(JsonObject obj, String key, String defaultJson) {
    String encoded = encodeJsonOrNull(obj, key);
    return encoded == null ? defaultJson : encoded;
  }

  private String encodeJsonOrNull(JsonObject obj, String key) {
    if (obj == null || key == null || key.isBlank() || !obj.containsKey(key)) return null;
    Object v = obj.getValue(key);
    if (v == null) return null;

    if (v instanceof JsonObject jo) return jo.encode();
    if (v instanceof JsonArray ja) return ja.encode();

    if (v instanceof Map<?, ?> m) {
      @SuppressWarnings("unchecked")
      Map<String, Object> cast = (Map<String, Object>) m;
      return new JsonObject(cast).encode();
    }
    if (v instanceof List<?> l) {
      @SuppressWarnings("unchecked")
      List<Object> cast = (List<Object>) l;
      return new JsonArray(cast).encode();
    }

    if (v instanceof String s) {
      String trimmed = s.trim();
      if (trimmed.isEmpty()) return null;
      // If the client already sent a JSON string, pass it through when it looks valid-ish.
      if (trimmed.startsWith("{") || trimmed.startsWith("[")) return trimmed;
      return null;
    }

    return null;
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
          CASE UPPER(COALESCE(to_jsonb(food_items)->>'traffic_light', ''))
            WHEN 'GREEN' THEN 'SAFE'
            WHEN 'YELLOW' THEN 'CAUTION'
            WHEN 'RED' THEN 'AVOID'
            WHEN 'SAFE' THEN 'SAFE'
            WHEN 'CAUTION' THEN 'CAUTION'
            WHEN 'AVOID' THEN 'AVOID'
            ELSE 'SAFE'
          END AS traffic_light,
          COALESCE(
            to_jsonb(food_items)->>'food_name',
            to_jsonb(food_items)->>'name'
          ) AS food_name,
          to_jsonb(food_items)->>'category' AS category,
          COALESCE(
            to_jsonb(food_items)->>'definition',
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
          COALESCE(to_jsonb(food_items)->'vitamins', '{}'::jsonb) AS vitamins,
          COALESCE(to_jsonb(food_items)->'health_effect', '{}'::jsonb) AS health_effect
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
      .put("category", row.getString("category"))
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
      .put("vitamins", row.getJsonObject("vitamins"))
      .put("health_effect", row.getJsonObject("health_effect"));
  }

  private String normalizeLight(String raw) {
    if (raw == null) return "SAFE";
    String normalized = raw.trim().toUpperCase();
    if ("SAFE".equals(normalized) || "GREEN".equals(normalized) || "OK".equals(normalized)) return "SAFE";
    if ("CAUTION".equals(normalized) || "YELLOW".equals(normalized) || "WARN".equals(normalized) || "WARNING".equals(normalized)) return "CAUTION";
    if ("AVOID".equals(normalized) || "RED".equals(normalized) || "DANGER".equals(normalized)) return "AVOID";
    return "SAFE";
  }
}
