package com.nutritionguard.app.controller;

import com.nutritionguard.app.repository.FoodRepository;
import com.nutritionguard.app.repository.UserRepository;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

public class AdminStatsController {
  private final FoodRepository foods;
  private final UserRepository users;

  public AdminStatsController(FoodRepository foods, UserRepository users) {
    this.foods = foods;
    this.users = users;
  }

  public void mount(Router router) {
    router.get("/api/admin/stats").handler(ctx -> {
      Future<Integer> totalUsers = users.count(null, "ALL");
      Future<Integer> totalFoods = foods.countAll();
      Future<Integer> avoidFoods = foods.countByTrafficLight("RED");
      Future<Integer> cautionFoods = foods.countByTrafficLight("YELLOW");

      CompositeFuture.all(totalUsers, totalFoods, avoidFoods, cautionFoods)
        .onSuccess(ignored -> {
          JsonObject payload = new JsonObject()
            .put("total_users", totalUsers.result())
            .put("total_foods", totalFoods.result())
            .put("avoid_foods", avoidFoods.result())
            .put("caution_foods", cautionFoods.result());
          ctx.response().putHeader("Content-Type", "application/json").end(payload.encode());
        })
        .onFailure(err -> ctx.response().setStatusCode(400).end(err.getMessage()));
    });
  }
}
