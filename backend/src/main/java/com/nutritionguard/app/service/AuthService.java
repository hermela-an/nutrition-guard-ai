package com.nutritionguard.app.service;

import com.nutritionguard.app.config.AppConfig;
import com.nutritionguard.app.repository.EmailVerificationTokenRepository;
import com.nutritionguard.app.repository.PasswordResetTokenRepository;
import com.nutritionguard.app.repository.UserRepository;
import com.nutritionguard.app.util.HashUtil;
import com.nutritionguard.app.util.JwtService;
import com.nutritionguard.app.util.TokenUtil;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class AuthService {
  private final UserRepository users;
  private final PasswordResetTokenRepository resetTokens;
  private final EmailVerificationTokenRepository verificationTokens;
  private final JwtService jwt;
  private final PasswordResetMailer resetMailer;
  private final EmailVerificationMailer verificationMailer;
  private final AppConfig config;
  private final Set<String> bootstrapAdminEmails;
  private static final int MIN_PASSWORD_LENGTH = 6;
  private static final int MIN_TOKEN_LENGTH = 20;
  private static final String EMAIL_PATTERN = "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$";

  public AuthService(
    UserRepository users,
    PasswordResetTokenRepository resetTokens,
    EmailVerificationTokenRepository verificationTokens,
    JwtService jwt,
    PasswordResetMailer resetMailer,
    EmailVerificationMailer verificationMailer,
    AppConfig config
  ) {
    this.users = users;
    this.resetTokens = resetTokens;
    this.verificationTokens = verificationTokens;
    this.jwt = jwt;
    this.resetMailer = resetMailer;
    this.verificationMailer = verificationMailer;
    this.config = config;
    this.bootstrapAdminEmails = parseAdminEmails(config.adminEmails());
  }

  public Future<JsonObject> register(String email, String password) {
    return register(email, password, null);
  }

  public Future<JsonObject> register(String email, String password, String fullName) {
    String normalizedEmail = normalizeEmail(email);
    if (!isValidEmail(normalizedEmail)) {
      return Future.failedFuture("Invalid email format");
    }
    if (!isValidPassword(password)) {
      return Future.failedFuture("Password must be at least 6 characters");
    }

    return users.findByEmail(normalizedEmail).compose(existing -> {
      if (existing != null) {
        Boolean verified = existing.getBoolean("is_verified");
        if (Boolean.FALSE.equals(verified)) {
          // Helpfully re-send a verification email if they registered but didn't verify.
          return resendVerificationInternal(existing)
            .map(ignored -> new JsonObject().put("message", "A verification email has been sent. Please check your inbox."));
        }
        return Future.failedFuture("Email already exists");
      }
      String role = isBootstrapAdminEmail(normalizedEmail) ? "ADMIN" : "USER";
      return users.createUser(normalizedEmail, HashUtil.hashPassword(password), role, false)
        .compose(user -> sendVerificationForUser(user)
          .map(ignored -> {
            JsonObject safeUser = new JsonObject()
              .put("id", user.getString("id"))
              .put("email", user.getString("email"))
              .put("role", user.getString("role", "USER"))
              .put("is_verified", user.getBoolean("is_verified", false));
            return new JsonObject()
              .put("message", "Account created. Please verify your email to activate your account.")
              .put("user", safeUser);
          })
        );
    });
  }

  public Future<JsonObject> login(String email, String password) {
    String normalizedEmail = normalizeEmail(email);
    return users.findByEmail(normalizedEmail).compose(existing -> {
      if (existing == null) {
        return Future.failedFuture("Email not registered");
      }
      if (!existing.getBoolean("is_active", true)) {
        return Future.failedFuture("Account is inactive");
      }
      if (!existing.getBoolean("is_verified", true)) {
        return Future.failedFuture("Email not verified. Please check your email or request a new verification link.");
      }
      boolean ok = HashUtil.verifyPasswordFlexible(password, existing.getString("password_hash"));
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
        .put("role", role)
        .put("is_verified", existing.getBoolean("is_verified", true));
      return Future.succeededFuture(new JsonObject().put("user", user).put("token", token));
    });
  }

  public Future<JsonObject> resendVerification(String email) {
    String normalizedEmail = normalizeEmail(email);
    JsonObject response = new JsonObject().put(
      "message",
      "If an account exists for that email, a verification link has been sent."
    );

    if (!isValidEmail(normalizedEmail)) {
      return Future.succeededFuture(response);
    }

    return users.findByEmail(normalizedEmail).compose(existing -> {
      if (existing == null || !existing.getBoolean("is_active", true)) {
        return Future.succeededFuture(response);
      }
      if (existing.getBoolean("is_verified", true)) {
        return Future.succeededFuture(new JsonObject().put("message", "Your email is already verified. You can log in."));
      }

      return resendVerificationInternal(existing)
        .recover(err -> {
          System.err.printf("Verification email send failed for %s: %s%n", maskEmail(normalizedEmail), err.getMessage());
          return Future.succeededFuture();
        })
        .map(ignored -> response);
    });
  }

  public Future<JsonObject> verifyEmail(String token) {
    if (token == null || token.isBlank() || token.trim().length() < MIN_TOKEN_LENGTH) {
      return Future.failedFuture("Invalid or expired token");
    }

    String tokenHash = TokenUtil.sha256Hex(token.trim());
    return verificationTokens.consumeAndVerify(tokenHash).compose(result -> {
      if (result == null || result.getString("userId") == null) {
        return Future.failedFuture("Invalid or expired token");
      }
      return Future.succeededFuture(new JsonObject()
        .put("message", "Email verified successfully. You can now log in.")
        .put("email", result.getString("email")));
    });
  }

  public Future<JsonObject> forgotPassword(String email) {
    String normalizedEmail = normalizeEmail(email);
    // Always respond with the same message to avoid account enumeration.
    JsonObject response = new JsonObject().put(
      "message",
      "If an account exists for that email, a password reset link has been sent."
    );

    if (!isValidEmail(normalizedEmail)) {
      return Future.succeededFuture(response);
    }

    return users.findByEmail(normalizedEmail).compose(existing -> {
      if (existing == null || !existing.getBoolean("is_active", true)) {
        return Future.succeededFuture(response);
      }

      String userId = existing.getString("id");
      if (userId == null || userId.isBlank()) {
        return Future.succeededFuture(response);
      }

      String rawToken = TokenUtil.generateUrlSafeToken(32);
      String tokenHash = TokenUtil.sha256Hex(rawToken);
      int ttl = clampTtlMinutes(config.passwordResetTtlMinutes());
      OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(ttl);

      String baseUrl = config.frontendBaseUrl() == null ? "" : config.frontendBaseUrl().trim();
      if (baseUrl.endsWith("/")) {
        baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
      }
      String resetLink = baseUrl + "/reset-password?token=" + rawToken;

      // Revoke existing tokens to reduce attack surface.
      return resetTokens.revokeAllForUser(userId)
        .compose(ignored -> resetTokens.create(userId, tokenHash, expiresAt))
        .compose(ignored -> resetMailer.sendPasswordResetEmail(normalizedEmail, resetLink, ttl))
        .recover(err -> {
          // Do not leak email delivery failures to clients.
          System.err.printf("Password reset email send failed for %s: %s%n", maskEmail(normalizedEmail), err.getMessage());
          return Future.succeededFuture();
        })
        .map(ignored -> response);
    });
  }

  public Future<JsonObject> resetPassword(String token, String newPassword) {
    if (token == null || token.isBlank() || token.trim().length() < MIN_TOKEN_LENGTH) {
      return Future.failedFuture("Invalid or expired token");
    }
    if (!isValidPassword(newPassword)) {
      return Future.failedFuture("Password must be at least 6 characters");
    }

    String tokenHash = TokenUtil.sha256Hex(token.trim());
    String newHash = HashUtil.hashPassword(newPassword);
    return resetTokens.consumeAndUpdatePasswordHash(tokenHash, newHash).compose(userId -> {
      if (userId == null) {
        return Future.failedFuture("Invalid or expired token");
      }
      return Future.succeededFuture(new JsonObject().put("message", "Password updated successfully"));
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

  private int clampTtlMinutes(int raw) {
    int ttl = raw <= 0 ? 20 : raw;
    return Math.max(5, Math.min(ttl, 60));
  }

  private Future<Void> resendVerificationInternal(JsonObject existingUser) {
    if (existingUser == null) return Future.succeededFuture();
    return sendVerificationForUser(existingUser);
  }

  private Future<Void> sendVerificationForUser(JsonObject user) {
    if (user == null) return Future.succeededFuture();
    String userId = user.getString("id");
    String email = normalizeEmail(user.getString("email"));
    if (userId == null || userId.isBlank() || !isValidEmail(email)) {
      return Future.succeededFuture();
    }

    String rawToken = TokenUtil.generateUrlSafeToken(32);
    String tokenHash = TokenUtil.sha256Hex(rawToken);
    int ttl = clampTtlMinutes(config.passwordResetTtlMinutes());
    OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(ttl);

    String baseUrl = config.frontendBaseUrl() == null ? "" : config.frontendBaseUrl().trim();
    if (baseUrl.endsWith("/")) {
      baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
    }
    String verifyLink = baseUrl + "/verify-email?token=" + rawToken;

    return verificationTokens.revokeAllForUser(userId)
      .compose(ignored -> verificationTokens.create(userId, tokenHash, expiresAt))
      .compose(ignored -> verificationMailer.sendVerificationEmail(email, verifyLink, ttl))
      .mapEmpty();
  }

  private String maskEmail(String email) {
    if (email == null) return "(null)";
    String e = email.trim();
    int at = e.indexOf('@');
    if (at <= 1) return "***";
    String local = e.substring(0, at);
    String domain = e.substring(at + 1);
    String maskedLocal = local.charAt(0) + "***" + local.charAt(local.length() - 1);
    return maskedLocal + "@" + domain;
  }
}
