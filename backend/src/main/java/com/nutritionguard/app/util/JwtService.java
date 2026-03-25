package com.nutritionguard.app.util;

import com.nutritionguard.app.config.AppConfig;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;

public class JwtService {
  private final JWTAuth jwtAuth;

  public JwtService(io.vertx.core.Vertx vertx, AppConfig config) {
    JWTAuthOptions options = new JWTAuthOptions()
      .addPubSecKey(new PubSecKeyOptions()
        .setAlgorithm("HS256")
        .setBuffer(config.jwtSecret()));
    this.jwtAuth = JWTAuth.create(vertx, options);
  }

  public String issueToken(String userId, String email, String role) {
    JsonObject claims = new JsonObject()
      .put("sub", userId)
      .put("email", email)
      .put("role", role == null || role.isBlank() ? "USER" : role);
    return jwtAuth.generateToken(claims, new JWTOptions().setExpiresInMinutes(60 * 24));
  }

  public io.vertx.core.Future<JsonObject> authenticate(String token) {
    return jwtAuth.authenticate(new TokenCredentials(token))
      .map(user -> {
        JsonObject attrs = user.attributes();
        JsonObject principal = user.principal();

        // Vert.x may place JWT claims in principal() and/or attributes().
        // Merge both so downstream middleware reliably sees "sub/email/role".
        JsonObject merged = new JsonObject();
        if (attrs != null && !attrs.isEmpty()) {
          merged.mergeIn(attrs, true);
        }
        if (principal != null && !principal.isEmpty()) {
          merged.mergeIn(principal, true);
        }
        return merged;
      });
  }
}
