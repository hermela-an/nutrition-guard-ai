package com.nutritionguard.app.verticle;

import com.nutritionguard.app.db.Database;
import com.nutritionguard.app.repository.FoodRepository;
import com.nutritionguard.app.service.FilterService;
import com.nutritionguard.app.util.EventAddresses;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;

public class FilterVerticle extends AbstractVerticle {
  private final Database db;

  public FilterVerticle(Database db) {
    this.db = db;
  }

  @Override
  public void start() {
    FilterService service = new FilterService(new FoodRepository(db));
    vertx.eventBus().consumer(EventAddresses.FILTER_MEAL_PLAN, msg -> {
      JsonObject profile = (JsonObject) msg.body();
      service.generateMealPlan(profile)
        .onSuccess(msg::reply)
        .onFailure(err -> msg.fail(400, err.getMessage()));
    });
  }
}
