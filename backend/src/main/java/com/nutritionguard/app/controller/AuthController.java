package com.nutritionguard.app.controller;

import com.nutritionguard.app.util.EventAddresses;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

public class AuthController {
  private final Vertx vertx;

  public AuthController(Vertx vertx) {
    this.vertx = vertx;
  }

  public void mount(Router router) {
    router.post("/api/auth/register").handler(ctx -> {
      JsonObject body = ctx.body() == null ? null : ctx.body().asJsonObject();
      if (body == null) {
        ctx.response().setStatusCode(400).end("Invalid JSON body");
        return;
      }
      request(EventAddresses.AUTH_REGISTER, body)
        .onSuccess(payload -> ctx.response().putHeader("Content-Type", "application/json").end(payload.encode()))
        .onFailure(err -> ctx.response().setStatusCode(statusFrom(err, 400)).end(messageFrom(err)));
    });

    router.get("/api/auth/verify-email").handler(ctx -> {
      String token = ctx.request().getParam("token");
      JsonObject body = new JsonObject().put("token", token == null ? "" : token);
      request(EventAddresses.AUTH_VERIFY_EMAIL, body)
        .onSuccess(payload -> ctx.response().putHeader("Content-Type", "application/json").end(payload.encode()))
        .onFailure(err -> ctx.response().setStatusCode(statusFrom(err, 400)).end(messageFrom(err)));
    });

    router.post("/api/auth/resend-verification").handler(ctx -> {
      JsonObject body = ctx.body() == null ? null : ctx.body().asJsonObject();
      if (body == null) {
        ctx.response().setStatusCode(400).end("Invalid JSON body");
        return;
      }
      request(EventAddresses.AUTH_RESEND_VERIFICATION, body)
        .onSuccess(payload -> ctx.response().putHeader("Content-Type", "application/json").end(payload.encode()))
        .onFailure(err -> ctx.response()
          .setStatusCode(200)
          .putHeader("Content-Type", "application/json")
          .end(new JsonObject().put("message", "If an account exists for that email, a verification link has been sent.").encode()));
    });

    router.post("/api/auth/login").handler(ctx -> {
      JsonObject body = ctx.body() == null ? null : ctx.body().asJsonObject();
      if (body == null) {
        ctx.response().setStatusCode(400).end("Invalid JSON body");
        return;
      }
      request(EventAddresses.AUTH_LOGIN, body)
        .onSuccess(payload -> ctx.response().putHeader("Content-Type", "application/json").end(payload.encode()))
        .onFailure(err -> ctx.response().setStatusCode(statusFrom(err, 401)).end(messageFrom(err)));
    });

    router.post("/api/auth/forgot-password").handler(ctx -> {
      JsonObject body = ctx.body() == null ? null : ctx.body().asJsonObject();
      if (body == null) {
        ctx.response().setStatusCode(400).end("Invalid JSON body");
        return;
      }
      request(EventAddresses.AUTH_FORGOT_PASSWORD, body)
        .onSuccess(payload -> ctx.response().putHeader("Content-Type", "application/json").end(payload.encode()))
        .onFailure(err -> ctx.response()
          .setStatusCode(200)
          .putHeader("Content-Type", "application/json")
          .end(new JsonObject().put("message", "If an account exists for that email, a password reset link has been sent.").encode()));
    });

    router.post("/api/auth/reset-password").handler(ctx -> {
      JsonObject body = ctx.body() == null ? null : ctx.body().asJsonObject();
      if (body == null) {
        ctx.response().setStatusCode(400).end("Invalid JSON body");
        return;
      }
      request(EventAddresses.AUTH_RESET_PASSWORD, body)
        .onSuccess(payload -> ctx.response().putHeader("Content-Type", "application/json").end(payload.encode()))
        .onFailure(err -> ctx.response().setStatusCode(statusFrom(err, 400)).end(messageFrom(err)));
    });
  }

  private io.vertx.core.Future<JsonObject> request(String address, JsonObject body) {
    return vertx.eventBus()
      .<JsonObject>request(address, body, new DeliveryOptions().setSendTimeout(15_000))
      .map(msg -> msg.body());
  }

  private int statusFrom(Throwable err, int fallback) {
    if (err instanceof ReplyException re) {
        int code = re.failureCode();
        // FIX: Validate that the code is a valid HTTP status code (100-599)
        // Vert.x returns -1 on timeout, which is invalid
        if (code >= 100 && code <= 599) {
            return code;
        }
    }
    return fallback;
}

  private String messageFrom(Throwable err) {
    if (err instanceof ReplyException re) {
      String msg = re.getMessage();
      if (msg != null && !msg.isBlank()) {
        return msg;
      }
    }
    String msg = err.getMessage();
    return msg == null ? "Request failed" : msg;
  }
}
