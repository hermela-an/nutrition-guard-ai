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

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import com.nutritionguard.app.service.routing.IntentHandler;
import com.nutritionguard.app.service.routing.NutrientLookupHandler;
import com.nutritionguard.app.service.routing.FoodBenefitHandler;
import com.nutritionguard.app.service.routing.FoodComparisonHandler;
import com.nutritionguard.app.service.routing.SymptomAnalysisHandler;
import com.nutritionguard.app.service.routing.MealRecommendationHandler;
import com.nutritionguard.app.service.routing.DietPlanningHandler;
import com.nutritionguard.app.service.routing.VitaminLookupHandler;
import com.nutritionguard.app.service.routing.GeneralNutritionChatHandler;
import java.util.function.Function;
import java.util.Set;

/**
 * Service to handle user conversational requests in a health and nutrition application.
 * Uses a Semantic NLU classifier powered by Gemini to route questions to the correct
 * destination (Local DB, USDA FoodData Central, Conversational Chat, or Greetings).
 */
public class VitaminChatService {

  private final FoodRepository foods;
  private final AppConfig config;
  private final WebClient webClient;
  private final Function<String, Future<String>> aiCaller;
  private final RagService ragService;

  private static final int GEMINI_TIMEOUT_MS = 15_000;
private static final String USDA_ENDPOINT = "https://api.nal.usda.gov/fdc/v1/foods/search";
private static final Set<String> VALID_INTENTS = Set.of(
    "NUTRIENT_LOOKUP",
    "FOOD_BENEFIT",
    "FOOD_COMPARISON",
    "SYMPTOM_ANALYSIS",
    "MEAL_RECOMMENDATION",
    "DIET_PLANNING",
    "VITAMIN_LOOKUP",
    "GENERAL_NUTRITION_CHAT",
    "GREETING"
);
  private final Map<String, IntentHandler> intentHandlerMap;


  // System prompt for general conversational tasks and synthesis
  private static final String SYSTEM_PROMPT = """
    Nutrition Expert AI

    You are a professional nutrition expert and food knowledge assistant.

    When context data is provided (nutrition facts, food information, or document excerpts), use it as the primary source of truth to answer the user's question. Explain the health meaning of the data and provide practical advice.

    When no context is provided, answer from your own knowledge about food, nutrition, vitamins, minerals, diet, healthy eating, cooking ingredients, and food preparation.

    Rules:
    1. Provide clear, accurate, and helpful nutrition information.
    2. Explain the health significance of nutrition data.
    3. Suggest healthy alternatives when appropriate.
    4. Keep answers concise and easy to understand.
    5. If the question is completely unrelated to nutrition, respond with:
       'I specialize only in nutrition and food-related questions.'

    Format your answers naturally using standard HTML tags such as <b>, <br>, <ul>, <li>. Limit your response to 200 words.
    """;

  // System prompt explicitly instructing Gemini to act as a structured NLU Classifier
  private static final String NLU_ROUTING_PROMPT = """
You are an expert routing, intent classification, and entity extraction assistant for a health and nutrition application.
Your job is to analyze the user's message, determine the appropriate destination, infer the user's intent, and extract any relevant entities.

Destinations (choose exactly one):
- "GREETING"
- "LOCAL_DB"
- "USDA_SEARCH"
- "CHAT"

Intents (choose exactly one):
- "NUTRIENT_LOOKUP"
- "FOOD_BENEFIT"
- "FOOD_COMPARISON"
- "SYMPTOM_ANALYSIS"
- "MEAL_RECOMMENDATION"
- "DIET_PLANNING"
- "VITAMIN_LOOKUP"
- "GENERAL_NUTRITION_CHAT"
- "GREETING"

Entities to extract (set to null if not applicable):
- "food_item"
- "nutrient"
- "symptom"
- "goal"
- "meal_type"

Extract the clean food item name as "food_item". Return a raw, minified JSON object with the following keys:
{
  "destination": "<one of the destinations>",
  "intent": "<one of the intents>",
  "food_item": "<extracted food item or null>",
  "nutrient": "<extracted nutrient or null>",
  "symptom": "<extracted symptom or null>",
  "goal": "<extracted goal or null>",
  "meal_type": "<extracted meal type or null>"
}
""";

