package com.nutritionguard.app.service;

import com.nutritionguard.app.repository.FoodRepository;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import java.util.Arrays;
import java.util.List;

public class VitaminChatService {
  private final FoodRepository foods;

  private static final String FALLBACK_IMAGE_URL =
    "https://images.unsplash.com/photo-1490645935967-10de6ba17061?auto=format&fit=crop&w=400&q=80";

  public VitaminChatService(FoodRepository foods) {
    this.foods = foods;
  }

  public Future<JsonObject> ask(JsonObject body) {
    String question = body == null ? "" : body.getString("question", "");
    String vitaminKey = body == null ? null : body.getString("vitaminKey");
    Integer limit = body == null ? null : body.getInteger("limit");

    if (vitaminKey != null && !vitaminKey.isBlank()) {
      return answerFromDataset(question, vitaminKey, limit == null ? 15 : limit);
    }

    String q = question == null ? "" : question.toLowerCase();

    // 1. Conversational logic
    if (isGreeting(q)) {
      return Future.succeededFuture(withImageAndDefinition(
        new JsonObject(),
        FALLBACK_IMAGE_URL,
        "NutritionGuard AI is a nutrition assistant that helps explain foods and vitamins in simple terms.",
        "Hello! I am your NutriGuard AI. I can help you find vitamin information, suggest healthy meals, or track your progress!"
      ));
    }
    if (q.contains("water") || q.contains("hydration")) {
      return Future.succeededFuture(withImageAndDefinition(
        new JsonObject(),
        FALLBACK_IMAGE_URL,
        "Hydration is maintaining enough body water for normal function; needs vary by activity and climate.",
        "Staying hydrated is crucial! A common guideline is about 2.5-3.5 liters of total fluids per day, adjusted for activity and climate."
      ));
    }
    if (q.contains("snack") || q.contains("late night")) {
      return Future.succeededFuture(withImageAndDefinition(
        new JsonObject(),
        FALLBACK_IMAGE_URL,
        "A snack is a small meal between main meals; choosing protein and fiber can improve satiety.",
        "For a healthy late-night snack, try something light, like a handful of almonds, Greek yogurt, or apple slices."
      ));
    }
    if (q.contains("weight") || q.contains("lose")) {
      return Future.succeededFuture(withImageAndDefinition(
        new JsonObject(),
        FALLBACK_IMAGE_URL,
        "Weight management means balancing energy intake and activity over time while meeting nutrient needs.",
        "For sustainable weight management, focus on a high-protein, fiber-rich diet with an appropriate calorie balance. I can suggest healthy proteins if you want!"
      ));
    }

    // 2. Vitamin & Food Search
    String foodQuery = extractFoodQuery(q);
    if (!foodQuery.isEmpty()) {
      return foods.search(foodQuery).map(items -> {
        if (items == null || items.isEmpty()) return buildNoRecordFoodResponse(foodQuery);

        JsonObject first = items.getJsonObject(0);
        return buildFoodResponse(first, foodQuery);
      });
    }

    // 3. General Fallback
    String definition = generateFallbackDefinition(question);
    return Future.succeededFuture(withImageAndDefinition(
      new JsonObject(),
      FALLBACK_IMAGE_URL,
      definition,
      "That is an interesting question! Could you try asking me about a specific food (for example: \"tell me about oats\") or hydration?"
    ));
  }

  private boolean isGreeting(String q) {
    if (q == null) return false;

    String cleaned = q.replaceAll("[^a-z\\s]", " ").trim();
    if (cleaned.isEmpty()) return false;

    return Arrays.stream(cleaned.split("\\s+"))
      .filter(w -> !w.isBlank())
      .anyMatch(w -> w.equals("hi") || w.equals("hello") || w.equals("hey"));
  }

  private JsonObject buildFoodResponse(JsonObject food, String foodQuery) {
    String name = food == null ? null : food.getString("food_name");
    if (name == null || name.isBlank()) name = foodQuery;

    String definition = food == null ? null : food.getString("definition");
    if (definition == null || definition.isBlank()) {
      definition = generateShortDefinition(food, name);
    }

    String imageUrl = resolveImageUrl(food);

    return withImageAndDefinition(
      new JsonObject()
        .put("name", name)
        .put("food_name", name),
      imageUrl,
      definition,
      "**" + name + "**: " + definition
    );
  }

