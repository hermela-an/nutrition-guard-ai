package com.nutritionguard.app.verticle;

import com.nutritionguard.app.config.AppConfig;
import com.nutritionguard.app.controller.AuthController;
import com.nutritionguard.app.controller.CalculationController;
import com.nutritionguard.app.controller.ChatController;
import com.nutritionguard.app.controller.AdminFoodController;
import com.nutritionguard.app.controller.AdminStatsController;
import com.nutritionguard.app.controller.AdminUsersController;
import com.nutritionguard.app.controller.AdminAnalyticsController;
import com.nutritionguard.app.controller.FoodSearchController;
import com.nutritionguard.app.controller.FoodsController;
import com.nutritionguard.app.controller.MealPlanController;
import com.nutritionguard.app.controller.NotificationController;
import com.nutritionguard.app.controller.ProfileController;
import com.nutritionguard.app.controller.ProgressController;
import com.nutritionguard.app.routes.AdminMiddleware;
import com.nutritionguard.app.db.Database;
import com.nutritionguard.app.repository.FoodRepository;
import com.nutritionguard.app.repository.MealTrackingRepository;
import com.nutritionguard.app.repository.NotificationRepository;
import com.nutritionguard.app.repository.ProfileRepository;
import com.nutritionguard.app.repository.ProgressRepository;
import com.nutritionguard.app.repository.UserRepository;
import com.nutritionguard.app.repository.FoodSearchEventRepository;
import com.nutritionguard.app.routes.AuthMiddleware;
import com.nutritionguard.app.service.FoodSearchService;
import com.nutritionguard.app.service.NotificationService;
import com.nutritionguard.app.service.ProfileService;
import com.nutritionguard.app.service.ProgressService;
import com.nutritionguard.app.util.EventAddresses;
import com.nutritionguard.app.util.JwtService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;

import java.util.UUID;
import java.util.Set;

public class AppVerticle extends AbstractVerticle {
  private final Database db;
  private final AppConfig config;
  private static final String BUILD_TAG = "foodrepo-v4-adminfoods-fix";

  public AppVerticle(Database db, AppConfig config) {
    this.db = db;
    this.config = config;
  }

  @Override
  public void start() {
    Router router = Router.router(vertx);

    router.route().handler(ctx -> {
      ctx.response().putHeader("X-NG-Build", BUILD_TAG);
      ctx.next();
    });

    String frontendUrl = System.getenv("FRONTEND_BASE_URL");
    if (frontendUrl == null || frontendUrl.isBlank()) {
        frontendUrl = "http://localhost:4200"; // Local Angular fallback
    }

    CorsHandler corsHandler = CorsHandler.create()
        .addOrigin(frontendUrl)               // Production Netlify URL
        .addOrigin("http://localhost:4200")   // Keep localhost for local testing
        .allowedMethod(io.vertx.core.http.HttpMethod.GET)
        .allowedMethod(io.vertx.core.http.HttpMethod.POST)
        .allowedMethod(io.vertx.core.http.HttpMethod.PUT)
        .allowedMethod(io.vertx.core.http.HttpMethod.DELETE)
        .allowedMethod(io.vertx.core.http.HttpMethod.OPTIONS)
        .allowedHeader("Authorization")
        .allowedHeader("Content-Type")
        .allowedHeader("Accept")
        .allowCredentials(true);

    router.route().handler(corsHandler);
    router.route().handler(BodyHandler.create());

    // Surface handler exceptions (otherwise clients just see "Internal Server Error" with no context).
    // Keep the response plain-text so the Angular app can display it directly.
    router.route().failureHandler(ctx -> {
      if (ctx.response().ended()) return;
      Throwable failure = ctx.failure();
      int status = ctx.statusCode();
      if (status <= 0) status = 500;

      String requestId = UUID.randomUUID().toString();
      String method = ctx.request().method().name();
      String path = ctx.request().path();

      if (failure != null) {
        System.err.printf("REQ %s %s %s -> %d %s%n", requestId, method, path, status, failure);
        failure.printStackTrace();
      } else {
        System.err.printf("REQ %s %s %s -> %d (no failure)%n", requestId, method, path, status);
      }

      String message;
      if (failure != null && failure.getMessage() != null && !failure.getMessage().isBlank()) {
        message = failure.getMessage().trim();
      } else if (failure != null) {
        message = failure.toString();
      } else {
        message = "Request failed";
      }

      // Include requestId to correlate with server logs.
      ctx.response()
        .setStatusCode(status)
        .putHeader("Content-Type", "text/plain; charset=utf-8")
        .end(message + " (requestId=" + requestId + ")");
    });

    ProfileService profileService = new ProfileService(new ProfileRepository(db));
    FoodRepository foodRepository = new FoodRepository(db);
    FoodSearchService foodSearchService = new FoodSearchService(foodRepository);
    ProgressService progressService = new ProgressService(new ProgressRepository(db));
    NotificationService notificationService = new NotificationService(new NotificationRepository(db));
    MealTrackingRepository mealTrackingRepository = new MealTrackingRepository(db);
    UserRepository userRepository = new UserRepository(db);
    FoodSearchEventRepository searchEvents = new FoodSearchEventRepository(db);

    new AuthController(vertx).mount(router);
    new CalculationController(vertx).mount(router);

    AuthMiddleware auth = new AuthMiddleware(new JwtService(vertx, config), config.adminEmails());
    AdminMiddleware admin = new AdminMiddleware();

    router.route("/api/admin*").handler(auth::handle);
    router.route("/api/admin*").handler(admin::handle);
    router.route("/api/auth/me").handler(auth::handle);
    router.route("/api/profile*").handler(auth::handle);
    router.route("/api/meal-plan*").handler(auth::handle);
    router.route("/api/foods*").handler(auth::handle);
    router.route("/api/progress*").handler(auth::handle);
    router.route("/api/chat*").handler(auth::handle);
    router.route("/api/notifications*").handler(auth::handle);

    router.get("/api/auth/me").handler(ctx -> ctx.response()
      .putHeader("Content-Type", "application/json")
      .end(new JsonObject()
        .put("user_id", ctx.get("user_id"))
        .put("email", ctx.get("email"))
        .put("role", ctx.get("role"))
        .encode()));

    // Mount Admin controllers
    com.nutritionguard.app.service.FoodImportService foodImportService = new com.nutritionguard.app.service.FoodImportService(foodRepository);
    new AdminFoodController(foodRepository, foodImportService).mount(router);
    new AdminUsersController(userRepository).mount(router);
    new AdminStatsController(foodRepository, userRepository).mount(router);
    new AdminAnalyticsController(searchEvents).mount(router);
    
    new ProfileController(profileService, notificationService).mount(router);
    new MealPlanController(vertx, profileService, notificationService, mealTrackingRepository).mount(router);
    new FoodSearchController(foodSearchService, profileService, notificationService, searchEvents).mount(router);
    new FoodsController(foodRepository, profileService).mount(router);
    new ProgressController(progressService).mount(router);
    new ChatController(vertx, profileService, foodSearchService, notificationService).mount(router);
    new NotificationController(notificationService).mount(router);

    router.get("/health").handler(ctx -> ctx.end("OK " + BUILD_TAG));

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
