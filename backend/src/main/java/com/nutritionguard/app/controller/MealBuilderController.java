package com.nutritionguard.app.controller;

import com.nutritionguard.app.service.MealComposerService;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

public class MealBuilderController {
  private final MealComposerService meals;

  public MealBuilderController(MealComposerService meals) {
    this.meals = meals;
  }

  public void mount(Router router) {
    router.post("/api/meals").handler(ctx -> {
      JsonObject body = ctx.body() == null ? null : ctx.body().asJsonObject();
      if (body == null) {
        ctx.response().setStatusCode(400).end("Invalid JSON body");
        return;
      }

      String name = body.getString("name", body.getString("food_name", ""));
      String mealType = body.getString("meal_type", body.getString("mealType", "custom"));
      JsonArray components = componentsFrom(body);

      meals.createMealAndCalculate(name, mealType, components)
        .onSuccess(res -> ctx.response().putHeader("Content-Type", "application/json").end(res.encode()))
        .onFailure(err -> ctx.response().setStatusCode(400).end(err.getMessage()));
    });

    router.get("/api/meals/:id").handler(ctx -> {
      int id;
      try {
        id = Integer.parseInt(ctx.pathParam("id"));
      } catch (NumberFormatException ignored) {
        ctx.response().setStatusCode(400).end("Invalid meal id");
        return;
      }

      meals.getMealWithNutrition(id)
        .onSuccess(res -> ctx.response().putHeader("Content-Type", "application/json").end(res.encode()))
        .onFailure(err -> {
          String msg = err.getMessage() == null ? "Request failed" : err.getMessage();
          int status = msg.equalsIgnoreCase("Meal not found") ? 404 : 400;
          ctx.response().setStatusCode(status).end(msg);
        });
    });

    router.post("/api/meals/calculate").handler(ctx -> {
      JsonObject body = ctx.body() == null ? null : ctx.body().asJsonObject();
      if (body == null) {
        ctx.response().setStatusCode(400).end("Invalid JSON body");
        return;
      }

      JsonArray components = componentsFrom(body);
      meals.calculateNutrition(components)
        .onSuccess(res -> ctx.response().putHeader("Content-Type", "application/json").end(res.encode()))
        .onFailure(err -> ctx.response().setStatusCode(400).end(err.getMessage()));
    });
  }

  private JsonArray componentsFrom(JsonObject body) {
    if (body == null) return new JsonArray();
    JsonArray components = body.getJsonArray("components_json");
    if (components == null) components = body.getJsonArray("components");
    if (components == null) components = body.getJsonArray("items");
    return components == null ? new JsonArray() : components;
  }
}

