package com.nutritionguard.app.repository;

import com.nutritionguard.app.db.Database;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

public class ProfileRepository {
  private final Database db;

  public ProfileRepository(Database db) {
    this.db = db;
  }

  public Future<JsonObject> upsertProfile(String userId, JsonObject body) {
    String sql = """
      INSERT INTO profiles (user_id, age, gender, height_cm, weight_kg, activity_level, goal, health_metadata)
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8::jsonb)
      ON CONFLICT (user_id) DO UPDATE
      SET age = EXCLUDED.age,
          gender = EXCLUDED.gender,
          height_cm = EXCLUDED.height_cm,
          weight_kg = EXCLUDED.weight_kg,
          activity_level = EXCLUDED.activity_level,
          goal = EXCLUDED.goal,
          health_metadata = EXCLUDED.health_metadata,
          updated_at = now()
      RETURNING user_id, age, gender, height_cm, weight_kg, activity_level, goal, health_metadata
      """;
    return db.query(sql, Tuple.of(
      userId,
      asInt(body, "age"),
      body.getString("gender"),
      asDouble(body, "height_cm"),
      asDouble(body, "weight_kg"),
      body.getString("activity_level"),
      body.getString("goal"),
      body.getJsonObject("health_metadata", new JsonObject())
    )).map(rows -> map(rows.iterator().next()));
  }

    public Future<JsonObject> getProfile(String userId) {
    // 👇 THIS IS THE EXACT SQL QUERY YOU NEED 👇
    // Notice 'u.full_name AS name' instead of 'u.name'
    String sql = """
      SELECT p.user_id, p.age, p.gender, p.height_cm, p.weight_kg, p.activity_level, p.goal, p.health_metadata, u.full_name AS name, u.email 
      FROM profiles p 
      JOIN users u ON p.user_id = u.id 
      WHERE p.user_id = $1
      """;
      
    return db.query(sql, Tuple.of(userId))
      .map(rows -> rows.iterator().hasNext() ? map(rows.iterator().next()) : null);
  }

  private JsonObject map(Row row) {
    Object rawHealth = row.getValue("health_metadata");
    JsonObject healthMetadata;
    if (rawHealth instanceof JsonObject health) {
      healthMetadata = health;
    } else if (rawHealth instanceof String text && !text.isBlank()) {
      healthMetadata = new JsonObject(text);
    } else {
      healthMetadata = new JsonObject();
    }

    return new JsonObject()
      .put("user_id", row.getString("user_id"))
      .put("age", row.getInteger("age"))
      .put("gender", row.getString("gender"))
      .put("height_cm", row.getDouble("height_cm"))
      .put("weight_kg", row.getDouble("weight_kg"))
      .put("activity_level", row.getString("activity_level"))
      .put("goal", row.getString("goal"))
      .put("health_metadata", healthMetadata)
      .put("name", row.getString("name"))
      .put("email", row.getString("email"));
  }

  private Integer asInt(JsonObject obj, String key) {
    Object value = obj.getValue(key);
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value instanceof String text && !text.isBlank()) {
      return Integer.parseInt(text);
    }
    return null;
  }

  private Double asDouble(JsonObject obj, String key) {
    Object value = obj.getValue(key);
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    if (value instanceof String text && !text.isBlank()) {
      return Double.parseDouble(text);
    }
    return null;
  }
}
