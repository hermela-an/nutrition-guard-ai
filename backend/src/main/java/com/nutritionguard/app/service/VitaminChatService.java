package com.nutritionguard.app.service;

import com.nutritionguard.app.repository.FoodRepository;
import com.nutritionguard.app.config.AppConfig;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

public class VitaminChatService {
  private final FoodRepository foods;
  private final AppConfig config;
  private final WebClient webClient;
  private final Function<String, Future<String>> aiCaller;

  private static final int GEMINI_TIMEOUT_MS = 15_000;
  private static final String GEMINI_MODEL = "gemini-1.5-flash";
  private static final String GEMINI_ENDPOINT =
    "https://generativelanguage.googleapis.com/v1beta/models/" + GEMINI_MODEL + ":generateContent";

  private static final String SYSTEM_PROMPT = """
    NutritionGuard AI – Flexible Hybrid Prompt

    You are the NutritionGuard AI chatbot. You use a hybrid system (database + AI) to answer all user questions.

    🧠 1. Database First (Primary Source)

    When the user asks about food, nutrition, or ingredients:

    First check the database for matching information.
    If data exists:
    Use database values (calories, protein, carbs, fats, vitamins, minerals).
    Do NOT change or guess these values.
    🤖 2. AI Fallback (If database is missing or incomplete)

    If:

    the food is NOT in the database, OR
    the question is about cooking, explanation, health advice, or general knowledge

    👉 Use AI knowledge to provide a helpful, correct, and complete answer.

    🔄 3. Combined Mode (Best behavior)

    If both exist:

    Use database for factual nutrition data
    Use AI for:
    cooking instructions
    explanations
    health insights
    practical advice
    ⚠️ 4. No Blocking Rule (VERY IMPORTANT)

    Do NOT reject or block questions.

    Even if:

    data is missing
    question is general
    question is cooking-related

    👉 Always respond using AI when database is not enough.

    Never say:

    “this is only nutrition data” ❌
    “depends on database” ❌

    Instead:
    👉 always provide a helpful answer.

    🎯 Goal

    Provide:

    accurate nutrition from database when available
    intelligent AI responses when not available
    seamless experience without restrictions or missing answers
    🧠 Final Behavior Rule

    Always do this order:

    Check database
    If found → use it
    If not found or needs explanation → use AI
    Combine both when needed
    Always respond fully (no empty or blocked answers)

    Please format your answers nicely using standard HTML tags such as <b>, <br>, <ul>, <li>. Limit your response to 200 words.
    """;

  private static final String FALLBACK_IMAGE_URL =
    "https://images.unsplash.com/photo-1490645935967-10de6ba17061?auto=format&fit=crop&w=400&q=80";

  public VitaminChatService(Vertx vertx, AppConfig config, FoodRepository foods) {
    this(vertx, config, foods, null);
  }

  public VitaminChatService(
    Vertx vertx,
    AppConfig config,
    FoodRepository foods,
    Function<String, Future<String>> aiCaller
  ) {
    this.foods = foods;
    this.config = config;
    this.webClient = WebClient.create(vertx, new WebClientOptions().setSsl(true).setTrustAll(true));
    this.aiCaller = aiCaller == null ? this::callGemini : aiCaller;
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

    // 2. Vitamin & Food Search (Hybrid DB + AI mode)
    String foodQuery = extractFoodQuery(q);
    if (!foodQuery.isEmpty()) {
      return foods.search(foodQuery).compose(items -> {
        if (items != null && !items.isEmpty()) {
          JsonObject first = items.getJsonObject(0);
          JsonObject dbResponse = buildFoodResponse(first, foodQuery);
          boolean shouldAugment = shouldAugmentWithAi(body, question);
          if (!shouldAugment) {
            return Future.succeededFuture(dbResponse);
          }

          String prompt =
            "User asked: " + question + "\n\n"
              + "Database facts (do not alter numbers): " + first.encode() + "\n\n"
              + "Provide a short explanation and practical advice. Do not repeat nutrition numbers; those are already shown to the user.";

          return aiCaller.apply(prompt)
            .map(aiAnswer -> {
              String safeAi = sanitizeLimitedHtml(aiAnswer);
              String combined = dbResponse.getString("answer", "") + "<br><br><b>AI insight</b><br>" + safeAi;
              dbResponse.put("answer", combined);
              return dbResponse;
            })
            .recover(err -> Future.succeededFuture(dbResponse));
        } else {
          // Food not found in DB -> AI Fallback
          return fallbackToAI(question, buildNoRecordFoodResponse(foodQuery));
        }
      });
    }

    // 3. General Fallback -> AI mode
    return fallbackToAI(question, Future.succeededFuture(withImageAndDefinition(
      new JsonObject(),
      FALLBACK_IMAGE_URL,
      generateFallbackDefinition(question),
      "That is an interesting question! Could you try asking me about a specific food (for example: \"tell me about oats\") or hydration?"
    )).result());
  }

