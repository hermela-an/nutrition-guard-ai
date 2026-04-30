package com.nutritionguard.app.repository;

import com.nutritionguard.app.db.Database;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class FoodItemRepository {
  private final Database db;

  public FoodItemRepository(Database db) {
    this.db = db;
  }

  public Future<JsonArray> listRecommendedBaseFoods(String query, int limit, int offset) {
    int safeLimit = Math.max(1, Math.min(limit, 200));
    int safeOffset = Math.max(0, Math.min(offset, 10_000));
    String q = query == null ? "" : query.trim();

    String sql = """
      SELECT
        id,
        food_name,
        item_type,
        FALSE AS is_recommended_base,
        calories_per_100g,
        protein,
        carbs,
        fat,
        portion_amount,
        portion_unit,
        calories_kcal,
        protein_g,
        carbs_g,
        fat_g
      FROM public.food_items
      WHERE item_type = 'food'
        AND ($1 = '' OR LOWER(food_name) LIKE LOWER($2))
      ORDER BY food_name ASC
      LIMIT $3 OFFSET $4
      """;

    return db.query(sql, Tuple.of(q, "%" + q + "%", safeLimit, safeOffset)).map(rows -> {
      JsonArray out = new JsonArray();
      for (Row row : rows) {
        out.add(toBaseFood(row));
      }
      return out;
    });
  }

  public Future<JsonObject> createMeal(String name, String mealType, JsonArray componentsJson) {
    String foodName = (name == null ? "" : name.trim());
    if (foodName.isBlank()) {
      return Future.failedFuture("Missing required field: name");
    }

    String mt = (mealType == null ? "" : mealType.trim());
    if (mt.isBlank()) mt = "custom";

    JsonArray components = componentsJson == null ? new JsonArray() : componentsJson;
    double totalGrams = totalQuantityGrams(components);
    if (totalGrams <= 0) {
      return Future.failedFuture("Meal components must include at least one item with quantity_g > 0");
    }

    String sql = """
      INSERT INTO public.food_items (
        food_name,
        meal_type,
        portion_amount,
        portion_unit,
        calories_kcal,
        item_type,
        components_json
      )
      VALUES ($1, $2, $3, 'g', NULL, 'meal', $4::jsonb)
      RETURNING
        id,
        food_name,
        meal_type,
        item_type,
        FALSE AS is_recommended_base,
        portion_amount,
        portion_unit,
        components_json
      """;

    return db.query(sql, Tuple.of(foodName, mt, totalGrams, components.encode()))
      .map(rows -> toMeal(rows.iterator().next()));
  }

  public Future<JsonObject> getMeal(int id) {
    String sql = """
      SELECT
        id,
        food_name,
        meal_type,
        item_type,
        FALSE AS is_recommended_base,
        portion_amount,
        portion_unit,
        components_json
      FROM public.food_items
      WHERE id = $1 AND item_type = 'meal'
      """;

    return db.query(sql, Tuple.of(id)).compose(rows -> {
      if (rows == null || rows.size() == 0) {
        return Future.failedFuture("Meal not found");
      }
      return Future.succeededFuture(toMeal(rows.iterator().next()));
    });
  }

  public Future<Map<Integer, JsonObject>> fetchFoodsForNutrition(Integer[] ids) {
    if (ids == null || ids.length == 0) {
      return Future.succeededFuture(Map.of());
    }

    String sql = """
      SELECT
        id,
        food_name,
        item_type,
        calories_per_100g,
        protein,
        carbs,
        fat,
        portion_amount,
        portion_unit,
        calories_kcal,
        protein_g,
        carbs_g,
        fat_g
      FROM public.food_items
      WHERE id = ANY($1::int[])
      """;

    return db.query(sql, Tuple.of(ids)).map(rows -> {
      Map<Integer, JsonObject> map = new HashMap<>();
      for (Row row : rows) {
        Integer id = row.getInteger("id");
        if (id != null) {
          map.put(id, toNutritionRow(row));
        }
      }
      return map;
    });
  }

  private JsonObject toBaseFood(Row row) {
    JsonObject out = new JsonObject()
      .put("id", row.getInteger("id"))
      .put("name", row.getString("food_name"))
      .put("type", row.getString("item_type"))
      .put("is_recommended_base", Boolean.TRUE.equals(row.getBoolean("is_recommended_base")));

    putOrNull(out, "calories_per_100g", row.getBigDecimal("calories_per_100g"));
    putOrNull(out, "protein", row.getDouble("protein"));
    putOrNull(out, "carbs", row.getDouble("carbs"));
    putOrNull(out, "fat", row.getDouble("fat"));

    // Compatibility fields if clients still need them.
    out
      .put("portion_amount", row.getDouble("portion_amount"))
      .put("portion_unit", row.getString("portion_unit"))
      .put("calories_kcal", intOrNull(row.getValue("calories_kcal")))
      .put("protein_g", row.getDouble("protein_g"))
      .put("carbs_g", row.getDouble("carbs_g"))
      .put("fat_g", row.getDouble("fat_g"));

    return out;
  }

  private JsonObject toMeal(Row row) {
    return new JsonObject()
      .put("id", row.getInteger("id"))
      .put("name", row.getString("food_name"))
      .put("meal_type", row.getString("meal_type"))
      .put("type", row.getString("item_type"))
      .put("is_recommended_base", Boolean.TRUE.equals(row.getBoolean("is_recommended_base")))
      .put("portion_amount", row.getDouble("portion_amount"))
      .put("portion_unit", row.getString("portion_unit"))
      .put("components_json", decodeJsonValue(row.getValue("components_json")));
  }

  private JsonObject toNutritionRow(Row row) {
    return new JsonObject()
      .put("id", row.getInteger("id"))
      .put("name", row.getString("food_name"))
      .put("type", row.getString("item_type"))
      .put("calories_per_100g", row.getBigDecimal("calories_per_100g"))
      .put("protein", row.getDouble("protein"))
      .put("carbs", row.getDouble("carbs"))
      .put("fat", row.getDouble("fat"))
      .put("portion_amount", row.getDouble("portion_amount"))
      .put("portion_unit", row.getString("portion_unit"))
      .put("calories_kcal", intOrNull(row.getValue("calories_kcal")))
      .put("protein_g", row.getDouble("protein_g"))
      .put("carbs_g", row.getDouble("carbs_g"))
      .put("fat_g", row.getDouble("fat_g"));
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
    return null;
  }

  private void putOrNull(JsonObject obj, String key, BigDecimal value) {
    if (obj == null || key == null) return;
    if (value == null) obj.put(key, null);
    else obj.put(key, value.doubleValue());
  }

  private void putOrNull(JsonObject obj, String key, Double value) {
    if (obj == null || key == null) return;
    obj.put(key, value);
  }

  private Integer intOrNull(Object value) {
    if (value == null) return null;
    if (value instanceof Integer i) return i;
    if (value instanceof Long l) return l.intValue();
    if (value instanceof BigDecimal bd) return bd.intValue();
    if (value instanceof Number n) return n.intValue();
    try {
      return Integer.valueOf(value.toString());
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private double totalQuantityGrams(JsonArray components) {
    if (components == null) return 0;
    double total = 0;
    for (int i = 0; i < components.size(); i++) {
      JsonObject c = components.getJsonObject(i);
      if (c == null) continue;
      total += Math.max(0, c.getDouble("quantity_g", c.getDouble("grams", 0.0)));
    }
    return total;
  }
}
