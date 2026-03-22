package com.nutritionguard.app.verticle;

import com.nutritionguard.app.config.AppConfig;
import com.nutritionguard.app.db.Database;
import com.nutritionguard.app.repository.UserRepository;
import com.nutritionguard.app.service.AuthService;
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
    AuthService service = new AuthService(new UserRepository(db), new JwtService(vertx, config));

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
  }
}
