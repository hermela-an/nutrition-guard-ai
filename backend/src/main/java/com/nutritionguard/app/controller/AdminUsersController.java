package com.nutritionguard.app.controller;

import com.nutritionguard.app.repository.UserRepository;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

public class AdminUsersController {
  private final UserRepository users;

  public AdminUsersController(UserRepository users) {
    this.users = users;
  }

  public void mount(Router router) {
    router.get("/api/admin/users").handler(ctx -> {
      String q = ctx.request().getParam("q");
      String status = ctx.request().getParam("status");
      int limit = parseInt(ctx.request().getParam("limit"), 20, 1, 200);
      int offset = parseInt(ctx.request().getParam("offset"), 0, 0, 50_000);

      users.count(q, status)
        .compose(total -> users.list(q, status, limit, offset).map(items -> new JsonObject()
          .put("items", items)
          .put("total", total)))
        .onSuccess(payload -> ctx.response().putHeader("Content-Type", "application/json").end(payload.encode()))
        .onFailure(err -> ctx.response().setStatusCode(400).end(err.getMessage()));
    });

    router.put("/api/admin/users/:id").handler(ctx -> {
      String id = ctx.pathParam("id");
      if (id == null || id.isBlank()) {
        ctx.response().setStatusCode(400).end("Invalid id");
        return;
      }

      JsonObject body = ctx.body() == null ? null : ctx.body().asJsonObject();
      if (body == null) {
        ctx.response().setStatusCode(400).end("Invalid JSON body");
        return;
      }

      if (body.containsKey("role")) {
        String role = body.getString("role", "").trim().toUpperCase();
        if (!role.isBlank() && !("USER".equals(role) || "ADMIN".equals(role))) {
          ctx.response().setStatusCode(400).end("Invalid role (expected USER or ADMIN)");
          return;
        }
        body.put("role", role);
      }

      if (body.containsKey("is_active")) {
        Object raw = body.getValue("is_active");
        if (!(raw instanceof Boolean)) {
          ctx.response().setStatusCode(400).end("Invalid is_active (expected boolean)");
          return;
        }
      }

      users.update(id, body)
        .onSuccess(updated -> {
          if (updated == null) {
            ctx.response().setStatusCode(404).end("User not found");
            return;
          }
          ctx.response().putHeader("Content-Type", "application/json").end(updated.encode());
        })
        .onFailure(err -> ctx.response().setStatusCode(400).end(err.getMessage()));
    });

    router.delete("/api/admin/users/:id").handler(ctx -> {
      String id = ctx.pathParam("id");
      if (id == null || id.isBlank()) {
        ctx.response().setStatusCode(400).end("Invalid id");
        return;
      }

      users.delete(id)
        .onSuccess(deleted -> ctx.response().putHeader("Content-Type", "application/json")
          .end(new JsonObject().put("deleted", deleted).encode()))
        .onFailure(err -> ctx.response().setStatusCode(400).end(err.getMessage()));
    });
  }

  private int parseInt(String raw, int fallback, int min, int max) {
    if (raw == null || raw.isBlank()) return fallback;
    try {
      int v = Integer.parseInt(raw);
      if (v < min) return min;
      if (v > max) return max;
      return v;
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }
}