  private Future<JsonObject> fallbackToAI(String question, JsonObject defaultResponse) {
    if (config.geminiApiKey() == null || config.geminiApiKey().isBlank()) {
      String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
      if (isCookingQuestion(q)) {
        String cleaned = q.replaceAll("[^a-z\\s]", " ").replaceAll("\\s+", " ").trim();
        String food = extractCookingQuery(cleaned);
        if (!food.isBlank()) {
          JsonObject res = defaultResponse == null ? new JsonObject() : defaultResponse.copy();
          res.put("name", food).put("food_name", food);
          res.put("definition", generateFallbackDefinition(food));
          res.put("answer", buildCookingAnswerHtml(food));
          return Future.succeededFuture(res);
        }
      }
      return Future.succeededFuture(defaultResponse);
    }
    return aiCaller.apply("User asked: " + question).map(aiAnswer -> {
       defaultResponse.put("answer", sanitizeLimitedHtml(aiAnswer));
       if (defaultResponse.getString("definition", "").isBlank()) {
         defaultResponse.put("definition", "NutritionGuard AI Assistant");
       }
       return defaultResponse;
    }).recover(err -> {
       System.err.println("Gemini API error: " + err.getMessage());
       return Future.succeededFuture(defaultResponse);
    });
  }

  private boolean shouldAugmentWithAi(JsonObject body, String question) {
    if (config.geminiApiKey() == null || config.geminiApiKey().isBlank()) return false;
    if (explicitlyRequestsAi(body)) return true;

    String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
    if (q.isBlank()) return false;
    if (isNutritionFactsQuestion(q)) return false;

    return q.contains("explain")
      || q.contains("benefit")
      || q.contains("good for")
      || q.contains("healthy")
      || q.contains("recipe")
      || q.contains("cook")
      || q.contains("how to")
      || q.contains("should i")
      || q.contains("is it safe")
      || q.contains("side effect")
      || q.contains("allergy");
  }

  private boolean explicitlyRequestsAi(JsonObject body) {
    if (body == null) return false;
    Boolean useAi = body.getBoolean("useAI");
    if (useAi == null) useAi = body.getBoolean("useAi");
    return Boolean.TRUE.equals(useAi);
  }

  private boolean isNutritionFactsQuestion(String qLower) {
    if (qLower == null) return false;
    String q = qLower.toLowerCase(Locale.ROOT);
    return q.contains("calorie")
      || q.contains("kcal")
      || q.contains("protein")
      || q.contains("carb")
      || q.contains("fat")
      || q.contains("fiber")
      || q.contains("sugar")
      || q.contains("sodium")
      || q.contains("cholesterol")
      || q.contains("glycemic")
      || q.contains("nutrition facts")
      || q.contains("nutrition of")
      || q.contains("macros");
  }

