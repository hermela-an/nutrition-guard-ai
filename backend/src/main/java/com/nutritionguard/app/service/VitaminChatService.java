package com.nutritionguard.app.service;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import com.nutritionguard.app.model.RetrievalQuery;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;


import java.util.function.Function;
import java.util.Set;
import com.nutritionguard.app.config.AppConfig;
import com.nutritionguard.app.repository.FoodRepository;
import java.util.List;

 /**
 * Service to handle user conversational requests in a health and nutrition application.
 * Uses a Semantic NLU classifier powered by Gemini to route questions to the correct
 * destination (Local DB, USDA FoodData Central, Conversational Chat, or Greetings).
 */
public class VitaminChatService {

  private static final Logger logger = LoggerFactory.getLogger(VitaminChatService.class);
  private final FoodRepository foods;
  private final AppConfig config;
  private final WebClient webClient;
  private final Function<String, Future<String>> aiCaller;
  private final RagService ragService;
  private final QueryUnderstandingService queryUnderstandingService;

  private static final int GEMINI_TIMEOUT_MS = 15_000;
  private static final String USDA_ENDPOINT = "https://api.nal.usda.gov/fdc/v1/foods/search";
  private static final Set<String> VALID_INTENTS = Set.of(); // placeholder, intents removed
  private static final String SYSTEM_PROMPT = "You are a helpful, polite nutrition assistant named NutriGuard AI. " +
            "You are allowed to engage in basic conversational pleasantries, such as greetings, farewells, saying 'you're welcome', and introducing yourself. " +
            "However, you MUST strictly refuse to answer questions about unrelated general knowledge topics (such as programming, history, politics, weather, math, or general trivia). " +
            "If asked about an unrelated general knowledge topic, you MUST reply with exactly: 'I am a nutritional assistant and only answer food-related or descriptive nutrition questions.'";

   
     private static final String FALLBACK_IMAGE_URL =
    "https://images.unsplash.com/photo-1490645935967-10de6ba17061?auto=format&fit=crop&w=400&q=80";

  public VitaminChatService(Vertx vertx, AppConfig config, FoodRepository foods, RagService ragService, QueryUnderstandingService queryUnderstandingService) {
    this(vertx, config, foods, ragService, queryUnderstandingService, null);
  }

