package com.nutritionguard.app.verticle;

import com.nutritionguard.app.config.AppConfig;
import com.nutritionguard.app.db.Database;
import com.nutritionguard.app.repository.PasswordResetTokenRepository;
import com.nutritionguard.app.repository.UserRepository;
import com.nutritionguard.app.service.AuthService;
import com.nutritionguard.app.service.PasswordResetMailer;
import com.nutritionguard.app.util.EventAddresses;
import com.nutritionguard.app.util.JwtService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;

public class AuthVerticle extends AbstractVerticle {
  private final Database db;
  private final AppConfig config;

  public AuthVerticle(Database db, AppConfig config) {
    this.db = db;
    this.config = config;
  }

  @Override
  public void start() {
    AuthService service = new AuthService(
      new UserRepository(db),
      new PasswordResetTokenRepository(db),
      new JwtService(vertx, config),
      new PasswordResetMailer(vertx, config),
      config
    );

    vertx.eventBus().consumer(EventAddresses.AUTH_REGISTER, msg -> {
      JsonObject body = (JsonObject) msg.body();
      if (body == null) {
        msg.fail(400, "Invalid JSON body");
        return;
      }
      String email = body.getString("email");
      String password = body.getString("password");
      if (email == null || email.isBlank() || password == null) {
        msg.fail(400, "Missing required fields: email, password");
        return;
      }
      service.register(email, password)
        .onSuccess(msg::reply)
        .onFailure(err -> msg.fail(400, err.getMessage()));
    });

    vertx.eventBus().consumer(EventAddresses.AUTH_LOGIN, msg -> {
      JsonObject body = (JsonObject) msg.body();
      if (body == null) {
        msg.fail(400, "Invalid JSON body");
        return;
      }
      String email = body.getString("email");
      String password = body.getString("password");
      if (email == null || email.isBlank() || password == null) {
        msg.fail(400, "Missing required fields: email, password");
        return;
      }
      service.login(email, password)
        .onSuccess(msg::reply)
        .onFailure(err -> msg.fail(401, err.getMessage()));
    });

    vertx.eventBus().consumer(EventAddresses.AUTH_FORGOT_PASSWORD, msg -> {
      JsonObject body = (JsonObject) msg.body();
      if (body == null) {
        msg.fail(400, "Invalid JSON body");
        return;
      }
      String email = body.getString("email");
      if (email == null || email.isBlank()) {
        // Still return the generic success message to avoid enumeration.
        msg.reply(new JsonObject().put("message", "If an account exists for that email, a password reset link has been sent."));
        return;
      }
      service.forgotPassword(email)
        .onSuccess(msg::reply)
        .onFailure(err -> msg.reply(new JsonObject().put("message", "If an account exists for that email, a password reset link has been sent.")));
    });

    vertx.eventBus().consumer(EventAddresses.AUTH_RESET_PASSWORD, msg -> {
      JsonObject body = (JsonObject) msg.body();
      if (body == null) {
        msg.fail(400, "Invalid JSON body");
        return;
      }
      String token = body.getString("token");
      String password = body.getString("password");
      if (token == null || token.isBlank() || password == null) {
        msg.fail(400, "Missing required fields: token, password");
        return;
      }
      service.resetPassword(token, password)
        .onSuccess(msg::reply)
        .onFailure(err -> msg.fail(400, err.getMessage()));
    });
  }
}
