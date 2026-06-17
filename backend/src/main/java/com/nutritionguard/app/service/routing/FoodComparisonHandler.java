package com.nutritionguard.app.service.routing;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * Handler for food comparison intent.
 */
public class FoodComparisonHandler implements IntentHandler {
    private final com.nutritionguard.app.service.VitaminChatService service;

    public FoodComparisonHandler(com.nutritionguard.app.service.VitaminChatService service) {
        this.service = service;
    }

    @Override
    public Future<JsonObject> handle(String question, String cleanFoodItem, String destination, JsonObject routingDecision) {
        // Placeholder – actual comparison logic to be added later.
        return Future.succeededFuture(new JsonObject().put("error", "FoodComparisonHandler not implemented"));
    }
}