  private boolean isCookingQuestion(String qLower) {
    if (qLower == null) return false;
    String q = qLower.toLowerCase(Locale.ROOT);
    return q.contains("how to cook")
      || q.contains("how do i cook")
      || q.contains("recipe")
      || q.contains("how to make")
      || q.contains("how do i make")
      || q.contains("cook ")
      || q.contains("cooking ")
      || q.contains("prepare ")
      || q.contains("saute")
      || q.contains("sauté")
      || q.contains("steam ")
      || q.contains("boil ")
      || q.contains("roast ")
      || q.contains("bake ")
      || q.contains("grill ")
      || q.contains("fry ");
  }

  private String extractCookingQuery(String cleanedLower) {
    if (cleanedLower == null) return "";
    String cleaned = cleanedLower.trim().toLowerCase(Locale.ROOT);
    if (cleaned.isEmpty()) return "";

    String extracted = extractAfter(cleaned, "how to cook ");
    if (extracted.isEmpty()) extracted = extractAfter(cleaned, "how do i cook ");
    if (extracted.isEmpty()) extracted = extractAfter(cleaned, "how do you cook ");
    if (extracted.isEmpty()) extracted = extractAfter(cleaned, "how to make ");
    if (extracted.isEmpty()) extracted = extractAfter(cleaned, "how do i make ");
    if (extracted.isEmpty()) extracted = extractAfter(cleaned, "recipe for ");
    if (extracted.isEmpty()) extracted = extractAfter(cleaned, "cook ");
    if (extracted.isEmpty()) extracted = extractAfter(cleaned, "prepare ");
    if (extracted.isEmpty()) extracted = extractAfter(cleaned, "steam ");
    if (extracted.isEmpty()) extracted = extractAfter(cleaned, "boil ");
    if (extracted.isEmpty()) extracted = extractAfter(cleaned, "bake ");
    if (extracted.isEmpty()) extracted = extractAfter(cleaned, "roast ");
    if (extracted.isEmpty()) extracted = extractAfter(cleaned, "grill ");
    if (extracted.isEmpty()) extracted = extractAfter(cleaned, "fry ");

    extracted = extracted.trim();
    extracted = extracted.replaceAll("^(a|an|the)\\s+", "");
    extracted = extracted.replaceAll("\\s+(please|today)$", "");

    if (extracted.length() > 60) extracted = extracted.substring(0, 60).trim();

    // Keep up to first 4 words (food names)
    String[] parts = extracted.split(" ");
    if (parts.length > 4) {
      extracted = String.join(" ", Arrays.copyOfRange(parts, 0, 4)).trim();
    }

    return extracted;
  }

  private String buildCookingAnswerHtml(String foodName) {
    String name = (foodName == null || foodName.isBlank()) ? "this food" : escapeHtml(foodName.trim());
    return "<b>How to cook " + name + "</b><br>"
      + "<ul>"
      + "<li><b>Quick sauté</b>: Heat 1–2 tsp oil, add garlic/onion (optional), add " + name + ", stir 2–4 min until just wilted/tender. Season with salt, pepper, lemon.</li>"
      + "<li><b>Steam</b>: Steam 2–5 min until tender. Drain/squeeze excess water, season and serve.</li>"
      + "<li><b>Blanch</b>: Boil 30–60 sec, then ice bath. Great for freezing or adding to sauces/soups.</li>"
      + "<li><b>Tip</b>: Don’t overcook—short cooking keeps better color and texture.</li>"
      + "</ul>";
  }