  private String generateShortDefinition(JsonObject food, String name) {
    if (food == null) {
      return "A food item. Nutrition can vary by portion and preparation.";
    }

    String mealType = food.getString("meal_type", "meal");
    Double portionAmount = food.getDouble("portion_amount");
    String portionUnit = food.getString("portion_unit", "");
    Integer calories = food.getInteger("calories");
    Double protein = food.getDouble("protein_g");
    Double carbs = food.getDouble("carbs_g");
    Double fat = food.getDouble("fat_g");

    StringBuilder s = new StringBuilder();
    s.append(name).append(" is a nutrient-rich ").append(mealType).append(" option.");

    if (portionAmount != null && calories != null) {
      s.append(" Per ").append(trimDouble(portionAmount)).append(portionUnit.isBlank() ? "" : " " + portionUnit).append(": ")
        .append(calories).append(" kcal");

      if (protein != null || carbs != null || fat != null) {
        s.append(" (");
        s.append("protein ").append(trimDouble(protein)).append("g, ");
        s.append("carbs ").append(trimDouble(carbs)).append("g, ");
        s.append("fat ").append(trimDouble(fat)).append("g");
        s.append(")");
      }
      s.append(".");
    }

    JsonObject vitamins = food.getJsonObject("vitamins", new JsonObject());
    if (vitamins != null && !vitamins.isEmpty()) {
      List<String> keys = vitamins.fieldNames().stream().sorted().limit(3).toList();
      if (!keys.isEmpty()) {
        List<String> pretty = keys.stream().map(this::prettyVitaminKey).toList();
        s.append(" Notable micronutrients: ").append(String.join(", ", pretty)).append(".");
      }
    }

    return s.toString();
  }

  private String trimDouble(Double value) {
    if (value == null) return "0";
    if (Math.abs(value - Math.rint(value)) < 0.000001) {
      return String.valueOf((long) Math.rint(value));
    }
    return String.format(java.util.Locale.US, "%.1f", value);
  }

  private String resolveImageUrl(JsonObject food) {
    if (food == null) return FALLBACK_IMAGE_URL;
    String fromDb = food.getString("image_url");
    if (fromDb != null && !fromDb.isBlank()) return fromDb;
    return FALLBACK_IMAGE_URL;
  }

  private JsonObject buildNoRecordFoodResponse(String foodQuery) {
    String name = (foodQuery == null || foodQuery.isBlank()) ? "Food" : foodQuery.trim();
    String definition = generateFallbackDefinition(name);
    return withImageAndDefinition(
      new JsonObject().put("name", name).put("food_name", name),
      FALLBACK_IMAGE_URL,
      definition,
      "**" + name + "**: " + definition
    );
  }

  private String generateFallbackDefinition(String query) {
    String cleaned = query == null ? "" : query.trim();
    if (cleaned.isEmpty()) {
      return "A nutrition topic. Nutrition varies by portion, preparation, and individual needs.";
    }

    if (cleaned.length() > 60) cleaned = cleaned.substring(0, 60).trim();
    return cleaned + " is a food or nutrition topic. Nutrition depends on portion and preparation.";
  }

  private JsonObject withImageAndDefinition(JsonObject base, String imageUrl, String definition, String answer) {
    String img = (imageUrl == null || imageUrl.isBlank()) ? FALLBACK_IMAGE_URL : imageUrl;
    String def = (definition == null || definition.isBlank()) ? generateFallbackDefinition("") : definition;

    return (base == null ? new JsonObject() : base)
      .put("definition", def)
      .put("image_url", img)
      .put("imageUrl", img)
      .put("answer", answer == null ? "" : answer);
  }

  private Future<JsonObject> answerFromDataset(String question, String vitaminKeyRaw, int limit) {
    String vitaminKey = normalizeVitaminKey(vitaminKeyRaw);
    if (vitaminKey.isEmpty()) {
      return Future.succeededFuture(withImageAndDefinition(
        new JsonObject(),
        FALLBACK_IMAGE_URL,
        "A vitamin is an essential micronutrient needed in small amounts to support normal body functions.",
        "I couldn't recognize that vitamin key. Please try selecting a vitamin again."
      ));
    }

    String title = prettyVitaminName(vitaminKey);
    int safeLimit = Math.max(1, Math.min(limit, 50));

      return foods.vitaminLeaderboard(vitaminKey, safeLimit).map(items -> {
        if (items == null || items.isEmpty()) {
          String definition = title + " is an essential micronutrient. Food sources depend on diet patterns and preparation.";
          return withImageAndDefinition(
            new JsonObject(),
            FALLBACK_IMAGE_URL,
            definition,
            "I don't have a list of foods for **" + title + "** right now."
          );
        }

      StringBuilder answer = new StringBuilder();
      answer
        .append("Here are foods that contain **")
        .append(title)
        .append("** (top ")
        .append(items.size())
        .append("):<br>");

      for (int i = 0; i < items.size(); i++) {
        JsonObject item = items.getJsonObject(i);
        if (item == null) continue;
        String name = item.getString("food_name", "Food");
        String value = item.getString("vitamin_value", "");
        if (value == null || value.isBlank()) value = "present";
        answer
          .append(i + 1)
          .append(") ")
          .append(name)
          .append(" - ")
          .append(value)
          .append("<br>");
      }

      if (question != null && !question.isBlank()) {
        answer.append("<br>Question: ").append(escapeHtml(question));
      }

      String definition = "A short list of foods that contain " + title + ".";
      return withImageAndDefinition(new JsonObject(), FALLBACK_IMAGE_URL, definition, answer.toString());
    });
  }

  private String normalizeVitaminKey(String vitaminKey) {
    if (vitaminKey == null) return "";
    String trimmed = vitaminKey.trim().toLowerCase();
    if (!trimmed.matches("^[a-z0-9_]+$")) return "";
    if (!trimmed.startsWith("vitamin_")) return "";
    return trimmed;
  }

