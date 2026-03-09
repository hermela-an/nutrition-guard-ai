package com.nutritionguard.app.verticle;

import com.nutritionguard.app.service.CalculationService;
import com.nutritionguard.app.util.EventAddresses;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;

public class CalculationVerticle extends AbstractVerticle {
  @Override
  public void start() {
    CalculationService service = new CalculationService();
    vertx.eventBus().consumer(EventAddresses.CALC_TARGETS, msg -> {
      JsonObject body = (JsonObject) msg.body();
      try {
        msg.reply(service.calculateTargets(body));
      } catch (Exception ex) {
        msg.fail(400, ex.getMessage());
      }
    });
  }
}
