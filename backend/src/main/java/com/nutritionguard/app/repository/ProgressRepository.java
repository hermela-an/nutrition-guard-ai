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
    String historySql = """
      SELECT log_date, weight_kg, consumed
      FROM daily_logs
      WHERE user_id = $1
      ORDER BY log_date ASC
      LIMIT 90
      """;
      
    String intakeSql = """
         SELECT dmi.intake_date, SUM(fi.calories_kcal)::int as calculated_calories
      FROM daily_meal_intake dmi
      JOIN food_items fi ON dmi.food_item_id = fi.id
      WHERE dmi.user_id = $1 AND dmi.eaten = true
      GROUP BY dmi.intake_date
      """;

    return db.query(historySql, Tuple.of(userId)).compose(historyRows -> 
      db.query(intakeSql, Tuple.of(userId)).map(intakeRows -> {
        java.util.Map<String, Integer> intakeMap = new java.util.HashMap<>();
        for (Row row : intakeRows) {
          intakeMap.put(row.getLocalDate("intake_date").toString(), row.getInteger("calculated_calories"));
        }

        JsonArray historyArray = new JsonArray();
        java.util.Map<String, JsonObject> mergedMap = new java.util.HashMap<>();

        for (Row row : historyRows) {
          String dateStr = row.getLocalDate("log_date").toString();
          JsonObject consumed = RowJsonUtil.getJsonObject(row, "consumed");
          if (consumed == null) consumed = new JsonObject();
          
          JsonObject item = new JsonObject()
            .put("log_date", dateStr)
            .put("weight_kg", row.getDouble("weight_kg"))
            .put("consumed", consumed);
          
          mergedMap.put(dateStr, item);
          historyArray.add(item);
        }

        for (java.util.Map.Entry<String, Integer> entry : intakeMap.entrySet()) {
          String dateStr = entry.getKey();
          Integer calcCal = entry.getValue();
          if (calcCal == null || calcCal <= 0) continue;

          if (mergedMap.containsKey(dateStr)) {
            mergedMap.get(dateStr).getJsonObject("consumed").put("calories", calcCal);
          } else {
            JsonObject consumed = new JsonObject().put("calories", calcCal);
            JsonObject item = new JsonObject()
              .put("log_date", dateStr)
              .put("weight_kg", 0.0)
              .put("consumed", consumed);
            historyArray.add(item);
          }
        }

        java.util.List<JsonObject> list = new java.util.ArrayList<>();
        for (int i = 0; i < historyArray.size(); i++) {
          list.add(historyArray.getJsonObject(i));
        }
        list.sort((a, b) -> a.getString("log_date").compareTo(b.getString("log_date")));

        return new JsonArray(list);
      })
    );
  }
}
