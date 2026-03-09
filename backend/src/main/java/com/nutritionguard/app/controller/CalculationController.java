package com.nutritionguard.app.controller;

import com.nutritionguard.app.util.EventAddresses;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

public class CalculationController {
  private final Vertx vertx;

  public CalculationController(Vertx vertx) {
    this.vertx = vertx;
  }

  public void mount(Router router) {
    router.post("/api/calculate/targets").handler(ctx -> {
      JsonObject body = ctx.body().asJsonObject();
      vertx.eventBus().<JsonObject>request(EventAddresses.CALC_TARGETS, body)
        .onSuccess(msg -> ctx.response().putHeader("Content-Type", "application/json").end(msg.body().encode()))
        .onFailure(err -> ctx.response().setStatusCode(400).end(err.getMessage()));
    });
  }
}
