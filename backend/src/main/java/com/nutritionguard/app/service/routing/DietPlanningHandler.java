package com.nutritionguard.app.service.routing;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * Handler for diet planning intent.
 */
public class DietPlanningHandler implements IntentHandler {
    private final com.nutritionguard.app.service.VitaminChatService service;

    public DietPlanningHandler(com.nutritionguard.app.service.VitaminChatService service) {
        this.service = service;
    }

    @Override
    public Future<JsonObject> handle(String question, String cleanFoodItem, String destination, JsonObject routingDecision) {
        // Placeholder – delegate to generic retrieval pipeline.
        return service.executeRetrievalPipeline(question, cleanFoodItem, destination);
    }
}
