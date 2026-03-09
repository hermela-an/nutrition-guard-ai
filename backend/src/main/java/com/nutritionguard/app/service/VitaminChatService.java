package com.nutritionguard.app.service;

import com.nutritionguard.app.repository.FoodRepository;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class VitaminChatService {
  private final FoodRepository foods;

  public VitaminChatService(FoodRepository foods) {
    this.foods = foods;
  }

  public Future<JsonObject> ask(String question) {
    String q = question == null ? "" : question.toLowerCase();
    return foods.search(extractFoodHint(q)).map(items -> {
      if (items.isEmpty()) {
        return new JsonObject()
          .put("answer", "I could not find a matching food. Try another food name.");
      }
      JsonObject first = items.getJsonObject(0);
      JsonObject vitamins = first.getJsonObject("vitamins", new JsonObject());
      return new JsonObject()
        .put("food", first.getString("name"))
        .put("vitamins", vitamins)
        .put("answer", first.getString("name") + " vitamins: " + vitamins.fieldNames());
    });
  }

  private String extractFoodHint(String q) {
    if (q.contains("spinach")) {
      return "spinach";
    }
    if (q.contains("egg")) {
      return "egg";
    }
    if (q.contains("salmon")) {
      return "salmon";
    }
    return q.replaceAll("[^a-z\\s]", "").trim();
  }
}