  public VitaminChatService(
    Vertx vertx,
    AppConfig config,
    FoodRepository foods,
    RagService ragService,
    QueryUnderstandingService queryUnderstandingService,
    Function<String, Future<String>> aiCaller
  ) {
    this.foods = foods;
    this.config = config;
    this.webClient = WebClient.create(vertx, new WebClientOptions().setSsl(true).setTrustAll(true));
    this.ragService = ragService;
    this.queryUnderstandingService = queryUnderstandingService;
    this.aiCaller = aiCaller == null ? this::callGeminiWithFallback : aiCaller;
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
    // ==========================================================================    
    return queryUnderstandingService.understand(question).compose(qurResult -> {
            RetrievalQuery retrievalQuery = new RetrievalQuery(question, qurResult);
            Future<JsonObject> localFuture = retrieveLocal(retrievalQuery);
            Future<JsonObject> usdaFuture = retrieveUsda(retrievalQuery);
            return localFuture.compose(localCtx ->
                usdaFuture.compose(usdaCtx -> {
                    // Query RAG with only the question and search terms
                    return queryRagWithContext(retrievalQuery).compose(ragAnswer -> {
                        JsonObject finalAgg = new JsonObject()
                                .put("local", localCtx != null ? localCtx.getJsonArray("items") : null)
                                .put("usda", usdaCtx)
                                .put("rag", ragAnswer);
                                logger.info("🧠 FINAL AI PROMPT - Master package being sent:\n{}", finalAgg.encodePrettily());
                        // 4. Final AI synthesis using all context
                        return synthesizeFinal(retrievalQuery, finalAgg);
                    });
                })
            );
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


        // 🚑 THE RESCUE SAFETY NET
    // If the main NLU fails to find a food, we ask the AI one simple question:
    // "Is there a food name in this sentence?"
    private Future<String> rescueExtractFoodName(String question) {
        String rescuePrompt = "You are a strict food entity extractor. Read the following user question and extract the exact name of the food or dish mentioned. " +
                "If the user is asking a general question without mentioning a specific food, reply with exactly the word 'NONE'. " +
                "Do not explain. Output ONLY the food name or 'NONE'.\n\n" +
                "Question: \"" + question + "\"\n" +
                "Food name:";
                
        return aiCaller.apply(rescuePrompt).map(response -> {
            String cleaned = response.trim().toLowerCase();
            // If the AI says NONE, or if it returns garbage, we return null
            if (cleaned.contains("none") || cleaned.isEmpty() || cleaned.length() > 50) {
                return null; 
            }
            // Clean up any punctuation the AI might have added
            return cleaned.replaceAll("[^a-z0-9\\s]", "").trim();
        }).recover(err -> {
            System.out.println("🚑 Rescue AI call failed: " + err.getMessage());
            return Future.succeededFuture(null);
        });
    }
    private Future<JsonObject> retrieveLocal(RetrievalQuery rq) {
        // 1. Get the extracted foods from the main NLU
        List<String> extractedFoods = rq.getUnderstanding().getSearchContext().getFoods();
        
        // 2. If the NLU found a food, search the DB immediately!
        if (extractedFoods != null && !extractedFoods.isEmpty()) {
            String query = String.join(" ", extractedFoods);
            System.out.println("🔍 LOCAL DB SEARCH - Query sent to DB: " + query);
            
            return foods.search(query).map(items -> {
                if (items == null || items.isEmpty()) return null;
                JsonArray limited = new JsonArray();
                for (int i = 0; i < Math.min(3, items.size()); i++) {
                    limited.add(items.getJsonObject(i));
                }
                System.out.println("🔍 LOCAL DB RESULT - Found " + limited.size() + " items.");
                return new JsonObject().put("items", limited);
            });
        }

        // 3. 🚑 RESCUE TRIGGERED: The NLU failed to find a food.
        System.out.println("⚠️ NLU found no foods. Triggering Rescue Extraction...");
        
        return rescueExtractFoodName(rq.getOriginalQuestion()).compose(rescuedFood -> {
            if (rescuedFood == null) {
                System.out.println("🛑 Rescue failed (User likely didn't mention a food). Skipping Local DB.");
                return Future.succeededFuture(null);
            }
            
            System.out.println("🚑 Rescue SUCCESS! Found food: " + rescuedFood);
            
            // Now search the DB with the rescued food name!
            return foods.search(rescuedFood).map(items -> {
                if (items == null || items.isEmpty()) return null;
                JsonArray limited = new JsonArray();
                for (int i = 0; i < Math.min(3, items.size()); i++) {
                    limited.add(items.getJsonObject(i));
                }
                return new JsonObject().put("items", limited);
            });
        });
    }

  
    private Future<String> queryRagWithContext(RetrievalQuery rq) {
        // Build prompt using only the user's question and extracted search terms.
        String ragPrompt = "User original question: " + rq.getOriginalQuestion() + "\nSearch terms: " + String.join(" ", rq.getUnderstanding().getSearchTerms());

         //logger.info("📚 RAG SEARCH - Prompt sent to RAG:\n{}", ragPrompt);
         System.out.println("📚 RAG SEARCH - Prompt sent to RAG:\n" + ragPrompt);
        return ragService.query(ragPrompt);
    }
        private Future<JsonObject> synthesizeFinal(RetrievalQuery rq, JsonObject masterPackage) {
        String ragAnswer = masterPackage.getString("rag", "");
        
        // 1. Truncate RAG answer ONLY if it's a massive document (to prevent Groq 413 crashes).
        // 2000 characters is plenty for detailed answers, but stops huge PDF dumps from breaking the app.
        if (ragAnswer.length() > 2000) {
            ragAnswer = ragAnswer.substring(0, 2000) + "... [truncated]";
        }

        // 2. Use .encode() instead of .encodePrettily() to remove all whitespace and save thousands of tokens!
        // AI models don't need pretty spacing, they just need the raw data.
        String localStr = masterPackage.getJsonArray("local") != null ? masterPackage.getJsonArray("local").encode() : "None";
        String usdaStr = masterPackage.getJsonObject("usda") != null ? masterPackage.getJsonObject("usda").encode() : "None";

        // 3. Ultra-compact prompt to save tokens
        String prompt = "You are a strict nutrition assistant. Answer the user's question using ONLY the provided data. " +
                "If the provided data is empty, or if the question is completely unrelated to food/nutrition, " +
                "you MUST reply with EXACTLY this phrase and nothing else: 'I am a nutritional assistant and only answer food-related or descriptive nutrition questions.'\n\n" +
                "Q: " + rq.getOriginalQuestion() + "\n" +
                "Local: " + localStr + "\n" +
                "USDA: " + usdaStr + "\n" +
                "RAG: " + ragAnswer + "\n" +
                "Reply concisely.";
            
        System.out.println("🧠 FINAL AI PROMPT - Exact text sent to AI:\n" + prompt);
        
        return aiCaller.apply(prompt).map(aiAnswer -> {
            // After obtaining clean text answer, format final UI payload.
            String safeAnswer = sanitizeLimitedHtml(aiAnswer);
            return withImageAndDefinition(new JsonObject(), null, "NutritionGuard AI Assistant", safeAnswer);
        });
    }

        private Future<JsonObject> retrieveUsda(RetrievalQuery rq) {
        // 1. Get the extracted foods and nutrients from the main NLU
        List<String> extractedFoods = rq.getUnderstanding().getSearchContext().getFoods();
        List<String> extractedNutrients = rq.getUnderstanding().getSearchContext().getNutrients();

        boolean hasFoods = extractedFoods != null && !extractedFoods.isEmpty();
        boolean hasNutrients = extractedNutrients != null && !extractedNutrients.isEmpty();

        // 2. If the NLU found AT LEAST ONE food or nutrient, search USDA immediately!
        if (hasFoods || hasNutrients) {
            StringBuilder sb = new StringBuilder();
            if (hasFoods) {
                sb.append(String.join(" ", extractedFoods));
            }
            if (hasNutrients) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(String.join(" ", extractedNutrients));
            }
            
            String query = sb.toString();
            System.out.println("🔍 USDA API SEARCH - Query sent to USDA: " + query);
            
            return callUsda(query);
        }

        // 3. 🚑 RESCUE TRIGGERED: The NLU found absolutely no foods or nutrients.
        System.out.println("⚠️ NLU found no foods or nutrients for USDA. Triggering Rescue Extraction...");
        
        return rescueExtractFoodName(rq.getOriginalQuestion()).compose(rescuedFood -> {
            if (rescuedFood == null) {
                System.out.println("🛑 Rescue failed (User likely didn't mention a food). Skipping USDA API.");
                return Future.succeededFuture(null);
            }
            
            System.out.println("🚑 Rescue SUCCESS for USDA! Found food: " + rescuedFood);
            
            // Now search the USDA API with the rescued food name!
            return callUsda(rescuedFood);
        });
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
            .timeout(60000)
            .send()
            .map(response -> {
                if (response.statusCode() != 200) return null;
                JsonObject json = response.bodyAsJsonObject();
                JsonArray foods = json.getJsonArray("foods");
                if (foods == null || foods.isEmpty()) return null;
                // Pick first suitable match.
                JsonObject bestMatch = null;
                for (int i = 0; i < foods.size(); i++) {
                    JsonObject candidate = foods.getJsonObject(i);
                    String dataType = candidate.getString("dataType", "");
                    if ("Experimental".equalsIgnoreCase(dataType) || "SubSample".equalsIgnoreCase(dataType)) continue;
                    bestMatch = candidate;
                    break;
                }
                if (bestMatch == null) bestMatch = foods.getJsonObject(0);
                // Return raw mapped object.
                return mapUsdaToRaw(bestMatch, foodQuery);
            })
            .recover(err -> {
                System.err.println("USDA API error: " + err.getMessage());
                return Future.succeededFuture(null);
            });
    }

    private JsonObject mapUsdaToRaw(JsonObject usdaFood, String query) {
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
                    int converted = (int) Math.round(value / 4.184);
                    if (!food.containsKey("calories")) food.put("calories", converted);
                } else {
                    food.put("calories", value.intValue());
                }
            } else if (nName.contains("protein")) {
                food.put("protein_g", value);
            } else if (nName.contains("carbohydrate")) {
                food.put("carbs_g", value);
            } else if (nName.contains("total lipid") || nName.contains("fat")) {
                food.put("fat_g", value);
            } else if (nName.contains("fiber")) {
                food.put("fiber_g", value);
            } else if (nName.contains("sugars")) {
                food.put("sugar_g", value);
            } else if (nName.contains("sodium")) {
                food.put("sodium_mg", value);
            } else if (nName.contains("cholesterol")) {
                food.put("cholesterol_mg", value);
            }
        }
        return food;
    }

  // Gemini API helpers & Multi-model Fallback Loops
    private Future<String> callGeminiWithFallback(String prompt) {
    return callGemini("gemini-2.5-flash", prompt)
      .recover(err -> {
          // 🚨 PRINT THE EXACT GEMINI ERROR HERE
          System.out.println("🚨 Primary model (Gemini) failed: " + err.getMessage());
          System.out.println("Switching to Groq...");
          return callGroq("llama-3.3-70b-versatile", prompt);
      })
      .recover(err -> {
          // 🚨 PRINT THE EXACT GROQ ERROR HERE
          System.out.println("🚨 Groq model failed: " + err.getMessage());
          System.out.println("Using safe fallback...");
          return Future.succeededFuture(
              "I'm currently experiencing high demand. Please try again shortly, or ask about specific foods or nutrients."
          );
      });
  }

  private Future<String> callGroq(String model, String userPrompt) {
    String apiKey = config.groqApiKey();
    if (apiKey == null || apiKey.isBlank()) {
      return Future.failedFuture("Missing GROQ_API_KEY");
    }

    String endpoint = "https://api.groq.com/openai/v1/chat/completions";
    String prompt = userPrompt == null ? "" : userPrompt;

    JsonObject requestBody = new JsonObject()
      .put("model", model)
      .put("messages", new JsonArray()
        .add(new JsonObject().put("role", "system").put("content", SYSTEM_PROMPT))
        .add(new JsonObject().put("role", "user").put("content", prompt))
      )
            .put("temperature", 0.4)
      .put("max_tokens", 2000); // <--- INCREASED TO 2000

    return webClient
      .postAbs(endpoint)
      .timeout(GEMINI_TIMEOUT_MS)
      .putHeader("Authorization", "Bearer " + apiKey.trim())
      .putHeader("Content-Type", "application/json")
      .sendJsonObject(requestBody)
      .compose(response -> {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
          return Future.failedFuture("Groq HTTP Error: " + response.statusCode() + " " + safeTruncate(response.bodyAsString(), 1200));
        }

        JsonObject json;
        try {
          json = response.bodyAsJsonObject();
        } catch (Exception e) {
          return Future.failedFuture("Error parsing Groq JSON: " + e.getMessage());
        }

        String text = extractGroqText(json);
        if (text == null || text.isBlank()) {
          return Future.failedFuture("Groq response invalid or empty");
        }

        return Future.succeededFuture(text.trim());
      });
  }

  private String extractGroqText(JsonObject json) {
    if (json == null) return null;
    JsonArray choices = json.getJsonArray("choices");
    if (choices == null || choices.isEmpty()) return null;

    JsonObject choice = choices.getJsonObject(0);
    if (choice == null) return null;

    JsonObject message = choice.getJsonObject("message");
    if (message == null) return null;

    return message.getString("content");
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
        .put("maxOutputTokens", 2000) // <--- INCREASED TO 2000
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
      "ሰላም"
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
      "who is", "who are", 
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
