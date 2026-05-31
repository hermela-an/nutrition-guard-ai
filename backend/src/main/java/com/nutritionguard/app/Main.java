package com.nutritionguard.app;

import com.nutritionguard.app.config.AppConfig;
import com.nutritionguard.app.db.Database;
import com.nutritionguard.app.verticle.AppVerticle;
import com.nutritionguard.app.verticle.AuthVerticle;
import com.nutritionguard.app.verticle.CalculationVerticle;
import com.nutritionguard.app.verticle.FilterVerticle;
import com.nutritionguard.app.verticle.SocketVerticle;
import io.vertx.core.Vertx;

public class Main {
  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    AppConfig config = AppConfig.fromEnv();
    Database database = new Database(vertx, config);

    database.ping()
      .compose(ok -> vertx.deployVerticle(new AuthVerticle(database, config)))
      .compose(ok -> vertx.deployVerticle(new CalculationVerticle()))
      .compose(ok -> vertx.deployVerticle(new FilterVerticle(database)))
      .compose(ok -> vertx.deployVerticle(new SocketVerticle(database, config)))
      .compose(ok -> vertx.deployVerticle(new AppVerticle(database, config)))
      .onSuccess(id -> System.out.println("NutritionGuard backend started."))
      .onFailure(err -> {
        System.err.println("Startup failed: " + err.getMessage());
        vertx.close();
      });
  }
}
