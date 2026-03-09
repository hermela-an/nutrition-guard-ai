package com.nutritionguard.app.service;

import com.nutritionguard.app.repository.UserRepository;
import com.nutritionguard.app.util.HashUtil;
import com.nutritionguard.app.util.JwtService;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class AuthService {
  private final UserRepository users;
  private final JwtService jwt;

  public AuthService(UserRepository users, JwtService jwt) {
    this.users = users;
    this.jwt = jwt;
  }

  public Future<JsonObject> register(String email, String password) {
    return users.findByEmail(email).compose(existing -> {
      if (existing != null) {
        return Future.failedFuture("Email already exists");
      }
      return users.createUser(email, HashUtil.hashPassword(password))
        .map(user -> {
          String token = jwt.issueToken(user.getString("id"), user.getString("email"));
          return new JsonObject().put("user", user).put("token", token);
        });
    });
  }

  public Future<JsonObject> login(String email, String password) {
    return users.findByEmail(email).compose(existing -> {
      if (existing == null) {
        return Future.failedFuture("Invalid credentials");
      }
      boolean ok = HashUtil.verifyPassword(password, existing.getString("password_hash"));
      if (!ok) {
        return Future.failedFuture("Invalid credentials");
      }
      String token = jwt.issueToken(existing.getString("id"), existing.getString("email"));
      JsonObject user = new JsonObject()
        .put("id", existing.getString("id"))
        .put("email", existing.getString("email"));
      return Future.succeededFuture(new JsonObject().put("user", user).put("token", token));
    });
  }
}
