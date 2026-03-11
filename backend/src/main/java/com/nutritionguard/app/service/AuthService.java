package com.nutritionguard.app.service;

import com.nutritionguard.app.repository.UserRepository;
import com.nutritionguard.app.util.HashUtil;
import com.nutritionguard.app.util.JwtService;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class AuthService {
  private final UserRepository users;
  private final JwtService jwt;
  private static final int MIN_PASSWORD_LENGTH = 6;
  private static final String EMAIL_PATTERN = "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$";

  public AuthService(UserRepository users, JwtService jwt) {
    this.users = users;
    this.jwt = jwt;
  }

  public Future<JsonObject> register(String email, String password) {
    String normalizedEmail = normalizeEmail(email);
    if (!isValidEmail(normalizedEmail)) {
      return Future.failedFuture("Invalid email format");
    }
    if (!isValidPassword(password)) {
      return Future.failedFuture("Password must be at least 6 characters");
    }

    return users.findByEmail(normalizedEmail).compose(existing -> {
      if (existing != null) {
        return Future.failedFuture("Email already exists");
      }
      return users.createUser(normalizedEmail, HashUtil.hashPassword(password))
        .map(user -> {
          String token = jwt.issueToken(user.getString("id"), user.getString("email"));
          return new JsonObject().put("user", user).put("token", token);
        });
    });
  }

  public Future<JsonObject> login(String email, String password) {
    String normalizedEmail = normalizeEmail(email);
    return users.findByEmail(normalizedEmail).compose(existing -> {
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

  private String normalizeEmail(String email) {
    if (email == null) {
      return "";
    }
    return email.trim().toLowerCase();
  }

  private boolean isValidEmail(String email) {
    if (email == null || email.isBlank()) {
      return false;
    }
    return email.toUpperCase().matches(EMAIL_PATTERN);
  }

  private boolean isValidPassword(String password) {
    return password != null && password.trim().length() >= MIN_PASSWORD_LENGTH;
  }
}
