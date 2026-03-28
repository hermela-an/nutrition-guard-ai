package com.nutritionguard.app.controller;

import com.nutritionguard.app.repository.FoodRepository;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

public class AdminFoodController {
  private final FoodRepository foods;

  public AdminFoodController(FoodRepository foods) {
    this.foods = foods;
  }

  public void mount(Router router) {
    router.get("/api/admin/foods").handler(ctx -> {
      try {
        String q = ctx.request().getParam("q");
        int limit = parseInt(ctx.request().getParam("limit"), 50, 1, 200);
        int offset = parseInt(ctx.request().getParam("offset"), 0, 0, 10_000);
        foods.listAdmin(q, limit, offset)
          .onSuccess(list -> ctx.response().putHeader("Content-Type", "application/json").end(list.encode()))
          .onFailure(err -> ctx.fail(400, err));
      } catch (Throwable t) {
        ctx.fail(t);
      }
    });

    router.post("/api/admin/foods").handler(ctx -> {
      try {
        JsonObject body = ctx.body() == null ? null : ctx.body().asJsonObject();
        if (body == null) {
          ctx.fail(400, new RuntimeException("Invalid JSON body"));
          return;
        }
        if (body.getString("food_name", "").isBlank() || body.getString("meal_type", "").isBlank()) {
          ctx.fail(400, new RuntimeException("Missing required fields: food_name, meal_type"));
          return;
        }
        foods.create(body)
          .onSuccess(created -> ctx.response().putHeader("Content-Type", "application/json").end(created.encode()))
          .onFailure(err -> ctx.fail(400, err));
      } catch (Throwable t) {
        ctx.fail(t);
      }
    });

    router.put("/api/admin/foods/:id").handler(ctx -> {
      try {
        Integer id = parseId(ctx.pathParam("id"));
        if (id == null) {
          ctx.fail(400, new RuntimeException("Invalid id"));
          return;
        }
        JsonObject body = ctx.body() == null ? null : ctx.body().asJsonObject();
        if (body == null) {
          ctx.fail(400, new RuntimeException("Invalid JSON body"));
          return;
        }
        foods.update(id, body)
          .onSuccess(updated -> {
            if (updated == null) {
              ctx.response().setStatusCode(404).end("Food not found");
              return;
            }
            ctx.response().putHeader("Content-Type", "application/json").end(updated.encode());
          })
          .onFailure(err -> ctx.fail(400, err));
      } catch (Throwable t) {
        ctx.fail(t);
      }
    });

    router.delete("/api/admin/foods/:id").handler(ctx -> {
      try {
        Integer id = parseId(ctx.pathParam("id"));
        if (id == null) {
          ctx.fail(400, new RuntimeException("Invalid id"));
          return;
        }
        foods.delete(id)
          .onSuccess(deleted -> ctx.response().putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("deleted", deleted).encode()))
          .onFailure(err -> ctx.fail(400, err));
      } catch (Throwable t) {
        ctx.fail(t);
      }
    });
  }

  private Integer parseId(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return Integer.parseInt(raw);
    } catch (NumberFormatException ignored) {
      return null;
    }
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
