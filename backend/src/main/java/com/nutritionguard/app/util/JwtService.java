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

  public String issueToken(String userId, String email) {
    JsonObject claims = new JsonObject()
      .put("sub", userId)
      .put("email", email);
    return jwtAuth.generateToken(claims, new JWTOptions().setExpiresInMinutes(60 * 24));
  }

  public io.vertx.core.Future<JsonObject> authenticate(String token) {
    return jwtAuth.authenticate(new TokenCredentials(token))
      .map(user -> user.attributes());
  }
}