  private static final String FALLBACK_IMAGE_URL =
    "https://images.unsplash.com/photo-1490645935967-10de6ba17061?auto=format&fit=crop&w=400&q=80";

  public VitaminChatService(Vertx vertx, AppConfig config, FoodRepository foods, RagService ragService) {
    this(vertx, config, foods, ragService, null);
  }

  public VitaminChatService(
    Vertx vertx,
    AppConfig config,
    FoodRepository foods,
    RagService ragService,
    Function<String, Future<String>> aiCaller
  ) {
    this.foods = foods;
    this.config = config;
    this.webClient = WebClient.create(vertx, new WebClientOptions().setSsl(true).setTrustAll(true));
    this.ragService = ragService;
    this.aiCaller = aiCaller == null ? this::callGeminiWithFallback : aiCaller;
    // Initialize intent handlers registry
    this.intentHandlerMap = new HashMap<>();
    this.intentHandlerMap.put("NUTRIENT_LOOKUP", new NutrientLookupHandler(this));
    this.intentHandlerMap.put("FOOD_BENEFIT", new FoodBenefitHandler(this));
    this.intentHandlerMap.put("FOOD_COMPARISON", new FoodComparisonHandler(this));
    this.intentHandlerMap.put("SYMPTOM_ANALYSIS", new SymptomAnalysisHandler(this));
    this.intentHandlerMap.put("MEAL_RECOMMENDATION", new MealRecommendationHandler(this));
    this.intentHandlerMap.put("DIET_PLANNING", new DietPlanningHandler(this));
    this.intentHandlerMap.put("VITAMIN_LOOKUP", new VitaminLookupHandler(this));
    this.intentHandlerMap.put("GENERAL_NUTRITION_CHAT", new GeneralNutritionChatHandler(this));
    this.intentHandlerMap.put("GREETING", (q, f, d, r) -> Future.succeededFuture(withImageAndDefinition(
        new JsonObject(), null,
        "NutritionGuard AI is a nutrition assistant that helps explain foods and vitamins in simple terms.",
        "Hello! I am your NutriGuard AI. I can help you find vitamin information, suggest healthy meals, or track your progress!"
    )));
  }

