package com.nutritionguard.app.routes;

import io.vertx.ext.web.RoutingContext;

public class AdminMiddleware {
  public void handle(RoutingContext ctx) {
    String role = ctx.get("role");
    String normalized = role == null ? "" : role.trim();
    if ("ADMIN".equalsIgnoreCase(normalized)) {
      ctx.next();
      return;
    }
    String email = ctx.get("email");
    String safeEmail = email == null ? "" : email;
    ctx.response().setStatusCode(403).end("Admin access required (role=" + (normalized.isBlank() ? "missing" : normalized) + ", email=" + safeEmail + ")");
  }
}
