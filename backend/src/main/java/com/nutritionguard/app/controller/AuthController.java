package com.nutritionguard.app.controller;

import com.nutritionguard.app.util.EventAddresses;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

public class AuthController {
  private final Vertx vertx;

  public AuthController(Vertx vertx) {
    this.vertx = vertx;
  }

  public void mount(Router router) {
    router.post("/api/auth/register").handler(ctx -> {
      JsonObject body = ctx.body().asJsonObject();
      request(EventAddresses.AUTH_REGISTER, body)
        .onSuccess(payload -> ctx.response().putHeader("Content-Type", "application/json").end(payload.encode()))
        .onFailure(err -> ctx.response().setStatusCode(400).end(err.getMessage()));
    });

    router.post("/api/auth/login").handler(ctx -> {
      JsonObject body = ctx.body().asJsonObject();
      request(EventAddresses.AUTH_LOGIN, body)
        .onSuccess(payload -> ctx.response().putHeader("Content-Type", "application/json").end(payload.encode()))
        .onFailure(err -> ctx.response().setStatusCode(401).end(err.getMessage()));
    });
  }

  private io.vertx.core.Future<JsonObject> request(String address, JsonObject body) {
    return vertx.eventBus()
      .<JsonObject>request(address, body, new DeliveryOptions().setSendTimeout(5_000))
      .map(msg -> msg.body());
  }
}