  private String sanitizeLimitedHtml(String input) {
    if (input == null) return "";
    String escaped = escapeHtml(input).replace("\r\n", "\n").replace("\n", "<br>");

    // Unescape a small whitelist of safe tags without attributes.
    escaped = escaped.replaceAll("(?i)&lt;\\s*b\\s*&gt;", "<b>");
    escaped = escaped.replaceAll("(?i)&lt;\\s*/\\s*b\\s*&gt;", "</b>");
    escaped = escaped.replaceAll("(?i)&lt;\\s*br\\s*/?\\s*&gt;", "<br>");
    escaped = escaped.replaceAll("(?i)&lt;\\s*ul\\s*&gt;", "<ul>");
    escaped = escaped.replaceAll("(?i)&lt;\\s*/\\s*ul\\s*&gt;", "</ul>");
    escaped = escaped.replaceAll("(?i)&lt;\\s*ol\\s*&gt;", "<ol>");
    escaped = escaped.replaceAll("(?i)&lt;\\s*/\\s*ol\\s*&gt;", "</ol>");
    escaped = escaped.replaceAll("(?i)&lt;\\s*li\\s*&gt;", "<li>");
    escaped = escaped.replaceAll("(?i)&lt;\\s*/\\s*li\\s*&gt;", "</li>");
    escaped = escaped.replaceAll("(?i)&lt;\\s*p\\s*&gt;", "<p>");
    escaped = escaped.replaceAll("(?i)&lt;\\s*/\\s*p\\s*&gt;", "</p>");
    escaped = escaped.replaceAll("(?i)&lt;\\s*strong\\s*&gt;", "<strong>");
    escaped = escaped.replaceAll("(?i)&lt;\\s*/\\s*strong\\s*&gt;", "</strong>");
    escaped = escaped.replaceAll("(?i)&lt;\\s*em\\s*&gt;", "<em>");
    escaped = escaped.replaceAll("(?i)&lt;\\s*/\\s*em\\s*&gt;", "</em>");
    escaped = escaped.replaceAll("(?i)&lt;\\s*i\\s*&gt;", "<i>");
    escaped = escaped.replaceAll("(?i)&lt;\\s*/\\s*i\\s*&gt;", "</i>");

    return escaped;
  }

