package com.nutritionguard.app.service.routing;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * Handler for symptom analysis intent.
 */
public class SymptomAnalysisHandler implements IntentHandler {
    private final com.nutritionguard.app.service.VitaminChatService service;

    public SymptomAnalysisHandler(com.nutritionguard.app.service.VitaminChatService service) {
        this.service = service;
    }

    @Override
    public Future<JsonObject> handle(String question, String cleanFoodItem, String destination, JsonObject routingDecision) {
        // Placeholder implementation – real symptom analysis logic to be added.
        return Future.succeededFuture(new JsonObject().put("error", "SymptomAnalysisHandler not implemented"));
    }
}
