package com.nutritionguard.app.repository;

import com.nutritionguard.app.db.Database;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

public class FoodRepository {
  private final Database db;

  public FoodRepository(Database db) {
    this.db = db;
  }

  public Future<JsonArray> search(String query) {
    String sql = """
      SELECT id, name, meal_type, calories, protein_g, carbs_g, fat_g, sugar_g, sodium_mg, cholesterol_mg, vitamins
      FROM food_items
      WHERE LOWER(name) LIKE LOWER($1)
      ORDER BY name
      LIMIT 50
      """;
    return db.query(sql, Tuple.of("%" + query + "%"))
      .map(rows -> {
        JsonArray items = new JsonArray();
        for (Row row : rows) {
          items.add(toFood(row));
        }
        return items;
      });
  }

  public Future<JsonArray> mealCandidates(String mealType) {
    String sql = """
      SELECT id, name, meal_type, calories, protein_g, carbs_g, fat_g, sugar_g, sodium_mg, cholesterol_mg, vitamins
      FROM food_items
      WHERE meal_type = $1
      ORDER BY calories ASC
      LIMIT 30
      """;
    return db.query(sql, Tuple.of(mealType))
      .map(rows -> {
        JsonArray items = new JsonArray();
        for (Row row : rows) {
          items.add(toFood(row));
        }
        return items;
      });
  }

  private JsonObject toFood(Row row) {
    return new JsonObject()
      .put("id", row.getInteger("id"))
      .put("name", row.getString("name"))
      .put("meal_type", row.getString("meal_type"))
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
