package com.nutritionguard.app.service;

import com.nutritionguard.app.repository.UserRepository;
import com.nutritionguard.app.util.HashUtil;
import com.nutritionguard.app.util.JwtService;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class AuthService {
  private final UserRepository users;
  private final JwtService jwt;
  private final Set<String> bootstrapAdminEmails;
  private static final int MIN_PASSWORD_LENGTH = 6;
  private static final String EMAIL_PATTERN = "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$";

  public AuthService(UserRepository users, JwtService jwt, String adminEmailsCsv) {
    this.users = users;
    this.jwt = jwt;
    this.bootstrapAdminEmails = parseAdminEmails(adminEmailsCsv);
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
      String role = isBootstrapAdminEmail(normalizedEmail) ? "ADMIN" : "USER";
      return users.createUser(normalizedEmail, HashUtil.hashPassword(password), role)
        .map(user -> {
          String token = jwt.issueToken(user.getString("id"), user.getString("email"), user.getString("role", "USER"));
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
      if (!existing.getBoolean("is_active", true)) {
        return Future.failedFuture("Account is inactive");
      }
      boolean ok = HashUtil.verifyPassword(password, existing.getString("password_hash"));
      if (!ok) {
        return Future.failedFuture("Invalid credentials");
      }

      String role = existing.getString("role", "USER");
      if (!"ADMIN".equalsIgnoreCase(role) && isBootstrapAdminEmail(normalizedEmail)) {
        return users.update(existing.getString("id"), new JsonObject().put("role", "ADMIN"))
          .map(updated -> {
            String token = jwt.issueToken(updated.getString("id"), updated.getString("email"), updated.getString("role", "ADMIN"));
            JsonObject user = new JsonObject()
              .put("id", updated.getString("id"))
              .put("email", updated.getString("email"))
              .put("role", updated.getString("role", "ADMIN"));
            return new JsonObject().put("user", user).put("token", token);
          });
      }

      String token = jwt.issueToken(existing.getString("id"), existing.getString("email"), role);
      JsonObject user = new JsonObject()
        .put("id", existing.getString("id"))
        .put("email", existing.getString("email"))
        .put("role", role);
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

  private boolean isBootstrapAdminEmail(String email) {
    if (bootstrapAdminEmails.isEmpty()) return false;
    String normalized = normalizeEmail(email);
    return bootstrapAdminEmails.contains(normalized);
  }

  private Set<String> parseAdminEmails(String raw) {
    if (raw == null || raw.isBlank()) return Set.of();
    return Arrays.stream(raw.split(","))
      .map(String::trim)
      .filter(s -> !s.isBlank())
      .map(this::normalizeEmail)
      .collect(Collectors.toSet());
  }
}
