package com.nutritionguard.app.controller;

import com.nutritionguard.app.util.EventAddresses;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

public class ChatController {
  private final Vertx vertx;

  public ChatController(Vertx vertx) {
    this.vertx = vertx;
  }

  public void mount(Router router) {
    router.post("/api/chat/ask").handler(ctx -> {
      JsonObject body = ctx.body().asJsonObject();
      vertx.eventBus().<JsonObject>request(EventAddresses.CHAT_ASK, body)
        .onSuccess(msg -> ctx.response().putHeader("Content-Type", "application/json").end(msg.body().encode()))
        .onFailure(err -> ctx.response().setStatusCode(400).end(err.getMessage()));
    });
  }
}
