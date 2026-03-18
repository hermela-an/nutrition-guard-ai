package com.nutritionguard.app.util;

import io.vertx.core.json.JsonObject;

public final class TrafficLightUtil {
  private TrafficLightUtil() {}

  public static String evaluate(JsonObject health, JsonObject food) {
    boolean diabetes = health.getBoolean("diabetes", false);
    boolean highBp = health.getBoolean("high_bp", false);
    boolean highCholesterol = health.getBoolean("high_cholesterol", false);
    boolean heartDisease = health.getBoolean("heart_disease", false);
    boolean kidneyDisease = health.getBoolean("kidney_disease", false);

    double sugar = food.getDouble("sugar_g", 0.0);
    double sodium = food.getDouble("sodium_mg", 0.0);
    double cholesterol = food.getDouble("cholesterol_mg", 0.0);

    if ((diabetes && sugar > 18) || ((highBp || heartDisease || kidneyDisease) && sodium > 600) || ((highCholesterol || heartDisease) && cholesterol > 60)) {
      return "RED";
    }
    if ((diabetes && sugar > 10) || ((highBp || heartDisease || kidneyDisease) && sodium > 350) || ((highCholesterol || heartDisease) && cholesterol > 30)) {
      return "YELLOW";
    }
    return "GREEN";
  }
}