  /**
   * Main entrypoint for chat queries.
   */
  public Future<JsonObject> ask(JsonObject body) {
    String question = body == null ? "" : body.getString("question", "");
    String vitaminKey = body == null ? null : body.getString("vitaminKey");
    Integer limit = body == null ? null : body.getInteger("limit");

    // Vitamin quick-chip queries (direct dataset lookup)
    if (vitaminKey != null && !vitaminKey.isBlank()) {
      return answerFromDataset(question, vitaminKey, limit == null ? 15 : limit);
    }

    if (question == null || question.isBlank()) {
      return Future.succeededFuture(withImageAndDefinition(
        new JsonObject(), null,
        "NutritionGuard AI Assistant",
        "Please ask a nutrition-related question!"
      ));
    }

    // ===========================================================================
    // PRE-NLU SAFETY CHECKS: Handle clear patterns before calling Gemini
    // ===========================================================================

    if (isGreeting(question)) {
      return Future.succeededFuture(withImageAndDefinition(
        new JsonObject(), null,
        "NutritionGuard AI is a nutrition assistant that helps explain foods and vitamins in simple terms.",
        "Hello! I am your NutriGuard AI. I can help you find vitamin information, suggest healthy meals, or track your progress!"
      ));
    }

    if (isClearChatQuery(question)) {
      return runDirectAiPipeline(question);
    }

    // ===========================================================================
    // 🧠 SEMANTIC NLU ROUTER: Handing routing classifications to Gemini via API
    // ===========================================================================
    return classifyIntent(question)
      .compose(routingDecision -> {
        String destination = routingDecision.getString("destination", "CHAT");
        String intent = routingDecision.getString("intent", "GENERAL_NUTRITION_CHAT");
        String cleanFoodItem = routingDecision.getString("food_item");
        // Fallback for older key name
        if (cleanFoodItem == null) {
          cleanFoodItem = routingDecision.getString("clean_food_item");
        }
        // Validate intent
        if (!isValidIntent(intent)) {
          System.err.println("NLU returned invalid intent '" + intent + "' for: " + question);
          intent = "GENERAL_NUTRITION_CHAT";
        }

        // Post-NLU validation: reject obviously wrong food extractions
        if ("LOCAL_DB".equals(destination) || "USDA_SEARCH".equals(destination)) {
          if (!isValidFoodExtraction(cleanFoodItem, question)) {
            System.err.println("NLU returned invalid food item '" + cleanFoodItem + "' for: " + question);
            return legacyKeywordPipeline(question);
          }
        }

        // Greeting handling based on intent or destination
        if ("GREETING".equals(intent) || "GREETING".equals(destination)) {
          return Future.succeededFuture(withImageAndDefinition(
            new JsonObject(),
            null,
            "NutritionGuard AI is a nutrition assistant that helps explain foods and vitamins in simple terms.",
            "Hello! I am your NutriGuard AI. I can help you find vitamin information, suggest healthy meals, or track your progress!"
          ));
        }

        // RAG destination handling: route to RAG service
        if ("RAG".equalsIgnoreCase(destination)) {
          return queryRagAndSynthesize(question);
        }
        // Existing handler lookup
        IntentHandler handler = intentHandlerMap.get(intent);
        if (handler != null) {
          // Run the handler first
          return handler.handle(question, cleanFoodItem, destination, routingDecision)
              .compose(res -> {
                // If the handler didn't produce a useful answer, fall back to RAG
                if (res == null || !res.containsKey("answer") ||
                    res.getString("answer") == null || res.getString("answer").isBlank()) {
                  return queryRagAndSynthesize(question);
                }
                return Future.succeededFuture(res);
              });
        }
        // No handler found – still try RAG as the last resort
        return queryRagAndSynthesize(question);
      })
      .recover(err -> {
        String msg = err.getMessage();
        if (msg != null && (msg.contains("429") || msg.contains("503"))) {
          System.err.println("Gemini NLU rate-limited/busy, using legacy keyword pipeline: " + msg);
          return legacyKeywordPipeline(question);
        }
        System.err.println("NLU routing failed, using direct AI fallback: " + msg);
        return queryRagAndSynthesize(question);
      });
  }

