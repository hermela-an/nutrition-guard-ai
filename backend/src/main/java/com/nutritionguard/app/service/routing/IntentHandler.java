package com.nutritionguard.app.service.routing;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * Handler for a specific Gemini NLU intent.
 * Implementations receive the full VitaminChatService so they can reuse its private helper methods
 * (e.g., executeRetrievalPipeline, runDirectAiPipeline, etc.).
 */
public interface IntentHandler {
    /**
     * Process the request for the given intent.
     *
     * @param question          original user question
     * @param cleanFoodItem     extracted food item (may be null)
     * @param destination       routing destination (LOCAL_DB, USDA_SEARCH, RAG, CHAT, GREETING)
     * @param routingDecision   the raw Gemini JSON containing intent, entities, etc.
     * @return a Future that resolves to the final JsonObject response
     */
    Future<JsonObject> handle(String question,
                               String cleanFoodItem,
                               String destination,
                               JsonObject routingDecision);
}
