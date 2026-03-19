package com.nutritionguard.app.controller;

import com.nutritionguard.app.service.NotificationService;
import com.nutritionguard.app.service.ProfileService;
import com.nutritionguard.app.repository.MealTrackingRepository;
import com.nutritionguard.app.util.EventAddresses;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

public class MealPlanController {
  private final Vertx vertx;
  private final ProfileService profiles;
  private final NotificationService notifications;
  private final MealTrackingRepository tracking;

  public MealPlanController(Vertx vertx, ProfileService profiles, NotificationService notifications, MealTrackingRepository tracking) {
    this.vertx = vertx;
    this.profiles = profiles;
    this.notifications = notifications;
    this.tracking = tracking;
  }

  public void mount(Router router) {
    router.get("/api/meal-plan/daily").handler(ctx -> {
      String userId = ctx.get("user_id");
      LocalDate today = LocalDate.now();
      profiles.getProfile(userId).compose(profile -> {
        if (profile == null) {
          return io.vertx.core.Future.failedFuture("Profile not found");
        }
        return vertx.eventBus().<io.vertx.core.json.JsonObject>request(EventAddresses.FILTER_MEAL_PLAN, profile)
          .map(msg -> msg.body());
      }).compose(plan -> tracking.saveRecommendations(userId, today, plan).map(plan))
        .compose(plan -> tracking.intakeMap(userId, today).map(map -> enrichWithIntake(plan, map)))
        .compose(plan -> tracking.progress(userId, today).map(progress -> plan.put("progress", progress)))
        .onSuccess(plan -> {
          maybeNotifyPlan(userId, plan);
          maybeNotifyUneatenReminders(userId, plan, today);
          ctx.response().putHeader("Content-Type", "application/json").end(plan.encode());
        })
        .onFailure(err -> {
          err.printStackTrace();
          int status = statusFrom(err, 400);
          ctx.response().setStatusCode(status).end(messageFrom(err));
        });
    });

    router.put("/api/meal-plan/intake").handler(ctx -> {
      String userId = ctx.get("user_id");
      JsonObject body = ctx.body().asJsonObject();
      LocalDate date = LocalDate.parse(body.getString("date", LocalDate.now().toString()));
      String mealType = body.getString("meal_type", "");
      Integer foodItemId = body.getInteger("food_item_id");
      Boolean eaten = body.getBoolean("eaten");

      if (foodItemId == null || eaten == null || mealType == null || mealType.isBlank()) {
        ctx.response().setStatusCode(400).end("Missing required fields: date, meal_type, food_item_id, eaten");
        return;
      }

      tracking.upsertIntake(userId, date, mealType, foodItemId, eaten)
        .compose(updated -> tracking.progress(userId, date).map(progress -> updated.put("progress", progress)))
        .onSuccess(updated -> ctx.response().putHeader("Content-Type", "application/json").end(updated.encode()))
        .onFailure(err -> ctx.response().setStatusCode(400).end(err.getMessage()));
    });
  }

  private JsonObject enrichWithIntake(JsonObject plan, Map<String, Boolean> intake) {
    if (plan == null) return plan;
    applyIntake(plan.getJsonArray("breakfast"), "breakfast", intake);
    applyIntake(plan.getJsonArray("lunch"), "lunch", intake);
    applyIntake(plan.getJsonArray("dinner"), "dinner", intake);
    applyIntake(plan.getJsonArray("snacks"), "snack", intake);
    applyIntake(plan.getJsonArray("snack"), "snack", intake);
    return plan;
  }

  private void applyIntake(JsonArray items, String mealType, Map<String, Boolean> intake) {
    if (items == null || items.isEmpty()) return;
    for (int i = 0; i < items.size(); i++) {
      JsonObject item = items.getJsonObject(i);
      if (item == null) continue;
      Integer id = item.getInteger("id");
      if (id == null) continue;
      boolean eaten = intake.getOrDefault(MealTrackingRepository.key(mealType, id), false);
      item.put("eaten", eaten);
    }
  }

  private void maybeNotifyPlan(String userId, JsonObject plan) {
    if (plan == null) return;

    int yellow = countByTrafficLight(plan, "YELLOW");
    int red = countByTrafficLight(plan, "RED");

    if (red <= 0 && yellow <= 0) return;

    String title = red > 0 ? "Meal plan alert" : "Meal plan caution";
    String message = red > 0
      ? "Your meal plan contains foods flagged as RED for your health profile. Consider safer alternatives."
      : "Your meal plan contains some YELLOW (caution) foods. Review portions and your health conditions.";

    notifications.create(
      userId,
      NotificationService.TYPE_ALERT,
      title,
      message,
      new JsonObject().put("red_count", red).put("yellow_count", yellow)
    ).onFailure(err -> err.printStackTrace());
  }

  private void maybeNotifyUneatenReminders(String userId, JsonObject plan, LocalDate date) {
    if (plan == null) return;
    JsonObject progress = plan.getJsonObject("progress", new JsonObject());
    int total = progress.getInteger("total", 0);
    int eaten = progress.getInteger("eaten", 0);
    if (total <= 0 || eaten >= total) return;

    LocalTime now = LocalTime.now();

    // Simple day-based reminder window: after 20:00, remind if anything is still uneaten.
    if (now.isBefore(LocalTime.of(20, 0))) return;

    String key = "reminder:uneaten:" + date;
    notifications.createOnce(
      userId,
      key,
      NotificationService.TYPE_ALERT,
      "Meal plan reminder",
      "You still have recommended foods not marked as eaten today. Update your intake to keep tracking accurate.",
      new JsonObject().put("date", date.toString()).put("total", total).put("eaten", eaten)
    ).onFailure(err -> err.printStackTrace());
  }

  private int countByTrafficLight(JsonObject plan, String level) {
    int count = 0;
    count += countInArray(plan.getJsonArray("breakfast"), level);
    count += countInArray(plan.getJsonArray("lunch"), level);
    count += countInArray(plan.getJsonArray("dinner"), level);
    count += countInArray(plan.getJsonArray("snacks"), level);
    count += countInArray(plan.getJsonArray("snack"), level);
    return count;
  }

  private int countInArray(JsonArray items, String level) {
    if (items == null || items.isEmpty()) return 0;
    int count = 0;
    for (int i = 0; i < items.size(); i++) {
      JsonObject item = items.getJsonObject(i);
      if (item != null && level.equalsIgnoreCase(item.getString("traffic_light"))) {
        count++;
      }
    }
    return count;
  }

  private int statusFrom(Throwable err, int fallback) {
    if (err instanceof ReplyException re) {
      return re.failureCode();
    }
    String msg = err.getMessage();
    if (msg != null && msg.equalsIgnoreCase("Profile not found")) {
      return 404;
    }
    return fallback;
  }

  private String messageFrom(Throwable err) {
    if (err instanceof ReplyException re) {
      String msg = re.getMessage();
      if (msg != null && !msg.isBlank()) {
        return msg;
      }
    }
    String msg = err.getMessage();
    return msg == null ? "Request failed" : msg;
  }
}
