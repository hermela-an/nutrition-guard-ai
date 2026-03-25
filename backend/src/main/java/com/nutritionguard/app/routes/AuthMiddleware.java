package com.nutritionguard.app.routes;

import com.nutritionguard.app.util.JwtService;
import io.vertx.ext.web.RoutingContext;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class AuthMiddleware {
  private final JwtService jwtService;
  private final Set<String> adminEmails;

  public AuthMiddleware(JwtService jwtService, String adminEmailsCsv) {
    this.jwtService = jwtService;
    this.adminEmails = parseAdminEmails(adminEmailsCsv);
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
        String email = claims.getString("email");
        String role = claims.getString("role", "USER");
        role = role == null || role.isBlank() ? "USER" : role.trim();
        if (email != null && adminEmails.contains(normalizeEmail(email))) {
          role = "ADMIN";
        }
        ctx.put("email", email);
        ctx.put("role", role.toUpperCase());
        ctx.next();
      })
      .onFailure(err -> ctx.response().setStatusCode(401).end("Invalid token"));
  }

  private Set<String> parseAdminEmails(String raw) {
    if (raw == null || raw.isBlank()) return Set.of();
    return Arrays.stream(raw.split(","))
      .map(String::trim)
      .filter(s -> !s.isBlank())
      .map(this::normalizeEmail)
      .collect(Collectors.toSet());
  }

  private String normalizeEmail(String email) {
    return email == null ? "" : email.trim().toLowerCase();
  }
}
