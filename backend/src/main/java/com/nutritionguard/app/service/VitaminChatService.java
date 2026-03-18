package com.nutritionguard.app.service;

import com.nutritionguard.app.repository.FoodRepository;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import java.util.Map;
import java.util.HashMap;

public class VitaminChatService {
  private final FoodRepository foods;
  
  // Static image sources to enrich chat results
  private static final Map<String, String> FOOD_IMAGES = new HashMap<>();
  static {
    FOOD_IMAGES.put("spinach", "https://images.unsplash.com/photo-1576045057995-568f588f82fb?w=400&q=80");
    FOOD_IMAGES.put("egg", "https://images.unsplash.com/photo-1582722872445-44dc5f7e3c8f?w=400&q=80");
    FOOD_IMAGES.put("salmon", "https://images.unsplash.com/photo-1519708227418-c8fd9a32b7a2?w=400&q=80");
    FOOD_IMAGES.put("chicken", "https://images.unsplash.com/photo-1604908176997-125f25cc6f3d?w=400&q=80");
    FOOD_IMAGES.put("water", "https://images.unsplash.com/photo-1523362628745-0c100150b504?w=400&q=80");
  }

  public VitaminChatService(FoodRepository foods) {
    this.foods = foods;
  }

  public Future<JsonObject> ask(String question) {
    String q = question == null ? "" : question.toLowerCase();

    // 1. Conversational logic
    if (q.contains("hello") || q.contains("hi") || q.equals("hey")) {
      return Future.succeededFuture(new JsonObject().put("answer", "Hello! I am your NutriGuard AI. I can help you find vitamin information, suggest healthy meals, or track your progress!"));
    }
    if (q.contains("water") || q.contains("hydration")) {
      return Future.succeededFuture(new JsonObject()
          .put("answer", "Staying hydrated is crucial! The general recommendation is to drink about 2.5 to 3.5 liters of water per day, depending on your activity level.")
          .put("imageUrl", FOOD_IMAGES.get("water")));
    }
    if (q.contains("snack") || q.contains("late night")) {
      return Future.succeededFuture(new JsonObject().put("answer", "For a healthy late-night snack, try reaching for something light, like a handful of almonds, Greek yogurt, or some apple slices."));
    }
    if (q.contains("weight") || q.contains("lose")) {
      return Future.succeededFuture(new JsonObject().put("answer", "For sustainable weight management, focus on a high-protein, fiber-rich diet with an appropriate caloric balance. I can suggest healthy proteins if you want!"));
    }

    // 2. Vitamin & Food Search
    String foodHint = extractFoodHint(q);
    if (!foodHint.isEmpty()) {
       return foods.search(foodHint).map(items -> {
          if (items.isEmpty()) {
             return new JsonObject()
               .put("answer", "That's a great question! While I don't have the exact vitamin breakdown for that specific food directly available, eating a variety of whole foods is always a great strategy.");
          }
          JsonObject first = items.getJsonObject(0);
          JsonObject vitamins = first.getJsonObject("vitamins", new JsonObject());
          String name = first.getString("food_name");
          
          StringBuilder answer = new StringBuilder();
          answer.append("Yes! **").append(name).append("** is a great, nutrient-dense choice. ");
          
          if (!vitamins.isEmpty()) {
             answer.append("It provides excellent amounts of: ").append(String.join(", ", vitamins.fieldNames())).append(".");
          } else {
             answer.append("It has a wonderful overall macro profile.");
          }

          JsonObject response = new JsonObject().put("answer", answer.toString());
          
          // Attach an image if available or a fallback generic food image
          String img = FOOD_IMAGES.get(foodHint);
          if (img != null) {
              response.put("imageUrl", img);
          } else {
              response.put("imageUrl", "https://images.unsplash.com/photo-1490645935967-10de6ba17061?w=400&q=80");
          }
          
          return response;
       });
    }

    // 3. General Fallback
    return Future.succeededFuture(new JsonObject().put("answer", "That is an interesting question! As an AI, I am constantly learning taking in new nutritional data. Could you try asking me about specific foods or hydration?"));
  }

  private String extractFoodHint(String q) {
    if (q.contains("spinach") || q.contains("iron")) return "spinach";
    if (q.contains("egg")) return "egg";
    if (q.contains("salmon") || q.contains("fish")) return "salmon";
    if (q.contains("chicken")) return "chicken";
    
    // Rudimentary fallback keyword extractor
    String cleaned = q.replaceAll("[^a-z\\s]", "").trim();
    if (cleaned.length() > 0 && cleaned.split("\\s").length == 1) {
        return cleaned;
    }
    return "";
  }
}
