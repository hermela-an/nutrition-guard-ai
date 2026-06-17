package com.nutritionguard.app.service.routing;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * Handler for vitamin lookup intent.
 */
public class VitaminLookupHandler implements IntentHandler {
    private final com.nutritionguard.app.service.VitaminChatService service;

    public VitaminLookupHandler(com.nutritionguard.app.service.VitaminChatService service) {
        this.service = service;
    }

    @Override
    public Future<JsonObject> handle(String question, String cleanFoodItem, String destination, JsonObject routingDecision) {
        // Placeholder – reuse generic retrieval pipeline.
        return service.executeRetrievalPipeline(question, cleanFoodItem, destination);
    }
}
