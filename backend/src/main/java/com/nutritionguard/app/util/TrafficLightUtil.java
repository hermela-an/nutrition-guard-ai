package com.nutritionguard.app.util;

import io.vertx.core.json.JsonObject;

public final class TrafficLightUtil {
  private TrafficLightUtil() {}

  public static String evaluate(JsonObject health, JsonObject food) {
    boolean diabetes = health.getBoolean("diabetes", false);
    boolean highBp = health.getBoolean("high_bp", false);
    boolean highCholesterol = health.getBoolean("high_cholesterol", false);

    double sugar = food.getDouble("sugar_g", 0.0);
    double sodium = food.getDouble("sodium_mg", 0.0);
    double cholesterol = food.getDouble("cholesterol_mg", 0.0);

    if ((diabetes && sugar > 18) || (highBp && sodium > 600) || (highCholesterol && cholesterol > 60)) {
      return "RED";
    }
    if ((diabetes && sugar > 10) || (highBp && sodium > 350) || (highCholesterol && cholesterol > 30)) {
      return "YELLOW";
    }
    return "GREEN";
  }
}