  private String prettyVitaminName(String vitaminKey) {
    return switch (vitaminKey) {
      case "vitamin_a" -> "Vitamin A";
      case "vitamin_d" -> "Vitamin D";
      case "vitamin_e" -> "Vitamin E";
      case "vitamin_k" -> "Vitamin K";
      case "vitamin_b1" -> "Vitamin B1 (Thiamine)";
      case "vitamin_b2" -> "Vitamin B2 (Riboflavin)";
      case "vitamin_b3" -> "Vitamin B3 (Niacin)";
      case "vitamin_b5" -> "Vitamin B5 (Pantothenic Acid)";
      case "vitamin_b6" -> "Vitamin B6 (Pyridoxine)";
      case "vitamin_b7" -> "Vitamin B7 (Biotin)";
      case "vitamin_b9" -> "Vitamin B9 (Folate / Folic Acid)";
      case "vitamin_b12" -> "Vitamin B12 (Cobalamin)";
      case "vitamin_c" -> "Vitamin C (Ascorbic Acid)";
      default -> vitaminKey.replace('_', ' ');
    };
  }

  private String prettyVitaminKey(String rawKey) {
    if (rawKey == null) return "";
    String normalized = normalizeVitaminKey(rawKey);
    if (!normalized.isEmpty()) return prettyVitaminName(normalized);
    return rawKey.replace('_', ' ');
  }

  private String escapeHtml(String input) {
    if (input == null) return "";
    return input
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&#39;");
  }

  private String extractFoodQuery(String q) {
    if (q == null) return "";

    String cleaned = q.toLowerCase().replaceAll("[^a-z\\s]", " ").replaceAll("\\s+", " ").trim();
    if (cleaned.isEmpty()) return "";

    // If user just typed a food name (1-4 words), treat it as a direct query.
    String[] words = cleaned.split(" ");
    if (words.length >= 1 && words.length <= 4 && !isGreeting(cleaned) && !containsFoodStopWords(words)) {
      return cleaned;
    }

    // Common question patterns: extract the phrase after the marker.
    String extracted = extractAfter(cleaned, "tell me about ");
    if (extracted.isEmpty()) extracted = extractAfter(cleaned, "what is ");
    if (extracted.isEmpty()) extracted = extractAfter(cleaned, "what are ");
    if (extracted.isEmpty()) extracted = extractAfter(cleaned, "define ");
    if (extracted.isEmpty()) extracted = extractAfter(cleaned, "vitamins in ");
    if (extracted.isEmpty()) extracted = extractAfter(cleaned, "benefits of ");
    if (extracted.isEmpty()) extracted = extractAfter(cleaned, "nutrition of ");

    extracted = extracted.trim();
    extracted = extracted.replaceAll("^(a|an|the)\\s+", "");
    extracted = extracted.replaceAll("\\s+(please|today)$", "");

    // Trim to a reasonable query length (food names can be multi-word, but not a paragraph).
    if (extracted.length() > 60) extracted = extracted.substring(0, 60).trim();

    // Keep up to first 6 words to avoid searching the whole sentence.
    String[] parts = extracted.split(" ");
    if (parts.length > 6) {
      extracted = String.join(" ", Arrays.copyOfRange(parts, 0, 6)).trim();
    }

    // Final fallback: try the last 1-3 words (often the food name).
    if (extracted.isEmpty() && words.length >= 1) {
      extracted = lastNonStopWordPhrase(words, 3);
    }

    return extracted;
  }

  private boolean containsFoodStopWords(String[] words) {
    for (String w : words) {
      if (w == null) continue;
      if (w.equals("what")
        || w.equals("is")
        || w.equals("are")
        || w.equals("this")
        || w.equals("that")
        || w.equals("tell")
        || w.equals("me")
        || w.equals("about")
        || w.equals("define")
        || w.equals("vitamins")
        || w.equals("vitamin")
        || w.equals("in")
        || w.equals("benefits")
        || w.equals("of")
        || w.equals("nutrition")
        || w.equals("healthy")
        || w.equals("health")
        || w.equals("good")
        || w.equals("bad")
        || w.equals("calories")
        || w.equals("calorie")
        || w.equals("kcal")
        || w.equals("protein")
        || w.equals("carbs")
        || w.equals("fat")
        || w.equals("for")
        || w.equals("please")) {
        return true;
      }
    }
    return false;
  }

  private String lastNonStopWordPhrase(String[] words, int maxWords) {
    if (words == null || words.length == 0) return "";

    java.util.ArrayList<String> picked = new java.util.ArrayList<>();
    for (int i = words.length - 1; i >= 0 && picked.size() < maxWords; i--) {
      String w = words[i];
      if (w == null || w.isBlank()) continue;
      if (containsFoodStopWords(new String[] { w })) continue;
      picked.add(0, w);
    }
    return String.join(" ", picked).trim();
  }

  private String extractAfter(String haystack, String needle) {
    int idx = haystack.indexOf(needle);
    if (idx < 0) return "";
    return haystack.substring(idx + needle.length());
  }
}
