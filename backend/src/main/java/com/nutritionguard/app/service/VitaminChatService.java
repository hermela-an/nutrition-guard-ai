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

  private boolean lastMessageWasNutrition = false;
  private String lastNutritionQuestion = "";

  private static final int GEMINI_TIMEOUT_MS = 15_000;
  private static final String USDA_ENDPOINT = "https://api.nal.usda.gov/fdc/v1/foods/search";

  private static final String SYSTEM_PROMPT = """
    Nutrition Expert AI

    You are a professional nutrition expert and food knowledge assistant.

    You only answer questions related to:
    * food
    * nutrition
    * calories
    * vitamins
    * minerals
    * diet
    * healthy eating
    * cooking ingredients
    * food preparation

    Rules:
    1. Provide clear, accurate, and helpful nutrition information.
    2. When nutrition data is provided, explain its health meaning.
    3. Suggest healthy food alternatives when appropriate.
    4. Keep answers concise and easy to understand.
    5. If the question is unrelated to nutrition, respond with:
       'I specialize only in nutrition and food-related questions.'

    Format your answers naturally using standard HTML tags such as <b>, <br>, <ul>, <li>. Limit your response to 200 words.

    Example format:
    <b>Nutrition Insight:</b><br>
    Explain what the nutrients mean for health.<br><br>
    <b>Optional Tip:</b><br>
    Add a short healthy eating suggestion or cooking idea.
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
    this.aiCaller = aiCaller == null ? this::callGeminiWithFallback : aiCaller;
  }

  public Future<JsonObject> ask(JsonObject body) {
    String question = body == null ? "" : body.getString("question", "");
    String vitaminKey = body == null ? null : body.getString("vitaminKey");
    Integer limit = body == null ? null : body.getInteger("limit");

    if (vitaminKey != null && !vitaminKey.isBlank()) {
      return answerFromDataset(question, vitaminKey, limit == null ? 15 : limit);
    }

    String q = question == null ? "" : question.toLowerCase();

    boolean isNutrition = isNutritionDomain(q);
    boolean isFollowUp = false;

    // 0. Domain Restriction
    if (isNutrition) {
      lastMessageWasNutrition = true;
      lastNutritionQuestion = question;
    } else if (lastMessageWasNutrition) {
      isFollowUp = true;
      lastMessageWasNutrition = false; // Reset context
    } else {
      lastMessageWasNutrition = false;
      return Future.succeededFuture(withImageAndDefinition(
        new JsonObject(),
        null,
        "NutritionGuard AI Assistant",
        "I'm a nutrition assistant and can only answer questions related to food, diet, and nutrients.\n" +
        "Please ask questions about nutrition, healthy eating, vitamins, calories, or recipes."
      ));
    }

    final String finalQuestion;
    if (isFollowUp) {
      finalQuestion = "The user previously asked about " + lastNutritionQuestion + ". The user now says: '" + question + "'. Continue the nutrition discussion and provide more helpful information.";
      return fallbackToAI(finalQuestion, Future.succeededFuture(withImageAndDefinition(
        new JsonObject(), null, "Nutrition Follow-up", "I couldn't generate an AI response. Please check your Gemini API key or try asking a more specific nutrition question."
      )).result());
    } else {
      finalQuestion = question;
    }

    // 1. Conversational logic
    if (isGreeting(q)) {
      return Future.succeededFuture(withImageAndDefinition(
        new JsonObject(),
        null,
        "NutritionGuard AI is a nutrition assistant that helps explain foods and vitamins in simple terms.",
        "Hello! I am your NutriGuard AI. I can help you find vitamin information, suggest healthy meals, or track your progress!"
      ));
    }


    // 2a. Nutrient / Vitamin query detection (before food search)
    String nutrientName = detectNutrientQuery(q);
    if (!nutrientName.isEmpty()) {
      // Step 1: try vitamins JSONB (vitamins + mapped minerals)
      String vitaminDbKey = nutrientToVitaminKey(nutrientName);
      if (!vitaminDbKey.isEmpty()) {
        return answerFromDataset(finalQuestion, vitaminDbKey, 10)
          .compose(dbResult -> {
            String dbAnswer = dbResult.getString("answer", "");
            if (!dbAnswer.isBlank() && !dbAnswer.contains("I don't have")) {
              return Future.succeededFuture(dbResult);
            }
            // Step 2: try DB column (e.g. fiber_g, sodium_mg, cholesterol_mg)
            String dbColumn = nutrientToDbColumn(nutrientName);
            if (!dbColumn.isEmpty()) {
              return answerFromDbColumn(finalQuestion, nutrientName, dbColumn, 10)
                .compose(colResult -> {
                  String colAnswer = colResult.getString("answer", "");
                  if (!colAnswer.isBlank() && !colAnswer.contains("I don't have")) {
                    return Future.succeededFuture(colResult);
                  }
                  return callUsdaForNutrient(nutrientName);
                });
            }
            return callUsdaForNutrient(nutrientName);
          });
      }
      // Step 2 (no JSONB key): try DB column before USDA
      String dbColumn = nutrientToDbColumn(nutrientName);
      if (!dbColumn.isEmpty()) {
        return answerFromDbColumn(finalQuestion, nutrientName, dbColumn, 10)
          .compose(colResult -> {
            String colAnswer = colResult.getString("answer", "");
            if (!colAnswer.isBlank() && !colAnswer.contains("I don't have")) {
              return Future.succeededFuture(colResult);
            }
            return callUsdaForNutrient(nutrientName);
          });
      }
      return callUsdaForNutrient(nutrientName);
    }

    // 2b. Vitamin & Food Search (Hybrid DB + AI mode)
    String foodQuery = extractFoodQuery(q);
    if (!foodQuery.isEmpty()) {
      return foods.search(foodQuery).compose(items -> {
        if (items != null && !items.isEmpty()) {
          JsonObject first = items.getJsonObject(0);
          JsonObject dbResponse = buildFoodResponse(first, foodQuery, true);
          boolean shouldAugment = shouldAugmentWithAi(body, finalQuestion);
          if (!shouldAugment) {
            return Future.succeededFuture(dbResponse);
          }

          String prompt =
            "User asked: " + finalQuestion + "\n\n"
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
          // USDA Fallback
          return callUsda(foodQuery).compose(usdaResponse -> {
            if (usdaResponse != null) {
              return Future.succeededFuture(usdaResponse);
            }
            // AI Fallback (Gemini)
            return fallbackToAI(finalQuestion, buildNoRecordFoodResponse(foodQuery));
          });
        }
      });
    }

    // 3. General Fallback -> AI mode
    return fallbackToAI(finalQuestion, Future.succeededFuture(withImageAndDefinition(
      new JsonObject(),
      null,
      generateFallbackDefinition(finalQuestion),
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

  private boolean isNutritionDomain(String q) {
    if (q == null || q.isBlank()) return false;
    String lower = q.toLowerCase(Locale.ROOT);
    if (isGreeting(lower)) return true;
    if (isNutritionFactsQuestion(lower)) return true;
    if (isCookingQuestion(lower)) return true;
    if (!detectNutrientQuery(lower).isEmpty()) return true;

    String[] keywords = {
      "food", "nutrition", "calorie", "protein", "carbohydrate", "carb", "fat",
      "vitamin", "mineral", "diet", "healthy", "weight", "ingredient", "recipe",
      "cook", "benefit"
    };
    for (String kw : keywords) {
      if (lower.contains(kw)) return true;
    }

    // Treat short plain queries as potential food-name lookups (e.g. "injera", "teff bread").
    // extractFoodQuery uses the same logic; returning true here lets the DB search run.
    String cleaned = lower.replaceAll("[^a-z\\s]", " ").replaceAll("\\s+", " ").trim();
    String[] words = cleaned.split("\\s+");
    if (words.length >= 1 && words.length <= 5
        && !cleaned.isEmpty()
        && !isGreeting(cleaned)
        && !containsFoodStopWords(words)) {
      return true;
    }

    return false;
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

  private Future<JsonObject> callUsdaForNutrient(String nutrientName) {
    String apiKey = config.usdaApiKey();
    if (apiKey == null || apiKey.isBlank()) {
      return Future.succeededFuture(buildNoRecordFoodResponse(nutrientName));
    }

    String url = USDA_ENDPOINT + "?api_key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
      + "&query=" + URLEncoder.encode(nutrientName, StandardCharsets.UTF_8)
      + "&pageSize=8";

    return webClient.getAbs(url)
      .timeout(6000)
      .send()
      .map(response -> {
        if (response.statusCode() != 200) return buildNoRecordFoodResponse(nutrientName);
        JsonObject json = response.bodyAsJsonObject();
        JsonArray foodList = json.getJsonArray("foods");
        if (foodList == null || foodList.isEmpty()) return buildNoRecordFoodResponse(nutrientName);

        String prettyName = Character.toUpperCase(nutrientName.charAt(0)) + nutrientName.substring(1);
        StringBuilder answer = new StringBuilder();
        answer.append("<b>Foods rich in ").append(escapeHtml(prettyName)).append("</b><br><ul>");

        int added = 0;
        for (int i = 0; i < foodList.size() && added < 8; i++) {
          JsonObject food = foodList.getJsonObject(i);
          String foodName = food.getString("description", "");
          if (foodName.isBlank()) continue;

          String nutrientValue = "";
          JsonArray nutrients = food.getJsonArray("foodNutrients", new JsonArray());
          for (int j = 0; j < nutrients.size(); j++) {
            JsonObject n = nutrients.getJsonObject(j);
            String nName = n.getString("nutrientName", "").toLowerCase();
            if (nName.contains(nutrientName.toLowerCase())) {
              Double val = n.getDouble("value");
              String unit = n.getString("unitName", "");
              if (val != null) {
                nutrientValue = " &mdash; " + String.format("%.1f", val) + " " + unit.toLowerCase();
              }
              break;
            }
          }
          answer.append("<li>").append(escapeHtml(foodName)).append(nutrientValue).append("</li>");
          added++;
        }
        answer.append("</ul>");

        String definition = prettyName + " is an essential nutrient found in many foods. Top sources are listed below.";
        return withImageAndDefinition(
          new JsonObject().put("name", prettyName).put("food_name", prettyName),
          null,
          definition,
          answer.toString()
        );
      })
      .recover(err -> {
        System.err.println("USDA nutrient search error: " + err.getMessage());
        return Future.succeededFuture(buildNoRecordFoodResponse(nutrientName));
      });
  }

  private String detectNutrientQuery(String q) {
    if (q == null || q.isBlank()) return "";
    String cleaned = q.trim().toLowerCase()
      .replaceAll("^(what is|what are|tell me about|define|explain|benefits of|foods with|foods high in|foods rich in|sources of|rich in|high in)\\s+", "")
      .replaceAll("\\s+(please|today|foods|sources)$", "")
      .trim();

    return switch (cleaned) {
      case "folate", "folic acid", "vitamin b9", "b9" -> "folate";
      case "iron" -> "iron";
      case "calcium" -> "calcium";
      case "magnesium" -> "magnesium";
      case "potassium" -> "potassium";
      case "zinc" -> "zinc";
      case "phosphorus" -> "phosphorus";
      case "vitamin a", "retinol" -> "vitamin a";
      case "vitamin b1", "thiamine", "b1" -> "thiamine";
      case "vitamin b2", "riboflavin", "b2" -> "riboflavin";
      case "vitamin b3", "niacin", "b3" -> "niacin";
      case "vitamin b6", "pyridoxine", "b6" -> "vitamin b6";
      case "vitamin b12", "cobalamin", "b12" -> "vitamin b12";
      case "vitamin c", "ascorbic acid" -> "vitamin c";
      case "vitamin d" -> "vitamin d";
      case "vitamin e", "tocopherol" -> "vitamin e";
      case "vitamin k" -> "vitamin k";
      case "omega 3", "omega-3", "omega3" -> "omega 3";
      case "fiber", "dietary fiber", "fibre" -> "fiber";
      case "sodium", "salt" -> "sodium";
      case "cholesterol" -> "cholesterol";
      default -> "";
    };
  }

  private String nutrientToVitaminKey(String nutrientName) {
    return switch (nutrientName.toLowerCase()) {
      case "vitamin a" -> "vitamin_a";
      case "vitamin c" -> "vitamin_c";
      case "vitamin d" -> "vitamin_d";
      case "vitamin e" -> "vitamin_e";
      case "vitamin k" -> "vitamin_k";
      case "thiamine" -> "vitamin_b1";
      case "riboflavin" -> "vitamin_b2";
      case "niacin" -> "vitamin_b3";
      case "vitamin b6" -> "vitamin_b6";
      case "folate" -> "vitamin_b9";
      case "vitamin b12" -> "vitamin_b12";
      // Minerals — look them up in the vitamins JSONB column first
      case "iron" -> "iron";
      case "calcium" -> "calcium";
      case "magnesium" -> "magnesium";
      case "potassium" -> "potassium";
      case "zinc" -> "zinc";
      case "phosphorus" -> "phosphorus";
      case "omega 3" -> "omega_3";
      default -> "";
    };
  }

  /**
   * Maps a nutrient name to a dedicated DB column when the nutrient
   * is stored as a first-class column rather than inside vitamins JSONB.
   */
  private String nutrientToDbColumn(String nutrientName) {
    return switch (nutrientName.toLowerCase()) {
      case "fiber", "dietary fiber", "fibre" -> "fiber_g";
      case "sodium", "salt" -> "sodium_mg";
      case "cholesterol" -> "cholesterol_mg";
      case "protein" -> "protein_g";
      default -> "";
    };
  }

  private Future<JsonObject> answerFromDbColumn(
      String question, String nutrientName, String column, int limit) {
    return foods.findFoodsRichInColumn(column, limit).map(items -> {
      String prettyName = Character.toUpperCase(nutrientName.charAt(0)) + nutrientName.substring(1);
      if (items == null || items.isEmpty()) {
        String def = prettyName + " is an essential nutrient found in many foods.";
        return withImageAndDefinition(
          new JsonObject().put("name", prettyName).put("food_name", prettyName),
          null,
          def,
          "I don't have " + escapeHtml(prettyName) + " data in the database right now."
        );
      }
      StringBuilder answer = new StringBuilder();
      answer.append("<b>Foods rich in ").append(escapeHtml(prettyName)).append("</b><br><ul>");
      for (int i = 0; i < items.size(); i++) {
        JsonObject item = items.getJsonObject(i);
        if (item == null) continue;
        String name = item.getString("food_name", "Food");
        String value = item.getString("nutrient_value", "");
        answer.append("<li>").append(escapeHtml(name));
        if (!value.isBlank()) answer.append(" &mdash; ").append(escapeHtml(value));
        answer.append("</li>");
      }
      answer.append("</ul>");
      if (question != null && !question.isBlank()) {
        answer.append("<br>Question: ").append(escapeHtml(question));
      }
      String definition = "Top foods by " + prettyName + " content, sourced from your database.";
      return withImageAndDefinition(
        new JsonObject().put("name", prettyName).put("food_name", prettyName),
        null, definition, answer.toString()
      );
    }).recover(err -> {
      System.err.println("DB column nutrient search error: " + err.getMessage());
      return Future.succeededFuture(withImageAndDefinition(
        new JsonObject(), null,
        nutrientName + " is an essential nutrient.",
        "I don't have " + escapeHtml(nutrientName) + " data right now."
      ));
    });
  }

  private Future<JsonObject> callUsda(String foodQuery) {
    String apiKey = config.usdaApiKey();

    if (apiKey == null || apiKey.isBlank()) {
      return Future.succeededFuture(null);
    }

    String url = USDA_ENDPOINT + "?api_key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
      + "&query=" + URLEncoder.encode(foodQuery, StandardCharsets.UTF_8)
      + "&pageSize=1";

    return webClient.getAbs(url)
      .timeout(5000)
      .send()
      .map(response -> {
        if (response.statusCode() != 200) return null;
        JsonObject json = response.bodyAsJsonObject();
        JsonArray foods = json.getJsonArray("foods");
        if (foods == null || foods.isEmpty()) return null;

        JsonObject food = foods.getJsonObject(0);
        return mapUsdaToInternal(food, foodQuery);
      })
      .recover(err -> {
        System.err.println("USDA API error: " + err.getMessage());
        return Future.succeededFuture(null);
      });
  }

  private JsonObject mapUsdaToInternal(JsonObject usdaFood, String query) {
    String name = usdaFood.getString("description", query);
    JsonObject food = new JsonObject().put("food_name", name);
    
    JsonArray nutrients = usdaFood.getJsonArray("foodNutrients", new JsonArray());
    for (int i = 0; i < nutrients.size(); i++) {
      JsonObject n = nutrients.getJsonObject(i);
      String nName = n.getString("nutrientName", "").toLowerCase();
      Double value = n.getDouble("value");
      if (value == null) continue;

      if (nName.contains("energy") || nName.contains("calories")) food.put("calories", value.intValue());
      else if (nName.contains("protein")) food.put("protein_g", value);
      else if (nName.contains("carbohydrate")) food.put("carbs_g", value);
      else if (nName.contains("total lipid") || nName.contains("fat")) food.put("fat_g", value);
      else if (nName.contains("fiber")) food.put("fiber_g", value);
      else if (nName.contains("sugars")) food.put("sugar_g", value);
      else if (nName.contains("sodium")) food.put("sodium_mg", value);
      else if (nName.contains("cholesterol")) food.put("cholesterol_mg", value);
    }

    return buildFoodResponse(food, name, false);
  }

  private Future<String> callGeminiWithFallback(String prompt) {
    return callGemini("gemini-1.5-flash", prompt)
      .recover(err -> {
        System.out.println("Primary model failed, switching to backup...");
        return callGemini("gemini-1.0-flash", prompt);
      })
      .recover(err -> {
        System.out.println("Backup model failed, using safe fallback...");
        return Future.succeededFuture(
          "I'm currently experiencing high demand. Please try again shortly, or ask about specific foods or nutrients."
        );
      });
  }

  private Future<String> callGemini(String model, String userPrompt) {
    String apiKey = config.geminiApiKey();
    if (apiKey == null || apiKey.isBlank()) {
      return Future.failedFuture("Missing GEMINI_API_KEY");
    }

    String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent";
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
      .postAbs(endpoint)
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
          .postAbs(endpoint + "?key=" + encodedKey)
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

  private JsonObject buildFoodResponse(JsonObject food, String foodQuery, boolean isDatabaseFood) {
    String name = food == null ? null : food.getString("food_name");
    if (name == null || name.isBlank()) name = foodQuery;

    String definition = food == null ? null : food.getString("definition");
    if (definition == null || definition.isBlank()) {
      definition = generateShortDefinition(food, name);
    }

    String imageUrl = resolveImageUrl(food, isDatabaseFood);

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

  private String resolveImageUrl(JsonObject food, boolean isDatabaseFood) {
    if (food == null) return isDatabaseFood ? FALLBACK_IMAGE_URL : null;
    String fromDb = food.getString("image_url");
    if (fromDb != null && !fromDb.isBlank()) return fromDb;
    return isDatabaseFood ? FALLBACK_IMAGE_URL : null;
  }

  private JsonObject buildNoRecordFoodResponse(String foodQuery) {
    String name = (foodQuery == null || foodQuery.isBlank()) ? "Food" : foodQuery.trim();
    String definition = generateFallbackDefinition(name);
    return withImageAndDefinition(
      new JsonObject().put("name", name).put("food_name", name),
      null,
      definition,
      "<b>" + escapeHtml(name) + "</b><br>" + escapeHtml(definition) + "<br><br>"
      + "I don't have exact nutrition facts for this item. Please ask me about its general health benefits or how to cook it!"
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
    String def = (definition == null || definition.isBlank()) ? generateFallbackDefinition("") : definition;

    JsonObject result = (base == null ? new JsonObject() : base)
      .put("definition", def)
      .put("answer", answer == null ? "" : answer);

    if (imageUrl != null && !imageUrl.isBlank()) {
      result.put("image_url", imageUrl).put("imageUrl", imageUrl);
    } else {
      result.put("image_url", "").put("imageUrl", "");
    }

    return result;
  }

  private Future<JsonObject> answerFromDataset(String question, String vitaminKeyRaw, int limit) {
    String vitaminKey = normalizeVitaminKey(vitaminKeyRaw);
    if (vitaminKey.isEmpty()) {
      return Future.succeededFuture(withImageAndDefinition(
        new JsonObject(),
        null,
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
            null,
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
      return withImageAndDefinition(new JsonObject(), null, definition, answer.toString());
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
