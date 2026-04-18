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

    System.out.printf(
      "Config: APP_PORT=%d DB_HOST=%s DB_PORT=%d DB_NAME=%s DB_SCHEMA=%s DB_USER=%s ENV_FILE=%s ADMIN_EMAILS=%s SMTP_HOST=%s SMTP_PORT=%d SMTP_STARTTLS=%s SMTP_FROM=%s%n",
      config.httpPort(),
      config.dbHost(),
      config.dbPort(),
      config.dbName(),
      config.dbSchema(),
      config.dbUser(),
      AppConfig.loadedEnvFile(),
      config.adminEmails() == null || config.adminEmails().isBlank() ? "(none)" : "(configured)"
      ,
      config.smtpHost() == null || config.smtpHost().isBlank() ? "(none)" : config.smtpHost(),
      config.smtpPort(),
      config.smtpStartTls(),
      config.smtpFrom() == null || config.smtpFrom().isBlank() ? "(default)" : config.smtpFrom()
    );

    database.ping()
      .compose(ok -> vertx.deployVerticle(new AuthVerticle(database, config)))
      .compose(ok -> vertx.deployVerticle(new CalculationVerticle()))
      .compose(ok -> vertx.deployVerticle(new FilterVerticle(database)))
      .compose(ok -> vertx.deployVerticle(new SocketVerticle(database)))
      .compose(ok -> vertx.deployVerticle(new AppVerticle(database, config)))
      .onSuccess(id -> System.out.println("NutritionGuard backend started."))
      .onFailure(err -> {
        System.err.println("Startup failed: " + err.getMessage());
        vertx.close();
      });
  }
}