  private Future<String> callGemini(String userPrompt) {
    String apiKey = config.geminiApiKey();
    if (apiKey == null || apiKey.isBlank()) {
      return Future.failedFuture("Missing GEMINI_API_KEY");
    }

    String prompt = userPrompt == null ? "" : userPrompt;

    JsonObject requestBody = new JsonObject()
      .put("systemInstruction", new JsonObject()
        .put("parts", new JsonArray().add(new JsonObject().put("text", SYSTEM_PROMPT)))
      )
      .put("contents", new JsonArray().add(new JsonObject()
        .put("role", "user")
        .put("parts", new JsonArray().add(new JsonObject().put("text", prompt)))
      ))
      .put("generationConfig", new JsonObject()
        .put("temperature", 0.4)
        .put("topP", 0.95)
        .put("maxOutputTokens", 350)
      );

    return webClient
      .postAbs(GEMINI_ENDPOINT)
      .timeout(GEMINI_TIMEOUT_MS)
      .putHeader("Content-Type", "application/json")
      .putHeader("x-goog-api-key", apiKey.trim())
      .sendJsonObject(requestBody)
      .compose(response -> {
        if (response.statusCode() != 401 && response.statusCode() != 403) {
          return Future.succeededFuture(response);
        }

        // Some environments expect API keys via query string rather than header.
        String encodedKey = URLEncoder.encode(apiKey.trim(), StandardCharsets.UTF_8);
        return webClient
          .postAbs(GEMINI_ENDPOINT + "?key=" + encodedKey)
          .timeout(GEMINI_TIMEOUT_MS)
          .putHeader("Content-Type", "application/json")
          .sendJsonObject(requestBody);
      })
      .compose(response -> {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
          return Future.failedFuture("Gemini HTTP Error: " + response.statusCode() + " " + safeTruncate(response.bodyAsString(), 1200));
        }

        JsonObject json;
        try {
          json = response.bodyAsJsonObject();
        } catch (Exception e) {
          return Future.failedFuture("Error parsing Gemini JSON: " + e.getMessage());
        }

        String text = extractGeminiText(json);
        if (text == null || text.isBlank()) {
          String reason = extractGeminiBlockReason(json);
          if (reason == null || reason.isBlank()) reason = "Empty response";
          return Future.failedFuture("Gemini response invalid: " + reason);
        }

        return Future.succeededFuture(text.trim());
      });
  }

  private String extractGeminiText(JsonObject json) {
    if (json == null) return null;
    JsonArray candidates = json.getJsonArray("candidates");
    if (candidates == null || candidates.isEmpty()) return null;

    for (int i = 0; i < candidates.size(); i++) {
      JsonObject candidate = candidates.getJsonObject(i);
      if (candidate == null) continue;

      JsonObject content = candidate.getJsonObject("content");
      if (content == null) continue;

      JsonArray parts = content.getJsonArray("parts");
      if (parts == null || parts.isEmpty()) continue;

      StringBuilder out = new StringBuilder();
      for (int p = 0; p < parts.size(); p++) {
        JsonObject part = parts.getJsonObject(p);
        if (part == null) continue;
        String text = part.getString("text");
        if (text != null && !text.isBlank()) {
          out.append(text);
        }
      }

      String joined = out.toString().trim();
      if (!joined.isEmpty()) return joined;
    }

    return null;
  }

  private String extractGeminiBlockReason(JsonObject json) {
    if (json == null) return null;

    JsonObject promptFeedback = json.getJsonObject("promptFeedback");
    if (promptFeedback != null) {
      String reason = promptFeedback.getString("blockReason");
      if (reason != null && !reason.isBlank()) return reason;
    }

    JsonArray candidates = json.getJsonArray("candidates");
    if (candidates != null) {
      for (int i = 0; i < candidates.size(); i++) {
        JsonObject candidate = candidates.getJsonObject(i);
        if (candidate == null) continue;
        String finishReason = candidate.getString("finishReason");
        if (finishReason != null && !finishReason.isBlank()) return finishReason;
      }
    }

    return null;
  }

  private String safeTruncate(String input, int maxChars) {
    if (input == null) return "";
    String trimmed = input.trim();
    if (trimmed.length() <= maxChars) return trimmed;
    return trimmed.substring(0, Math.max(0, maxChars)).trim() + "...";
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
      (food == null ? new JsonObject() : food.copy())
        .put("name", name)
        .put("food_name", name),
      imageUrl,
      definition,
      buildFoodAnswerHtml(food, name, definition)
    );
  }

  private String buildFoodAnswerHtml(JsonObject food, String name, String definition) {
    String safeName = escapeHtml(name);
    String safeDef = escapeHtml(definition);

    StringBuilder answer = new StringBuilder();
    answer.append("<b>").append(safeName).append("</b><br>").append(safeDef);

    if (food == null) return answer.toString();

    Double portionAmount = food.getDouble("portion_amount");
    String portionUnit = food.getString("portion_unit", "");
    Integer calories = food.getInteger("calories");
    Double protein = food.getDouble("protein_g");
    Double carbs = food.getDouble("carbs_g");
    Double fat = food.getDouble("fat_g");
    Double fiber = food.getDouble("fiber_g");
    Double sugar = food.getDouble("sugar_g");
    Double sodium = food.getDouble("sodium_mg");
    Double cholesterol = food.getDouble("cholesterol_mg");
    Integer glycemic = food.getInteger("glycemic_index");

    boolean hasAnyMacro =
      calories != null
        || protein != null
        || carbs != null
        || fat != null
        || fiber != null
        || sugar != null
        || sodium != null
        || cholesterol != null
        || glycemic != null;

    if (hasAnyMacro) {
      answer.append("<br><br><b>Nutrition</b>");
      if (portionAmount != null) {
        answer.append(" (per ").append(escapeHtml(trimDouble(portionAmount)));
        if (portionUnit != null && !portionUnit.isBlank()) {
          answer.append(" ").append(escapeHtml(portionUnit));
        }
        answer.append(")");
      }
      answer.append(":<ul>");

      if (calories != null && calories > 0) answer.append("<li>Calories: ").append(calories).append(" kcal</li>");
      if (protein != null && protein > 0) answer.append("<li>Protein: ").append(escapeHtml(trimDouble(protein))).append(" g</li>");
      if (carbs != null && carbs > 0) answer.append("<li>Carbs: ").append(escapeHtml(trimDouble(carbs))).append(" g</li>");
      if (fat != null && fat > 0) answer.append("<li>Fat: ").append(escapeHtml(trimDouble(fat))).append(" g</li>");
      if (fiber != null && fiber > 0) answer.append("<li>Fiber: ").append(escapeHtml(trimDouble(fiber))).append(" g</li>");
      if (sugar != null && sugar > 0) answer.append("<li>Sugar: ").append(escapeHtml(trimDouble(sugar))).append(" g</li>");
      if (sodium != null && sodium > 0) answer.append("<li>Sodium: ").append(escapeHtml(trimDouble(sodium))).append(" mg</li>");
      if (cholesterol != null && cholesterol > 0) answer.append("<li>Cholesterol: ").append(escapeHtml(trimDouble(cholesterol))).append(" mg</li>");
      if (glycemic != null && glycemic > 0) answer.append("<li>Glycemic index: ").append(glycemic).append("</li>");

      answer.append("</ul>");
    }

    JsonObject vitamins = food.getJsonObject("vitamins", new JsonObject());
    if (vitamins != null && !vitamins.isEmpty()) {
      List<String> keys = vitamins.fieldNames().stream().sorted().toList();
      boolean started = false;
      StringBuilder items = new StringBuilder();
      int added = 0;
      for (String key : keys) {
        if (added >= 8) break;
        Object raw = vitamins.getValue(key);
        String value = raw == null ? "" : raw.toString().trim();
        if (value.isBlank()) continue;
        if (!started) {
          started = true;
          items.append("<br><b>Vitamins</b>:<ul>");
        }
        items.append("<li>").append(escapeHtml(prettyVitaminKey(key))).append(": ").append(escapeHtml(value)).append("</li>");
        added++;
      }
      if (started) {
        items.append("</ul>");
        answer.append(items);
      }
    }

    return answer.toString();
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
      "<b>" + escapeHtml(name) + "</b><br>" + escapeHtml(definition)
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

    // Cooking queries: extract the food name so DB lookup works (DB-first).
    if (isCookingQuestion(cleaned)) {
      String cooking = extractCookingQuery(cleaned);
      if (!cooking.isEmpty()) return cooking;
    }

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
    if (extracted.isEmpty()) extracted = extractAfter(cleaned, "explain ");
    if (extracted.isEmpty()) extracted = extractAfter(cleaned, "vitamins in ");
    if (extracted.isEmpty()) extracted = extractAfter(cleaned, "benefits of ");
    if (extracted.isEmpty()) extracted = extractAfter(cleaned, "nutrition of ");
    if (extracted.isEmpty()) extracted = extractAfter(cleaned, "nutrition facts for ");
    if (extracted.isEmpty()) extracted = extractAfter(cleaned, "nutrition facts of ");
    if (extracted.isEmpty()) extracted = extractAfter(cleaned, "calories in ");
    if (extracted.isEmpty()) extracted = extractAfter(cleaned, "protein in ");
    if (extracted.isEmpty()) extracted = extractAfter(cleaned, "carbs in ");
    if (extracted.isEmpty()) extracted = extractAfter(cleaned, "fat in ");
    if (extracted.isEmpty()) extracted = extractAfter(cleaned, "macros in ");
    if (extracted.isEmpty()) extracted = extractAfter(cleaned, "how many calories in ");
    if (extracted.isEmpty()) extracted = extractAfter(cleaned, "how much protein in ");

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
        || w.equals("how")
        || w.equals("to")
        || w.equals("tell")
        || w.equals("me")
        || w.equals("about")
        || w.equals("explain")
        || w.equals("define")
        || w.equals("cook")
        || w.equals("cooking")
        || w.equals("recipe")
        || w.equals("make")
        || w.equals("prepare")
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