  /**
   * Dispatches system instructions to Gemini and parses the response schema into a JSON Object.
   */
  private Future<JsonObject> classifyIntent(String userQuestion) {
    String apiKey = config.geminiApiKey();
    if (apiKey == null || apiKey.isBlank()) {
      return Future.failedFuture("Missing GEMINI_API_KEY for routing classification.");
    }

    String encodedKey = URLEncoder.encode(apiKey.trim(), StandardCharsets.UTF_8);
    String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + encodedKey;

    JsonObject requestBody = new JsonObject()
      .put("systemInstruction", new JsonObject()
        .put("parts", new JsonArray().add(new JsonObject().put("text", NLU_ROUTING_PROMPT)))
      )
      .put("contents", new JsonArray().add(new JsonObject()
        .put("role", "user")
        .put("parts", new JsonArray().add(new JsonObject().put("text", "User query: \"" + userQuestion + "\"")))
      ))
      .put("generationConfig", new JsonObject()
        .put("responseMimeType", "application/json") // Forces Gemini to return structured raw JSON
        .put("temperature", 0.1)                      // Low temperature ensures strict routing accuracy
        .put("maxOutputTokens", 150)
      );

    return webClient
      .postAbs(endpoint)
      .timeout(GEMINI_TIMEOUT_MS)
      .putHeader("Content-Type", "application/json")
      .sendJsonObject(requestBody)
      .compose(response -> {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
          if (response.statusCode() == 429) {
            return Future.failedFuture("429 Rate limit exceeded for Gemini NLU routing");
          }
          if (response.statusCode() == 503) {
            return Future.failedFuture("503 Service unavailable for Gemini NLU routing");
          }
          return Future.failedFuture("Intent classifier returned HTTP error status: " + response.statusCode());
        }

        JsonObject json = response.bodyAsJsonObject();
        String textResult = extractGeminiText(json);

        if (textResult == null || textResult.isBlank()) {
          return Future.failedFuture("Received empty response from Gemini intent classifier.");
        }

        try {
          JsonObject parsedRouting = new JsonObject(textResult.trim());
          return Future.succeededFuture(parsedRouting);
        } catch (Exception e) {
          return Future.failedFuture("Failed to parse NLU JSON output: " + e.getMessage());
        }
      });
  }

  /**
   * Direct generative fallback conversation pipeline.
   */
  public Future<JsonObject> runDirectAiPipeline(String userMessage) {
    return callGeminiWithFallback(userMessage)
      .map(aiResponse -> {
        String safeAi = sanitizeLimitedHtml(aiResponse);
        return withImageAndDefinition(
          new JsonObject().put("name", "AI Assistant").put("food_name", "AI Assistant"),
          null,
          "NutritionGuard AI Assistant",
          "<b>AI insight</b><br>" + safeAi
        );
      });
  }

  /**
   * Retrieval Pipeline: Coordinates local DB and USDA APIs based on priority.
   */
  public Future<JsonObject> executeRetrievalPipeline(String originalQuestion, String cleanFoodItem, String primaryDestination) {
    if (cleanFoodItem == null || cleanFoodItem.isBlank()) {
      return fallbackToAI(originalQuestion, buildNoRecordFoodResponse(originalQuestion));
    }

    if ("LOCAL_DB".equalsIgnoreCase(primaryDestination)) {
      // Prioritize checking our local database rows first
      return searchLocalDb(cleanFoodItem).compose(dbResult -> {
        if (dbResult != null) {
          return augmentWithAi(originalQuestion, dbResult, "database");
        }
        // Fallback Step 2: Try USDA API
        return callUsda(cleanFoodItem).compose(usdaResult -> {
          if (usdaResult != null) {
            return augmentWithAi(originalQuestion, usdaResult, "usda");
          }
          // Fallback Step 3: Try Vector RAG Store
          return queryRagAndSynthesize(originalQuestion);
        });
      });
    } else {
      // Prioritize standard commodity USDA lookups first
      return callUsda(cleanFoodItem).compose(usdaResult -> {
        if (usdaResult != null) {
          return augmentWithAi(originalQuestion, usdaResult, "usda");
        }
        // Fallback Step 2: Try Local Database
        return searchLocalDb(cleanFoodItem).compose(dbResult -> {
          if (dbResult != null) {
            return augmentWithAi(originalQuestion, dbResult, "database");
          }
          // Fallback Step 3: Try Vector RAG Store
          return queryRagAndSynthesize(originalQuestion);
        });
      });
    }
  }

  private Future<JsonObject> queryRagAndSynthesize(String originalQuestion) {
    return callRagService(originalQuestion).compose(ragContext -> {
      if (ragContext != null && !ragContext.isBlank()) {
        return synthesizeWithRag(originalQuestion, ragContext);
      }
      return fallbackToAI(originalQuestion, buildNoRecordFoodResponse(originalQuestion));
    });
  }

  private Future<JsonObject> searchLocalDb(String query) {
    return foods.search(query).map(items -> {
      if (items == null || items.isEmpty()) return null;

      JsonObject first = items.getJsonObject(0);
      if (first == null) return null;

      String name = first.getString("food_name", "");
      if (name.isBlank()) return null;

      return buildFoodResponse(first, query, true);
    });
  }

  private Future<JsonObject> augmentWithAi(String question, JsonObject baseResponse, String source) {
    if (config.geminiApiKey() == null || config.geminiApiKey().isBlank()) {
      return Future.succeededFuture(baseResponse);
    }

    String contextJson = baseResponse.encode();
    String prompt =
      "User asked: " + question + "\n\n"
        + "Context (from " + source + "): " + contextJson + "\n\n"
        + "Provide a short explanation and practical advice based on this data. Do not repeat nutrition numbers already shown to the user.";

    return callGeminiWithFallback(prompt)
      .map(aiAnswer -> {
        String safeAi = sanitizeLimitedHtml(aiAnswer);
        String combined = baseResponse.getString("answer", "")
          + "<br><br><b>AI insight</b><br>" + safeAi;
        baseResponse.put("answer", combined);
        return baseResponse;
      })
      .recover(err -> Future.succeededFuture(baseResponse));
  }

  private Future<JsonObject> synthesizeWithRag(String question, String ragContext) {
    if (config.geminiApiKey() == null || config.geminiApiKey().isBlank()) {
      String answer = "<b>Nutrition Information</b><br>" + escapeHtml(ragContext);
      return Future.succeededFuture(withImageAndDefinition(
        new JsonObject().put("name", "Nutrition Information").put("food_name", "Nutrition Information"),
        null,
        "Information retrieved from nutrition documents.",
        answer
      ));
    }

    String prompt =
      "User asked: " + question + "\n\n"
        + "Context (from nutrition documents): " + ragContext + "\n\n"
        + "Using the context above, answer the user's question. Explain the key points in simple terms.";

    return aiCaller.apply(prompt)
      .map(aiAnswer -> {
        String safeAi = sanitizeLimitedHtml(aiAnswer);
        return withImageAndDefinition(
          new JsonObject().put("name", "Nutrition Information").put("food_name", "Nutrition Information"),
          null,
          "Information synthesized from nutrition documents.",
          safeAi
        );
      })
      .recover(err -> {
        String answer = "<b>Nutrition Information</b><br>" + escapeHtml(ragContext);
        return Future.succeededFuture(withImageAndDefinition(
          new JsonObject().put("name", "Nutrition Information").put("food_name", "Nutrition Information"),
          null,
          "Information retrieved from nutrition documents.",
          answer
        ));
      });
  }

  private Future<String> callRagService(String question) {
    return ragService.query(question);
  }

  private Future<JsonObject> fallbackToAI(String question, JsonObject defaultResponse) {
    if (config.geminiApiKey() == null || config.geminiApiKey().isBlank()) {
      return Future.succeededFuture(defaultResponse);
    }
    return aiCaller.apply("Please answer this nutrition-related question: " + question)
      .map(aiAnswer -> {
        defaultResponse.put("answer", sanitizeLimitedHtml(aiAnswer));
        if (defaultResponse.getString("definition", "").isBlank()) {
          defaultResponse.put("definition", "NutritionGuard AI Assistant");
        }
        return defaultResponse;
      })
      .recover(err -> {
        System.err.println("Gemini API error: " + err.getMessage());
        return Future.succeededFuture(defaultResponse);
      });
  }

  // ---------------------------------------------------------------------------
  // Legacy keyword pipeline: runtime fallback when Gemini NLU is rate-limited
  // ---------------------------------------------------------------------------

  private Future<JsonObject> legacyKeywordPipeline(String question) {
    if (isGreeting(question)) {
      return Future.succeededFuture(withImageAndDefinition(
        new JsonObject(), null,
        "NutritionGuard AI is a nutrition assistant that helps explain foods and vitamins in simple terms.",
        "Hello! I am your NutriGuard AI. I can help you find vitamin information, suggest healthy meals, or track your progress!"
      ));
    }

    String cleanedQuery = cleanQueryLegacy(question);
    if (cleanedQuery == null || cleanedQuery.isBlank()) {
      return runDirectAiPipeline(question);
    }

    String destination = determineDestinationLegacy(question, cleanedQuery);
    return executeRetrievalPipeline(question, cleanedQuery, destination);
  }

  private String cleanQueryLegacy(String question) {
    if (question == null) return null;
    String cleaned = question.toLowerCase()
      .replaceAll("(?i)\\b(what is|how much|how many|tell me about|is there|can i|do you|what are|tell us about|is|a|an|the|for|with|of|in|on|at|to)\\b", "")
      .replaceAll("\\s+", " ")
      .trim();
    return cleaned.isEmpty() ? null : cleaned;
  }

  private String determineDestinationLegacy(String question, String cleanedQuery) {
    String lower = question.toLowerCase();
    if (lower.contains("salad") || lower.contains("soup") || lower.contains("stew")
        || lower.contains("curry") || lower.contains("plate") || lower.contains("bread")
        || (cleanedQuery.contains(" ") && (lower.contains("wat") || lower.contains("rice")))) {
      return "LOCAL_DB";
    }
    return "USDA_SEARCH";
  }

  private Future<JsonObject> callUsda(String foodQuery) {
    String apiKey = config.usdaApiKey();
    if (apiKey == null || apiKey.isBlank()) {
      return Future.succeededFuture(null);
    }

    String url = USDA_ENDPOINT + "?api_key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
      + "&query=" + URLEncoder.encode(foodQuery, StandardCharsets.UTF_8)
      + "&pageSize=10";

    return webClient.getAbs(url)
      .timeout(5000)
      .send()
      .map(response -> {
        if (response.statusCode() != 200) return null;
        JsonObject json = response.bodyAsJsonObject();
        JsonArray foods = json.getJsonArray("foods");
        if (foods == null || foods.isEmpty()) return null;

        JsonObject bestMatch = null;
        for (int i = 0; i < foods.size(); i++) {
          JsonObject candidate = foods.getJsonObject(i);
          String dataType = candidate.getString("dataType", "");

          if ("Experimental".equalsIgnoreCase(dataType) || "SubSample".equalsIgnoreCase(dataType)) {
            continue;
          }
          bestMatch = candidate;
          break;
        }

        if (bestMatch == null) {
          bestMatch = foods.getJsonObject(0);
        }

        return mapUsdaToInternal(bestMatch, foodQuery);
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

      if (nName.contains("energy") || nName.contains("calories")) {
        String unit = n.getString("unitName", "").toLowerCase();
        if (unit.contains("kj")) {
          int convertedCalories = (int) Math.round(value / 4.184);
          if (!food.containsKey("calories")) {
            food.put("calories", convertedCalories);
          }
        } else {
          food.put("calories", value.intValue());
        }
      }
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

  // Gemini API helpers & Multi-model Fallback Loops
  private Future<String> callGeminiWithFallback(String prompt) {
    return callGemini("gemini-2.5-flash", prompt)
      .recover(err -> {
        System.out.println("Primary model failed, switching to backup...");
        return callGemini("gemini-2.0-flash", prompt);
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

    String encodedKey = URLEncoder.encode(apiKey.trim(), StandardCharsets.UTF_8);
    String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + encodedKey;
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
      .sendJsonObject(requestBody)
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

  // ---------------------------------------------------------------------------
  // Greeting detection (minimal, kept for UX)
  // ---------------------------------------------------------------------------

  private boolean isGreeting(String q) {
    if (q == null) return false;

    String lower = q.toLowerCase().trim();
    if (lower.isEmpty()) return false;

    String[] greetingPhrases = {
      "hi", "hello", "hey", "howdy", "yo", "sup",
      "how are you", "how are you doing", "how do you do",
      "good morning", "good afternoon", "good evening",
      "good day", "good night",
      "what's up", "whassup", "wassup",
      "nice to meet you", "how's it going",
      "ሰላም", "ሰላምታ"
    };

    String stripPunct = lower.replaceAll("[^a-z\\sሰላምታ]", " ").trim();
    if (stripPunct.isEmpty()) return false;

    for (String phrase : greetingPhrases) {
      if (phrase.contains(" ")) {
        if (lower.contains(phrase)) return true;
      } else {
        if (stripPunct.matches("(?i).*\\b" + phrase + "\\b.*")) return true;
      }
    }

    return false;
  }

  private boolean isClearChatQuery(String q) {
    if (q == null) return false;
    String lower = q.toLowerCase().trim();
    if (lower.isEmpty()) return false;

    String[] chatPatterns = {
      "who is", "who are", "what is the weather", "what's the weather",
      "tell me a joke", "tell us a joke", "tell me a story",
      "how old are you", "where are you", "what can you do",
      "who created you", "who made you", "what is your name",
      "thank you", "thanks", "you're welcome",
      "good bye", "goodbye", "bye", "see you", "later",
      "sorry", "i apologize", "my bad"
    };

    for (String pattern : chatPatterns) {
      if (lower.contains(pattern)) return true;
    }

    return false;
  }

  private boolean isValidFoodExtraction(String foodItem, String originalQuestion) {
    if (foodItem == null || foodItem.isBlank()) return false;
    if (originalQuestion == null || originalQuestion.isBlank()) return true;

    String lower = foodItem.toLowerCase().trim();
    String questionLower = originalQuestion.toLowerCase().trim();

    // Reject single words that are clearly not food (greetings, question words, verbs, pronouns)
    if (lower.matches("^(hi|hello|hey|howdy|yo|sup|how|why|what|when|where|who|which|does|did|do|is|are|can|will|would|could|should|have|has|had|make|made|get|got|eat|ate|use|used|say|said|tell|told|ask|asked|give|gave|take|took|let|like|just|good|bad|best|worst|safe|not|no|yes|maybe|okay|ok|please|thanks|thank|sorry|bye|goodbye|hello|hey|hi|sup|yo)$")) {
      return false;
    }

    // Reject if no word from extracted item appears in the original question
    String[] foodWords = lower.split("\\s+");
    boolean anyWordFound = false;
    for (String w : foodWords) {
      if (w.length() > 2 && questionLower.contains(w)) {
        anyWordFound = true;
        break;
      }
    }
    if (!anyWordFound && foodWords.length <= 3) {
      return false;
    }

    // Reject if the extracted item has no food-like word (all words are generic/non-food)
    String[] genericWords = {
      "make", "me", "my", "you", "your", "yours", "it", "its",
      "is", "are", "am", "be", "been", "being",
      "do", "does", "did", "done", "doing",
      "have", "has", "had", "having",
      "can", "will", "would", "could", "should", "shall", "may", "might", "must",
      "this", "that", "these", "those",
      "the", "a", "an", "and", "or", "but", "not", "no",
      "get", "got", "gotten", "want", "wanted", "wanting",
      "need", "needed", "needing", "use", "used", "using",
      "say", "said", "tell", "told", "ask", "asked",
      "give", "gave", "given", "take", "took", "taken",
      "let", "like", "liked", "just",
      "good", "bad", "better", "best", "worst", "worse",
      "safe", "unsafe", "healthy", "unhealthy",
      "sleepy", "tired", "awake", "energetic", "sluggish",
      "why", "how", "what", "when", "where", "who", "which",
      "very", "too", "so", "much", "many", "some", "any",
      "more", "less", "few", "little", "lot", "lots",
      "always", "never", "often", "sometimes", "usually",
      "please", "thanks", "thank", "sorry",
      "hi", "hello", "hey", "bye", "goodbye", "ok", "okay", "yes", "no", "maybe",
      "really", "actually", "basically", "literally",
      "about", "above", "after", "again", "against", "all",
      "also", "because", "before", "between", "both",
      "each", "every", "first", "from", "here", "here's",
      "however", "if", "into", "just", "last", "least",
      "most", "next", "now", "once", "only", "other",
      "over", "own", "same", "such", "than", "then",
      "there", "through", "thus", "too", "under", "until",
      "up", "very", "was", "well", "when", "where",
      "while", "while", "with", "within", "without",
      "yet"
    };
    int genericCount = 0;
    for (String w : foodWords) {
      for (String g : genericWords) {
        if (w.equals(g)) {
          genericCount++;
          break;
        }
      }
    }
    if (genericCount == foodWords.length) {
      return false; // All words are generic/non-food
    }

    return true;
  }

  private boolean isValidIntent(String intent) {
    return VALID_INTENTS.contains(intent);
}

// ---------------------------------------------------------------------------
// Response builders
// ---------------------------------------------------------------------------
  // Response builders
  // ---------------------------------------------------------------------------

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

  // ---------------------------------------------------------------------------
  // Vitamin dataset queries (kept for vitamin quick-chip feature)
  // ---------------------------------------------------------------------------

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

  // ---------------------------------------------------------------------------
  // HTML sanitization
  // ---------------------------------------------------------------------------

  private String sanitizeLimitedHtml(String input) {
    if (input == null) return "";
    String escaped = escapeHtml(input).replace("\r\n", "\n").replace("\n", "<br>");

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

  private String escapeHtml(String input) {
    if (input == null) return "";
    return input
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&#39;");
  }
}
