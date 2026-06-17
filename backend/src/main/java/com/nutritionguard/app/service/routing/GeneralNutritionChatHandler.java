package com.nutritionguard.app.service.routing;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * Handler for general nutrition chat intent.
 */
public class GeneralNutritionChatHandler implements IntentHandler {
    private final com.nutritionguard.app.service.VitaminChatService service;

    public GeneralNutritionChatHandler(com.nutritionguard.app.service.VitaminChatService service) {
        this.service = service;
    }

    @Override
    public Future<JsonObject> handle(String question, String cleanFoodItem, String destination, JsonObject routingDecision) {
        // For general chat, just run the direct AI pipeline.
        return service.runDirectAiPipeline(question);
    }
}
