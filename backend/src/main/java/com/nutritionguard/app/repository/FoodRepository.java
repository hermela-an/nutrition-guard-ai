package com.nutritionguard.app.repository;

import com.nutritionguard.app.db.Database;
import com.nutritionguard.app.util.RowJsonUtil;
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
      case "carbohydrates", "carbohydrate", "carbs", "carb" -> "Carbohydrates";
      case "nuts and seeds", "nuts & seeds", "nuts", "nut", "seeds", "seed", "nuts_and_seeds", "nuts-and-seeds" -> "Nuts and Seeds";
      default -> "Vegetables";
    };
  }

  private String normalizeFoodRole(String raw) {
    if (raw == null || raw.trim().isEmpty()) return "main_food";
    String trimmed = raw.trim().toLowerCase();
    if (trimmed.contains("standalone")) return "standalone_food";
    if (trimmed.contains("side")) return "side_food";
    return "main_food";
  }

  public Future<JsonArray> search(String query) {
    String sql = """
      SELECT
        id,
        COALESCE(item_type, 'food') AS item_type,
        COALESCE(is_recommended_base, FALSE) AS is_recommended_base,
        components_json,
        CASE UPPER(COALESCE(traffic_light, ''))
          WHEN 'GREEN' THEN 'SAFE'
          WHEN 'YELLOW' THEN 'CAUTION'
          WHEN 'RED' THEN 'AVOID'
          WHEN 'SAFE' THEN 'SAFE'
          WHEN 'CAUTION' THEN 'CAUTION'
          WHEN 'AVOID' THEN 'AVOID'
          ELSE 'SAFE'
        END AS traffic_light,
        food_name,
        category,
        definition,
        image_url,
        meal_type,
        food_role,
        (portion_amount)::double precision AS portion_amount,
        portion_unit,
        (calories_kcal)::numeric::int AS calories_kcal,
        (protein_g)::double precision AS protein_g,
        (carbs_g)::double precision AS carbs_g,
        (fat_g)::double precision AS fat_g,
        (fiber_g)::double precision AS fiber_g,
        (sugar_g)::double precision AS sugar_g,
        (sodium_mg)::double precision AS sodium_mg,
        (cholesterol_mg)::double precision AS cholesterol_mg,
        (glycemic_index)::numeric::int AS glycemic_index,
        COALESCE(vitamins, '{}'::jsonb) AS vitamins,
        ingredients,
        COALESCE(health_effect, '{}'::jsonb) AS health_effect
      FROM food_items
      WHERE (food_name ILIKE $1 OR category ILIKE $1 OR definition ILIKE $1) AND is_deleted = false
      ORDER BY food_name ASC
      LIMIT 100
      """;
    return db.query(sql, Tuple.of("%" + query + "%")).map(this::rowsToFoods);
  }

  public Future<JsonObject> findBestByFoodName(String foodName) {
    String name = foodName == null ? "" : foodName.trim();
    if (name.isBlank()) {
      return Future.succeededFuture(null);
    }

    String sql = """
      WITH normalized AS (
        SELECT
          id,
          COALESCE(to_jsonb(food_items)->>'item_type', 'food') AS item_type,
          to_jsonb(food_items)->'components_json' AS components_json,
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
          COALESCE(food_role, CASE WHEN LOWER(COALESCE(meal_type, '')) = 'side' THEN 'side_food' ELSE 'main_food' END) AS food_role,
          COALESCE(
            (to_jsonb(food_items)->>'portion_amount')::numeric::double precision,
            (to_jsonb(food_items)->>'portion_value')::numeric::double precision
          ) AS portion_amount,
          to_jsonb(food_items)->>'portion_unit' AS portion_unit,
          COALESCE(
            (to_jsonb(food_items)->>'calories')::numeric::int,
            (to_jsonb(food_items)->>'calories_kcal')::numeric::int,
            0
          ) AS calories_kcal,
          COALESCE((to_jsonb(food_items)->>'protein_g')::numeric::double precision, 0) AS protein_g,
          COALESCE((to_jsonb(food_items)->>'carbs_g')::numeric::double precision, 0) AS carbs_g,
          COALESCE((to_jsonb(food_items)->>'fat_g')::numeric::double precision, 0) AS fat_g,
          COALESCE((to_jsonb(food_items)->>'fiber_g')::numeric::double precision, 0) AS fiber_g,
          COALESCE((to_jsonb(food_items)->>'sugar_g')::numeric::double precision, 0) AS sugar_g,
          COALESCE((to_jsonb(food_items)->>'sodium_mg')::numeric::double precision, 0) AS sodium_mg,
          COALESCE((to_jsonb(food_items)->>'cholesterol_mg')::numeric::double precision, 0) AS cholesterol_mg,
          COALESCE((to_jsonb(food_items)->>'glycemic_index')::numeric::int, 0) AS glycemic_index,
          COALESCE(to_jsonb(food_items)->'vitamins', '{}'::jsonb) AS vitamins,
          to_jsonb(food_items)->>'ingredients' AS ingredients,
          COALESCE(to_jsonb(food_items)->'health_effect', '{}'::jsonb) AS health_effect
        FROM food_items
        WHERE COALESCE(to_jsonb(food_items)->>'item_type', 'food') IN ('food', 'meal') AND is_deleted = false
      )
      SELECT *
      FROM normalized
      WHERE LOWER(COALESCE(food_name, '')) = LOWER($1)
      ORDER BY
        CASE LOWER(COALESCE(meal_type, ''))
          WHEN 'side' THEN 0
          WHEN 'snack' THEN 1
          WHEN 'breakfast' THEN 2
          WHEN 'lunch' THEN 3
          WHEN 'dinner' THEN 4
          ELSE 10
        END,
        calories ASC,
        id ASC
      LIMIT 1
      """;

    return db.query(sql, Tuple.of(name))
      .map(rows -> rows.iterator().hasNext() ? toFood(rows.iterator().next()) : null);
  }

  public Future<JsonArray> list(String query, int limit, int offset) {
    int safeLimit = Math.max(1, Math.min(limit, 200));
    int safeOffset = Math.max(0, Math.min(offset, 10_000));
    String q = query == null ? "" : query;

    String sql = """
      WITH normalized AS (
        SELECT
          id,
          COALESCE(to_jsonb(food_items)->>'item_type', 'food') AS item_type,
          COALESCE((to_jsonb(food_items)->>'is_recommended_base')::boolean, FALSE) AS is_recommended_base,
          to_jsonb(food_items)->'components_json' AS components_json,
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
          COALESCE(food_role, CASE WHEN LOWER(COALESCE(meal_type, '')) = 'side' THEN 'side_food' ELSE 'main_food' END) AS food_role,
          COALESCE(
            (to_jsonb(food_items)->>'portion_amount')::numeric::double precision,
            (to_jsonb(food_items)->>'portion_value')::numeric::double precision
          ) AS portion_amount,
          to_jsonb(food_items)->>'portion_unit' AS portion_unit,
          COALESCE(
            (to_jsonb(food_items)->>'calories')::numeric::int,
            (to_jsonb(food_items)->>'calories_kcal')::numeric::int,
            0
          ) AS calories_kcal,
          COALESCE((to_jsonb(food_items)->>'protein_g')::numeric::double precision, 0) AS protein_g,
          COALESCE((to_jsonb(food_items)->>'carbs_g')::numeric::double precision, 0) AS carbs_g,
          COALESCE((to_jsonb(food_items)->>'fat_g')::numeric::double precision, 0) AS fat_g,
          COALESCE((to_jsonb(food_items)->>'fiber_g')::numeric::double precision, 0) AS fiber_g,
          COALESCE((to_jsonb(food_items)->>'sugar_g')::numeric::double precision, 0) AS sugar_g,
          COALESCE((to_jsonb(food_items)->>'sodium_mg')::numeric::double precision, 0) AS sodium_mg,
          COALESCE((to_jsonb(food_items)->>'cholesterol_mg')::numeric::double precision, 0) AS cholesterol_mg,
          COALESCE((to_jsonb(food_items)->>'glycemic_index')::numeric::int, 0) AS glycemic_index,
          COALESCE(to_jsonb(food_items)->'vitamins', '{}'::jsonb) AS vitamins,
          to_jsonb(food_items)->>'ingredients' AS ingredients,
          COALESCE(to_jsonb(food_items)->'health_effect', '{}'::jsonb) AS health_effect
        FROM food_items
        WHERE COALESCE(to_jsonb(food_items)->>'item_type', 'food') IN ('food', 'meal') AND is_deleted = false
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

  // Admin-friendly ordering (id order). Keeps the user-facing list() alphabetical.
  public Future<JsonArray> listAdmin(String query, int limit, int offset) {
    int safeLimit = Math.max(1, Math.min(limit, 200));
    int safeOffset = Math.max(0, Math.min(offset, 10_000));
    String q = query == null ? "" : query;

    String sql = """
      WITH normalized AS (
        SELECT
          id,
          COALESCE(to_jsonb(food_items)->>'item_type', 'food') AS item_type,
          COALESCE((to_jsonb(food_items)->>'is_recommended_base')::boolean, FALSE) AS is_recommended_base,
          to_jsonb(food_items)->'components_json' AS components_json,
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
          food_role,
          COALESCE(
            (to_jsonb(food_items)->>'portion_amount')::numeric::double precision,
            (to_jsonb(food_items)->>'portion_value')::numeric::double precision
          ) AS portion_amount,
          to_jsonb(food_items)->>'portion_unit' AS portion_unit,
          COALESCE(
            (to_jsonb(food_items)->>'calories')::numeric::int,
            (to_jsonb(food_items)->>'calories_kcal')::numeric::int,
            0
          ) AS calories_kcal,
          COALESCE((to_jsonb(food_items)->>'protein_g')::numeric::double precision, 0) AS protein_g,
          COALESCE((to_jsonb(food_items)->>'carbs_g')::numeric::double precision, 0) AS carbs_g,
          COALESCE((to_jsonb(food_items)->>'fat_g')::numeric::double precision, 0) AS fat_g,
          COALESCE((to_jsonb(food_items)->>'fiber_g')::numeric::double precision, 0) AS fiber_g,
          COALESCE((to_jsonb(food_items)->>'sugar_g')::numeric::double precision, 0) AS sugar_g,
          COALESCE((to_jsonb(food_items)->>'sodium_mg')::numeric::double precision, 0) AS sodium_mg,
          COALESCE((to_jsonb(food_items)->>'cholesterol_mg')::numeric::double precision, 0) AS cholesterol_mg,
          COALESCE((to_jsonb(food_items)->>'glycemic_index')::numeric::int, 0) AS glycemic_index,
          COALESCE(to_jsonb(food_items)->'vitamins', '{}'::jsonb) AS vitamins,
          to_jsonb(food_items)->>'ingredients' AS ingredients,
          COALESCE(to_jsonb(food_items)->'health_effect', '{}'::jsonb) AS health_effect
        FROM food_items
        WHERE COALESCE(to_jsonb(food_items)->>'item_type', 'food') IN ('food', 'meal') AND is_deleted = false
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

  public Future<JsonObject> upsert(JsonObject input) {
    String name = input == null ? "" : input.getString("food_name", "");
    String mealType = input == null ? "" : input.getString("meal_type", "");

    if (name.isBlank()) {
      return Future.failedFuture("food_name is required for upsert");
    }

    String sql = "SELECT id FROM food_items WHERE LOWER(food_name) = LOWER($1) AND LOWER(meal_type) = LOWER($2) AND is_deleted = false LIMIT 1";
    return db.query(sql, Tuple.of(name, mealType)).compose(rows -> {
      if (rows.iterator().hasNext()) {
        int id = rows.iterator().next().getInteger("id");
        return update(id, input);
      } else {
        return create(input);
      }
    });
  }

  public Future<JsonObject> create(JsonObject input) {
    JsonObject safe = input == null ? new JsonObject() : input;
    double portionValue = doubleFrom(safe, "portion_value", doubleFrom(safe, "portion_amount", 1.0));
    int caloriesKcal = intFrom(safe, "calories_kcal", intFrom(safe, "calories", 0));
    String ingredients = safe.getString("ingredients");
    if (ingredients != null && ingredients.isBlank()) ingredients = null;

    String sql = """
      INSERT INTO food_items (
        id,
        food_name,
        category,
        meal_type,
        food_role,
        portion_amount,
        portion_unit,
        calories_kcal,
        protein_g,
        carbs_g,
        fat_g,
        fiber_g,
        sugar_g,
        sodium_mg,
        cholesterol_mg,
        glycemic_index,
        vitamins,
        ingredients,
        health_effect,
        image_url,
        definition,
        traffic_light,
        item_type,
        components_json
      )
      VALUES ((SELECT COALESCE(MAX(id), 0) + 1 FROM food_items), $1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16::jsonb,$17,$18::jsonb,$19,$20,$21,$22,$23::jsonb)
      RETURNING
        id,
        traffic_light,
        food_name,
        category,
        definition,
        image_url,
        meal_type,
        food_role,
        item_type,
        components_json,
        (portion_amount)::double precision AS portion_amount,
        portion_unit,
        (calories_kcal)::numeric::int AS calories_kcal,
        (protein_g)::double precision AS protein_g,
        (carbs_g)::double precision AS carbs_g,
        (fat_g)::double precision AS fat_g,
        (fiber_g)::double precision AS fiber_g,
        (sugar_g)::double precision AS sugar_g,
        (sodium_mg)::double precision AS sodium_mg,
        (cholesterol_mg)::double precision AS cholesterol_mg,
        (glycemic_index)::numeric::int AS glycemic_index,
        vitamins,
        ingredients,
        health_effect
      """;

    return db
      .query(sql, Tuple.of(
        safe.getString("food_name", ""),
        normalizeCategory(safe.getString("category")),
        safe.getString("meal_type", ""),
        normalizeFoodRole(safe.getString("food_role")),
        portionValue,
        safe.getString("portion_unit", "serving"),
        caloriesKcal,
        doubleFrom(safe, "protein_g", 0.0),
        doubleFrom(safe, "carbs_g", 0.0),
        doubleFrom(safe, "fat_g", 0.0),
        doubleFrom(safe, "fiber_g", 0.0),
        doubleFrom(safe, "sugar_g", 0.0),
        doubleFrom(safe, "sodium_mg", 0.0),
        doubleFrom(safe, "cholesterol_mg", 0.0),
        intFrom(safe, "glycemic_index", 0),
        encodeJsonOrDefault(safe, "vitamins", "{}"),
        ingredients,
        encodeJsonOrDefault(safe, "health_effect", "{}"),
        safe.getString("image_url"),
        safe.getString("definition"),
        normalizeLight(safe.getString("traffic_light", "SAFE")),
        safe.getString("item_type", safe.getString("type", "food")),
        encodeJsonOrNull(safe, "components_json")
      ))
      .map(rows -> toFood(rows.iterator().next()));
  }

  public Future<JsonObject> update(int id, JsonObject input) {
    JsonObject safe = input == null ? new JsonObject() : input;

    String normalizedCategoryOrNull = null;
    if (safe.containsKey("category")) {
      String rawCategory = safe.getString("category");
      if (rawCategory != null && !rawCategory.isBlank()) {
        normalizedCategoryOrNull = normalizeCategory(rawCategory);
      }
    }

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
        needs_sync = true,
        food_name = COALESCE($2, food_name),
        category = COALESCE($3, category),
        meal_type = COALESCE($4, meal_type),
        food_role = CASE WHEN $5 IS NOT NULL AND $5 <> '' THEN $5 ELSE food_role END,
        portion_amount = COALESCE($6, portion_amount),
        portion_unit = COALESCE($7, portion_unit),
        calories_kcal = COALESCE($8, calories_kcal),
        protein_g = COALESCE($9, protein_g),
        carbs_g = COALESCE($10, carbs_g),
        fat_g = COALESCE($11, fat_g),
        fiber_g = COALESCE($12, fiber_g),
        sugar_g = COALESCE($13, sugar_g),
        sodium_mg = COALESCE($14, sodium_mg),
        cholesterol_mg = COALESCE($15, cholesterol_mg),
        glycemic_index = COALESCE($16, glycemic_index),
        vitamins = COALESCE($17::jsonb, vitamins),
         health_effect = COALESCE($18::jsonb, health_effect),
         ingredients = COALESCE($19, ingredients),
         image_url = COALESCE($20, image_url),
         definition = COALESCE($21, definition),
         traffic_light = COALESCE(
           $22,
           CASE UPPER(COALESCE(to_jsonb(food_items)->>'traffic_light', ''))
             WHEN 'GREEN' THEN 'SAFE'
             WHEN 'YELLOW' THEN 'CAUTION'
             WHEN 'RED' THEN 'AVOID'
             WHEN 'SAFE' THEN 'SAFE'
             WHEN 'CAUTION' THEN 'CAUTION'
             WHEN 'AVOID' THEN 'AVOID'
             ELSE 'SAFE'
           END
       ),
       item_type = COALESCE($23, item_type),
       components_json = COALESCE($24::jsonb, components_json)
       WHERE id = $1
       RETURNING
         id,
         traffic_light,
        food_name,
        category,
        definition,
        image_url,
        meal_type,
        food_role,
        item_type,
        components_json,
        (portion_amount)::double precision AS portion_amount,
        portion_unit,
        (calories_kcal)::numeric::int AS calories_kcal,
        (protein_g)::double precision AS protein_g,
        (carbs_g)::double precision AS carbs_g,
        (fat_g)::double precision AS fat_g,
        (fiber_g)::double precision AS fiber_g,
        (sugar_g)::double precision AS sugar_g,
        (sodium_mg)::double precision AS sodium_mg,
        (cholesterol_mg)::double precision AS cholesterol_mg,
        (glycemic_index)::numeric::int AS glycemic_index,
        vitamins,
        ingredients,
        health_effect
      """;

    return db
      .query(sql, Tuple.of(
        id,
        safe.containsKey("food_name") ? safe.getString("food_name") : null,
        normalizedCategoryOrNull,
        safe.containsKey("meal_type") ? safe.getString("meal_type") : null,
        safe.containsKey("food_role") ? normalizeFoodRole(safe.getString("food_role")) : null,
        portionValue,
        safe.containsKey("portion_unit") ? safe.getString("portion_unit") : null,
        caloriesKcal,
        safe.containsKey("protein_g") ? doubleOrNull(safe.getValue("protein_g")) : null,
        safe.containsKey("carbs_g") ? doubleOrNull(safe.getValue("carbs_g")) : null,
        safe.containsKey("fat_g") ? doubleOrNull(safe.getValue("fat_g")) : null,
        safe.containsKey("fiber_g") ? doubleOrNull(safe.getValue("fiber_g")) : null,
        safe.containsKey("sugar_g") ? doubleOrNull(safe.getValue("sugar_g")) : null,
        safe.containsKey("sodium_mg") ? doubleOrNull(safe.getValue("sodium_mg")) : null,
        safe.containsKey("cholesterol_mg") ? doubleOrNull(safe.getValue("cholesterol_mg")) : null,
        safe.containsKey("glycemic_index") ? intOrNull(safe.getValue("glycemic_index")) : null,
        encodeJsonOrNull(safe, "vitamins"),
        encodeJsonOrNull(safe, "health_effect"),
        safe.containsKey("ingredients") ? safe.getString("ingredients") : null,
        safe.containsKey("image_url") ? safe.getString("image_url") : null,
        safe.containsKey("definition") ? safe.getString("definition") : null,
        safe.containsKey("traffic_light") ? normalizeLight(safe.getString("traffic_light")) : null,
        safe.containsKey("item_type") ? safe.getString("item_type") : (safe.containsKey("type") ? safe.getString("type") : null),
        safe.containsKey("components_json") ? encodeJsonOrNull(safe, "components_json") : null
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
      // If the client already sent JSON text, validate it before passing through.
      if (trimmed.startsWith("{")) {
        try {
          return new JsonObject(trimmed).encode();
        } catch (RuntimeException ignored) {
          return null;
        }
      }
      if (trimmed.startsWith("[")) {
        try {
          return new JsonArray(trimmed).encode();
        } catch (RuntimeException ignored) {
          return null;
        }
      }
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
    String sql = "UPDATE food_items SET is_deleted = true, needs_sync = true WHERE id = $1";
    return db.query(sql, Tuple.of(id)).map(rows -> rows.rowCount());
  }

  public Future<Integer> countAll() {
    String sql = "SELECT COUNT(*)::int AS c FROM food_items WHERE COALESCE(to_jsonb(food_items)->>'item_type', 'food') IN ('food', 'meal') AND is_deleted = false";
    return db.query(sql, Tuple.tuple()).map(rows -> rows.iterator().hasNext() ? rows.iterator().next().getInteger("c") : 0);
  }

  public Future<Integer> countByTrafficLight(String trafficLight) {
    String light = trafficLight == null ? "" : trafficLight.trim().toUpperCase();
    if (light.isBlank()) {
      return Future.succeededFuture(0);
    }
    String sql = """
      WITH normalized AS (
        SELECT
          CASE UPPER(COALESCE(to_jsonb(food_items)->>'traffic_light', ''))
            WHEN 'GREEN' THEN 'SAFE'
            WHEN 'YELLOW' THEN 'CAUTION'
            WHEN 'RED' THEN 'AVOID'
            WHEN 'SAFE' THEN 'SAFE'
            WHEN 'CAUTION' THEN 'CAUTION'
            WHEN 'AVOID' THEN 'AVOID'
            ELSE 'SAFE'
          END AS traffic_light
        FROM food_items
        WHERE COALESCE(to_jsonb(food_items)->>'item_type', 'food') IN ('food', 'meal') AND is_deleted = false
      )
      SELECT COUNT(*)::int AS c
      FROM normalized
      WHERE traffic_light = $1
      """;
    return db.query(sql, Tuple.of(light))
      .map(rows -> rows.iterator().hasNext() ? rows.iterator().next().getInteger("c") : 0);
  }

  public Future<JsonArray> mealCandidates(String mealType) {
    String sql = """
      SELECT
        id,
        COALESCE(item_type, 'food') AS item_type,
        COALESCE(is_recommended_base, FALSE) AS is_recommended_base,
        components_json,
        CASE UPPER(COALESCE(traffic_light, ''))
          WHEN 'GREEN' THEN 'SAFE'
          WHEN 'YELLOW' THEN 'CAUTION'
          WHEN 'RED' THEN 'AVOID'
          WHEN 'SAFE' THEN 'SAFE'
          WHEN 'CAUTION' THEN 'CAUTION'
          WHEN 'AVOID' THEN 'AVOID'
          ELSE 'SAFE'
        END AS traffic_light,
        food_name,
        category,
        definition,
        image_url,
        meal_type,
        food_role,
        (portion_amount)::double precision AS portion_amount,
        portion_unit,
        (calories_kcal)::numeric::int AS calories_kcal,
        (protein_g)::double precision AS protein_g,
        (carbs_g)::double precision AS carbs_g,
        (fat_g)::double precision AS fat_g,
        (fiber_g)::double precision AS fiber_g,
        (sugar_g)::double precision AS sugar_g,
        (sodium_mg)::double precision AS sodium_mg,
        (cholesterol_mg)::double precision AS cholesterol_mg,
        (glycemic_index)::numeric::int AS glycemic_index,
        COALESCE(vitamins, '{}'::jsonb) AS vitamins,
        ingredients,
        COALESCE(health_effect, '{}'::jsonb) AS health_effect
      FROM food_items
      WHERE LOWER(meal_type) = LOWER($1) 
        AND food_role NOT IN ('side', 'side_food')
        AND is_deleted = false
      ORDER BY calories_kcal ASC
      LIMIT 100
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
        FROM food_items WHERE is_deleted = false
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
            .put("vitamins", RowJsonUtil.getJsonObject(row, "vitamins"))
            .put("vitamin_value", row.getString("vitamin_value"))
          );
        }
        return items;
      });
  }

  public Future<JsonArray> findSideFoods() {
    String sql = """
      WITH normalized AS (
        SELECT
          id,
          COALESCE(to_jsonb(food_items)->>'item_type', 'food') AS item_type,
          COALESCE((to_jsonb(food_items)->>'is_recommended_base')::boolean, FALSE) AS is_recommended_base,
          to_jsonb(food_items)->'components_json' AS components_json,
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
          COALESCE(food_role, CASE WHEN LOWER(COALESCE(meal_type, '')) = 'side' THEN 'side_food' ELSE 'main_food' END) AS food_role,
          COALESCE(
            (to_jsonb(food_items)->>'portion_amount')::numeric::double precision,
            (to_jsonb(food_items)->>'portion_value')::numeric::double precision
          ) AS portion_amount,
          to_jsonb(food_items)->>'portion_unit' AS portion_unit,
          COALESCE(
            (to_jsonb(food_items)->>'calories')::numeric::int,
            (to_jsonb(food_items)->>'calories_kcal')::numeric::int,
            0
          ) AS calories_kcal,
          COALESCE((to_jsonb(food_items)->>'protein_g')::numeric::double precision, 0) AS protein_g,
          COALESCE((to_jsonb(food_items)->>'carbs_g')::numeric::double precision, 0) AS carbs_g,
          COALESCE((to_jsonb(food_items)->>'fat_g')::numeric::double precision, 0) AS fat_g,
          COALESCE((to_jsonb(food_items)->>'fiber_g')::numeric::double precision, 0) AS fiber_g,
          COALESCE((to_jsonb(food_items)->>'sugar_g')::numeric::double precision, 0) AS sugar_g,
          COALESCE((to_jsonb(food_items)->>'sodium_mg')::numeric::double precision, 0) AS sodium_mg,
          COALESCE((to_jsonb(food_items)->>'cholesterol_mg')::numeric::double precision, 0) AS cholesterol_mg,
          COALESCE((to_jsonb(food_items)->>'glycemic_index')::numeric::int, 0) AS glycemic_index,
          COALESCE(to_jsonb(food_items)->'vitamins', '{}'::jsonb) AS vitamins,
          to_jsonb(food_items)->>'ingredients' AS ingredients,
          COALESCE(to_jsonb(food_items)->'health_effect', '{}'::jsonb) AS health_effect
        FROM food_items
        WHERE is_deleted = false
      )
      SELECT *
      FROM normalized
      WHERE food_role IN ('side', 'side_food')
      ORDER BY food_name ASC
      """;
    return db.query(sql, Tuple.tuple()).map(this::rowsToFoods);
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
      .put("category", normalizeCategory(row.getString("category")))
      .put("definition", row.getString("definition"))
      .put("image_url", row.getString("image_url"))
      .put("meal_type", row.getString("meal_type"))
      .put("food_role", normalizeFoodRole(row.getString("food_role")))
      .put("item_type", row.getString("item_type"))
      .put("components_json", decodeJsonValue(row.getValue("components_json")))
      .put("portion_amount", row.getDouble("portion_amount"))
      .put("portion_unit", row.getString("portion_unit"))
      .put("calories", row.getInteger("calories_kcal"))
      .put("protein_g", row.getDouble("protein_g"))
      .put("carbs_g", row.getDouble("carbs_g"))
      .put("fat_g", row.getDouble("fat_g"))
      .put("fiber_g", row.getDouble("fiber_g"))
      .put("sugar_g", row.getDouble("sugar_g"))
      .put("sodium_mg", row.getDouble("sodium_mg"))
      .put("cholesterol_mg", row.getDouble("cholesterol_mg"))
      .put("glycemic_index", row.getInteger("glycemic_index"))
      .put("vitamins", RowJsonUtil.getJsonObject(row, "vitamins"))
      .put("ingredients", row.getString("ingredients"))
      .put("health_effect", RowJsonUtil.getJsonObject(row, "health_effect"));
  }

  private Object decodeJsonValue(Object value) {
    if (value == null) return null;
    if (value instanceof JsonObject obj) return obj;
    if (value instanceof JsonArray arr) return arr;
    if (value instanceof io.vertx.core.buffer.Buffer b) {
      try {
        return b.toJsonObject();
      } catch (RuntimeException ignored) {
        try {
          return b.toJsonArray();
        } catch (RuntimeException ignored2) {
          return null;
        }
      }
    }
    if (value instanceof String s) {
      String trimmed = s.trim();
      if (trimmed.isEmpty()) return null;
      try {
        return new JsonObject(trimmed);
      } catch (RuntimeException ignored) {
        try {
          return new JsonArray(trimmed);
        } catch (RuntimeException ignored2) {
          return null;
        }
      }
    }
    if (value instanceof Map<?, ?> m) {
      try {
        @SuppressWarnings("unchecked")
        Map<String, Object> castMap = (Map<String, Object>) m;
        return new JsonObject(castMap);
      } catch (RuntimeException ignored) {
        return null;
      }
    }
    return null;
  }

  private String normalizeLight(String raw) {
    if (raw == null) return "SAFE";
    String normalized = raw.trim().toUpperCase();
    if ("SAFE".equals(normalized) || "GREEN".equals(normalized) || "OK".equals(normalized)) return "SAFE";
    if ("CAUTION".equals(normalized) || "YELLOW".equals(normalized) || "WARN".equals(normalized) || "WARNING".equals(normalized)) return "CAUTION";
    if ("AVOID".equals(normalized) || "RED".equals(normalized) || "DANGER".equals(normalized)) return "AVOID";
    return "SAFE";
  }

  public Future<JsonArray> findUnsynced() {
    String sql = """
      WITH normalized AS (
        SELECT
          id,
          COALESCE(to_jsonb(food_items)->>'item_type', 'food') AS item_type,
          COALESCE((to_jsonb(food_items)->>'is_recommended_base')::boolean, FALSE) AS is_recommended_base,
          to_jsonb(food_items)->'components_json' AS components_json,
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
          COALESCE(food_role, CASE WHEN LOWER(COALESCE(meal_type, '')) = 'side' THEN 'side_food' ELSE 'main_food' END) AS food_role,
          COALESCE(
            (to_jsonb(food_items)->>'portion_amount')::numeric::double precision,
            (to_jsonb(food_items)->>'portion_value')::numeric::double precision
          ) AS portion_amount,
          to_jsonb(food_items)->>'portion_unit' AS portion_unit,
          COALESCE(
            (to_jsonb(food_items)->>'calories')::numeric::int,
            (to_jsonb(food_items)->>'calories_kcal')::numeric::int,
            0
          ) AS calories_kcal,
          COALESCE((to_jsonb(food_items)->>'protein_g')::numeric::double precision, 0) AS protein_g,
          COALESCE((to_jsonb(food_items)->>'carbs_g')::numeric::double precision, 0) AS carbs_g,
          COALESCE((to_jsonb(food_items)->>'fat_g')::numeric::double precision, 0) AS fat_g,
          COALESCE((to_jsonb(food_items)->>'fiber_g')::numeric::double precision, 0) AS fiber_g,
          COALESCE((to_jsonb(food_items)->>'sugar_g')::numeric::double precision, 0) AS sugar_g,
          COALESCE((to_jsonb(food_items)->>'sodium_mg')::numeric::double precision, 0) AS sodium_mg,
          COALESCE((to_jsonb(food_items)->>'cholesterol_mg')::numeric::double precision, 0) AS cholesterol_mg,
          COALESCE((to_jsonb(food_items)->>'glycemic_index')::numeric::int, 0) AS glycemic_index,
          COALESCE(to_jsonb(food_items)->'vitamins', '{}'::jsonb) AS vitamins,
          to_jsonb(food_items)->>'ingredients' AS ingredients,
          COALESCE(to_jsonb(food_items)->'health_effect', '{}'::jsonb) AS health_effect,
          needs_sync,
          is_deleted
        FROM food_items
        WHERE needs_sync = true
      )
      SELECT *
      FROM normalized
      """;
    return db.query(sql, Tuple.tuple()).map(this::rowsToFoods);
  }

  /**
   * Returns foods ordered descending by a specific nutritional column.
   * The column name is validated against a strict whitelist to prevent SQL injection.
   */
  public Future<JsonArray> findFoodsRichInColumn(String columnName, int limit) {
    int safeLimit = Math.max(1, Math.min(limit, 50));

    // Strict whitelist — only known numeric columns are allowed
    String col = switch (columnName) {
      case "fiber_g"        -> "fiber_g";
      case "sodium_mg"      -> "sodium_mg";
      case "cholesterol_mg" -> "cholesterol_mg";
      case "protein_g"      -> "protein_g";
      default               -> null;
    };

    if (col == null) {
      return Future.succeededFuture(new JsonArray());
    }

    String unit = col.endsWith("_mg") ? " mg" : " g";

    String sql =
      "SELECT id, food_name, (" + col + ")::double precision AS nutrient_val " +
      "FROM food_items " +
      "WHERE is_deleted = false " +
      "  AND " + col + " IS NOT NULL " +
      "  AND (" + col + ")::double precision > 0 " +
      "ORDER BY (" + col + ")::double precision DESC " +
      "LIMIT $1";

    return db.query(sql, Tuple.of(safeLimit))
      .map(rows -> {
        JsonArray items = new JsonArray();
        for (Row row : rows) {
          Double val = row.getDouble("nutrient_val");
          String valueStr = val != null ? String.format("%.1f%s", val, unit) : "";
          items.add(new JsonObject()
            .put("id", row.getInteger("id"))
            .put("food_name", row.getString("food_name"))
            .put("nutrient_value", valueStr)
          );
        }
        return items;
      })
      .recover(err -> {
        System.err.println("findFoodsRichInColumn error [" + col + "]: " + err.getMessage());
        return Future.succeededFuture(new JsonArray());
      });
  }

  public Future<Integer> markSynced() {
    String sql = "UPDATE food_items SET needs_sync = false WHERE needs_sync = true";
    return db.query(sql, Tuple.tuple()).map(rows -> rows.rowCount());
  }

  public io.vertx.core.Future<Void> renumber() {
    return db.pool().withTransaction(conn ->
      conn.query("DELETE FROM food_items WHERE is_deleted = true").execute()
        .compose(v -> conn.query("UPDATE food_items SET id = -id").execute())
        .compose(v -> conn.query("UPDATE food_items f SET id = m.new_id FROM (SELECT id, row_number() OVER (ORDER BY -id ASC) as new_id FROM food_items) m WHERE f.id = m.id").execute())
        .compose(v -> conn.query("SELECT setval('food_items_id_seq', (SELECT COALESCE(MAX(id), 0) FROM food_items), true)").execute())
        .mapEmpty()
    );
  }
}

