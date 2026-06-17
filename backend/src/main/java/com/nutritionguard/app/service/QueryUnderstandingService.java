package com.nutritionguard.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutritionguard.app.config.AppConfig;
import com.nutritionguard.app.model.QueryUnderstandingResult;
import io.vertx.core.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Service that performs the Query Understanding step.
 * Uses Gemini (aiCaller) to extract structured semantics from user queries.
 */
public class QueryUnderstandingService {

    private static final Logger logger =
            LoggerFactory.getLogger(QueryUnderstandingService.class);

    private final Function<String, Future<String>> aiCaller;
    private final AppConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public QueryUnderstandingService(AppConfig config,
                                      Function<String, Future<String>> aiCaller) {
        this.config = config;
        this.aiCaller = aiCaller;
    }

    public Future<QueryUnderstandingResult> understand(String question) {

        // 🧠 THE BULLETPROOF PROMPT 🧠
        String prompt = "You are an expert NLU system for a nutrition chatbot. Your job is to extract structured data from user questions, even if the grammar is poor, contains typos, or mentions local dishes.\n\n"
                + "Extract the following into a JSON object:\n"
                + "- \"confidence\": (number 0.0 to 1.0)\n"
                + "- \"intent\": (string, e.g., 'check_health_benefit', 'get_nutrition_info', 'compare_foods')\n"
                + "- \"entities\": (list of objects with \"type\" and \"value\")\n"
                + "- \"searchContext\": object containing arrays for:\n"
                + "    - \"foods\": The exact name of the food(s) mentioned. Extract the food name even if misspelled or a local dish (e.g., 'firfir', 'doro wat', 'injera').\n"
                + "    - \"nutrients\": Specific vitamins/minerals mentioned (e.g., 'vitamin c', 'protein').\n"
                + "    - \"conditions\": Medical conditions (e.g., 'diabetes', 'anemia').\n"
                + "    - \"healthGoals\": Goals like 'weight gain', 'weight loss'.\n"
                + "- \"searchTerms\": A list of the best 1-3 keywords to search a food database (usually just the food name).\n\n"
                + "EXAMPLE 1:\n"
                + "Question: \"is it benefit normal firfir to weight gain?\"\n"
                + "JSON: {\n"
                + "  \"confidence\": 0.9,\n"
                + "  \"intent\": \"check_health_benefit\",\n"
                + "  \"entities\": [{\"type\": \"food\", \"value\": \"normal firfir\"}, {\"type\": \"healthGoal\", \"value\": \"weight gain\"}],\n"
                + "  \"searchContext\": {\n"
                + "    \"foods\": [\"normal firfir\"],\n"
                + "    \"nutrients\": [],\n"
                + "    \"conditions\": [],\n"
                + "    \"healthGoals\": [\"weight gain\"]\n"
                + "  },\n"
                + "  \"searchTerms\": [\"normal firfir\"]\n"
                + "}\n\n"
                + "EXAMPLE 2:\n"
                + "Question: \"nutartional value of chicken salad?\"\n"
                + "JSON: {\n"
                + "  \"confidence\": 1.0,\n"
                + "  \"intent\": \"get_nutrition_info\",\n"
                + "  \"entities\": [{\"type\": \"food\", \"value\": \"chicken salad\"}],\n"
                + "  \"searchContext\": {\n"
                + "    \"foods\": [\"chicken salad\"],\n"
                + "    \"nutrients\": [],\n"
                + "    \"conditions\": [],\n"
                + "    \"healthGoals\": []\n"
                + "  },\n"
                + "  \"searchTerms\": [\"chicken salad\"]\n"
                + "}\n\n"
                + "RULES:\n"
                + "1. Output ONLY valid JSON. No markdown, no explanations.\n"
                + "2. ALWAYS extract the food name into the \"foods\" array, even if the grammar is broken.\n"
                + "3. If no specific food is mentioned, \"foods\" should be an empty array.\n\n"
                + "Question: \"" + question + "\"";

        return aiCaller.apply(prompt)
                .compose(json -> {
                    try {
                        // Clean up any accidental markdown formatting the AI might add
                        String cleanJson = json.trim();
                        if (cleanJson.startsWith("```json")) {
                            cleanJson = cleanJson.substring(7);
                        }
                        if (cleanJson.startsWith("```")) {
                            cleanJson = cleanJson.substring(3);
                        }
                        if (cleanJson.endsWith("```")) {
                            cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
                        }
                        cleanJson = cleanJson.trim();

                        JsonNode root = objectMapper.readTree(cleanJson);

                        double confidence = root.path("confidence").asDouble(0.0);
                        String intent = root.path("intent").asText("");

                        // -------- entities --------
                        List<QueryUnderstandingResult.Entity> entities = new ArrayList<>();
                        for (JsonNode e : root.path("entities")) {
                            entities.add(new QueryUnderstandingResult.Entity(
                                    e.path("type").asText(""),
                                    e.path("value").asText("")
                            ));
                        }

                        // -------- search context --------
                        JsonNode sc = root.path("searchContext");

                        List<String> foods = readArray(sc, "foods");
                        List<String> nutrients = readArray(sc, "nutrients");
                        List<String> conditions = readArray(sc, "conditions");
                        List<String> healthGoals = readArray(sc, "healthGoals");

                        QueryUnderstandingResult.SearchContext searchContext =
                                new QueryUnderstandingResult.SearchContext(
                                        foods, nutrients, conditions, healthGoals
                                );

                        // -------- search terms --------
                        List<String> searchTerms = readArray(root, "searchTerms");

                        String queryId = UUID.randomUUID().toString();

                        QueryUnderstandingResult result =
                                new QueryUnderstandingResult(
                                        queryId,
                                        confidence,
                                        entities,
                                        searchContext,
                                        question,
                                        intent,
                                        searchTerms
                                );

                        logger.debug("Parsed QueryUnderstandingResult: {}",
                                objectMapper.writeValueAsString(result));

                        return Future.succeededFuture(result);

                    } catch (Exception e) {
                        logger.error("Failed to parse Gemini response. Raw JSON: {}", json, e);
                        return Future.succeededFuture(
                                QueryUnderstandingResult.minimal(question)
                        );
                    }
                })
                .recover(err -> {
                    logger.error("Gemini call failed", err);
                    return Future.succeededFuture(
                            QueryUnderstandingResult.minimal(question)
                    );
                });
    }

    // -------- helper --------
    private List<String> readArray(JsonNode node, String field) {
        List<String> list = new ArrayList<>();
        for (JsonNode n : node.path(field)) {
            list.add(n.asText());
        }
        return list;
    }
}