package com.nutritionguard.app.routes;

import com.nutritionguard.app.util.JwtService;
import io.vertx.ext.web.RoutingContext;

public class AuthMiddleware {
  private final JwtService jwtService;

  public AuthMiddleware(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  public void handle(RoutingContext ctx) {
    String auth = ctx.request().getHeader("Authorization");
    if (auth == null || !auth.startsWith("Bearer ")) {
      ctx.response().setStatusCode(401).end("Missing token");
      return;
    }

    String token = auth.substring("Bearer ".length()).trim();
    jwtService.authenticate(token)
      .onSuccess(claims -> {
        ctx.put("user_id", claims.getString("sub"));
        ctx.put("email", claims.getString("email"));
        ctx.next();
      })
      .onFailure(err -> ctx.response().setStatusCode(401).end("Invalid token"));
  }
}
