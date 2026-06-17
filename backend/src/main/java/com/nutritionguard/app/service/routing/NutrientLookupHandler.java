package com.nutritionguard.app.service.routing;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * Handler for nutrient lookup intent.
 */
public class NutrientLookupHandler implements IntentHandler {
    private final com.nutritionguard.app.service.VitaminChatService service;
    public NutrientLookupHandler(com.nutritionguard.app.service.VitaminChatService service) {
        this.service = service;
    }
    @Override
    public Future<JsonObject> handle(String question, String cleanFoodItem, String destination, JsonObject routingDecision) {
        // Simple placeholder implementation: delegate to existing service method if available
        // Here we just return a not-yet-implemented response.
        return Future.succeededFuture(new JsonObject().put("error", "NutrientLookupHandler not implemented"));
    }
}
