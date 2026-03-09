package com.nutritionguard.app.verticle;

import com.nutritionguard.app.config.AppConfig;
import com.nutritionguard.app.controller.AuthController;
import com.nutritionguard.app.controller.CalculationController;
import com.nutritionguard.app.controller.ChatController;
import com.nutritionguard.app.controller.FoodSearchController;
import com.nutritionguard.app.controller.MealPlanController;
import com.nutritionguard.app.controller.ProfileController;
import com.nutritionguard.app.controller.ProgressController;
import com.nutritionguard.app.db.Database;
import com.nutritionguard.app.repository.FoodRepository;
import com.nutritionguard.app.repository.ProfileRepository;
import com.nutritionguard.app.repository.ProgressRepository;
import com.nutritionguard.app.routes.AuthMiddleware;
import com.nutritionguard.app.service.FoodSearchService;
import com.nutritionguard.app.service.ProfileService;
import com.nutritionguard.app.service.ProgressService;
import com.nutritionguard.app.util.EventAddresses;
import com.nutritionguard.app.util.JwtService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;

import java.util.Set;

public class AppVerticle extends AbstractVerticle {
  private final Database db;
  private final AppConfig config;

  public AppVerticle(Database db, AppConfig config) {
    this.db = db;
    this.config = config;
  }

  @Override
  public void start() {
    Router router = Router.router(vertx);

    router.route().handler(CorsHandler.create("*")
      .allowedMethods(Set.of(io.vertx.core.http.HttpMethod.GET, io.vertx.core.http.HttpMethod.POST, io.vertx.core.http.HttpMethod.PUT))
      .allowedHeader("Content-Type")
      .allowedHeader("Authorization"));
    router.route().handler(BodyHandler.create());

    ProfileService profileService = new ProfileService(new ProfileRepository(db));
    FoodSearchService foodSearchService = new FoodSearchService(new FoodRepository(db));
    ProgressService progressService = new ProgressService(new ProgressRepository(db));

    new AuthController(vertx).mount(router);
    new CalculationController(vertx).mount(router);

    AuthMiddleware auth = new AuthMiddleware(new JwtService(vertx, config));
    router.route("/api/profile*").handler(auth::handle);
    router.route("/api/meal-plan*").handler(auth::handle);
    router.route("/api/foods*").handler(auth::handle);
    router.route("/api/progress*").handler(auth::handle);
    router.route("/api/chat*").handler(auth::handle);

    new ProfileController(profileService).mount(router);
    new MealPlanController(vertx, profileService).mount(router);
    new FoodSearchController(foodSearchService, profileService).mount(router);
    new ProgressController(progressService).mount(router);
    new ChatController(vertx).mount(router);

    router.get("/health").handler(ctx -> ctx.end("OK"));

    io.vertx.core.http.HttpServer server = vertx.createHttpServer();
    server.webSocketHandler(ws -> {
      if (!"/api/chat/ws".equals(ws.path())) {
        ws.reject();
        return;
      }
      ws.textMessageHandler(text -> {
        JsonObject payload = new JsonObject(text);
        vertx.eventBus().<JsonObject>request(EventAddresses.CHAT_ASK, payload)
          .onSuccess(msg -> ws.writeTextMessage(msg.body().encode()))
          .onFailure(err -> ws.writeTextMessage(new JsonObject().put("error", err.getMessage()).encode()));
      });
    });

    server.requestHandler(router).listen(config.httpPort())
      .onSuccess(http -> System.out.println("HTTP listening on " + http.actualPort()))
      .onFailure(err -> System.err.println("HTTP startup error: " + err.getMessage()));
  }
}
