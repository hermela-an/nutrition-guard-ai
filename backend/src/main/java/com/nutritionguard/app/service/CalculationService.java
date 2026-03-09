package com.nutritionguard.app.service;

import io.vertx.core.json.JsonObject;

public class CalculationService {
  public JsonObject calculateTargets(JsonObject profile) {
    int age = profile.getInteger("age");
    String gender = profile.getString("gender", "female").toLowerCase();
    double heightCm = profile.getDouble("height_cm");
    double weightKg = profile.getDouble("weight_kg");
    String activity = profile.getString("activity_level", "sedentary").toLowerCase();
    String goal = profile.getString("goal", "maintain").toLowerCase();

    double bmi = weightKg / Math.pow(heightCm / 100.0, 2);
    double bmr;
    if ("male".equals(gender)) {
      bmr = 10 * weightKg + 6.25 * heightCm - 5 * age + 5;
    } else {
      bmr = 10 * weightKg + 6.25 * heightCm - 5 * age - 161;
    }

    double activityMultiplier = switch (activity) {
      case "light" -> 1.375;
      case "moderate" -> 1.55;
      case "active" -> 1.725;
      case "extra_active" -> 1.9;
      default -> 1.2;
    };

    double tdee = bmr * activityMultiplier;
    double targetCalories = switch (goal) {
      case "weight_loss" -> tdee - 450;
      case "weight_gain" -> tdee + 350;
      default -> tdee;
    };

    return new JsonObject()
      .put("bmi", round2(bmi))
      .put("bmr", round2(bmr))
      .put("tdee", round2(tdee))
      .put("target_calories", Math.max(1200, round2(targetCalories)));
  }

  private double round2(double value) {
    return Math.round(value * 100.0) / 100.0;
  }
}
