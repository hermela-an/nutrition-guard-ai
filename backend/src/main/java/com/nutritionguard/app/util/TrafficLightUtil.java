package com.nutritionguard.app.util;

import io.vertx.core.json.JsonObject;

public final class TrafficLightUtil {
  private TrafficLightUtil() {}

  public static String evaluate(JsonObject health, JsonObject food) {
    String base = food == null ? null : food.getString("traffic_light");
    String baseNormalized = normalizeLight(base);
    if ("AVOID".equals(baseNormalized)) return "AVOID";

    String result = baseNormalized;

    double sugar = food.getDouble("sugar_g", 0.0);
    double sodium = food.getDouble("sodium_mg", 0.0);
    double cholesterol = food.getDouble("cholesterol_mg", 0.0);

    // 1) Admin-managed per-condition overrides (data-driven)
    JsonObject effects = food.getJsonObject("health_effect", new JsonObject());
    String effectLight = fromHealthEffect(health, effects);
    if (effectLight != null && !"AVOID".equals(result)) {
      result = effectLight;
    }

    // 2) Baseline analysis (automatic, nutrition-only)
    result = worse(result, fromNutrients(sugar, sodium, cholesterol));

    // 3) Personalized adjustment (user conditions + nutrition)
    result = worse(result, fromUserRules(health, sugar, sodium, cholesterol));

    return result;
  }

  private static String fromHealthEffect(JsonObject health, JsonObject effects) {
    if (health == null || effects == null) return null;

    String[] booleanConditions = new String[] {
      "high_bp",
      "low_bp",
      "diabetes",
      "high_cholesterol",
      "anemia",
      "allergy",
      "heart_disease",
      "kidney_disease"
    };

    boolean anyBad = false;
    boolean anyNeutral = false;
    boolean anyGood = false;
    int seen = 0;

    for (String key : booleanConditions) {
      if (!health.getBoolean(key, false)) continue;
      String v = effects.getString(key);
      if (v == null || v.isBlank()) continue;
      seen++;
      String normalized = v.trim().toLowerCase();
      if ("bad".equals(normalized)) anyBad = true;
      else if ("neutral".equals(normalized)) anyNeutral = true;
      else if ("good".equals(normalized)) anyGood = true;
    }

    String goal = health.getString("goal", "");
    String goalKey = goal == null ? "" : goal.trim().toLowerCase();
    if ("weight_loss".equals(goalKey) || "weight_gain".equals(goalKey)) {
      String v = effects.getString(goalKey);
      if (v != null && !v.isBlank()) {
        seen++;
        String normalized = v.trim().toLowerCase();
        if ("bad".equals(normalized)) anyBad = true;
        else if ("neutral".equals(normalized)) anyNeutral = true;
        else if ("good".equals(normalized)) anyGood = true;
      }
    }

    if (seen <= 0) return null;
    if (anyBad) return "AVOID";
    if (anyNeutral) return "CAUTION";
    if (anyGood) return "SAFE";
    return null;
  }

  private static String fromNutrients(double sugar, double sodium, double cholesterol) {
    if (sugar > 18 || sodium > 600 || cholesterol > 60) return "AVOID";
    if (sugar > 10 || sodium > 350 || cholesterol > 30) return "CAUTION";
    return "SAFE";
  }

  private static String fromUserRules(JsonObject health, double sugar, double sodium, double cholesterol) {
    if (health == null) return "SAFE";

    boolean diabetes = health.getBoolean("diabetes", false);
    boolean highBp = health.getBoolean("high_bp", false);
    boolean highCholesterol = health.getBoolean("high_cholesterol", false);
    boolean heartDisease = health.getBoolean("heart_disease", false);
    boolean kidneyDisease = health.getBoolean("kidney_disease", false);

    String worst = "SAFE";

    if (diabetes) {
      if (sugar > 18) worst = worse(worst, "AVOID");
      else if (sugar > 10) worst = worse(worst, "CAUTION");
    }
    if (highBp || heartDisease || kidneyDisease) {
      if (sodium > 600) worst = worse(worst, "AVOID");
      else if (sodium > 350) worst = worse(worst, "CAUTION");
    }
    if (highCholesterol || heartDisease) {
      if (cholesterol > 60) worst = worse(worst, "AVOID");
      else if (cholesterol > 30) worst = worse(worst, "CAUTION");
    }

    return worst;
  }

  private static String normalizeLight(String raw) {
    if (raw == null) return "SAFE";
    String normalized = raw.trim().toUpperCase();
    if ("AVOID".equals(normalized) || "RED".equals(normalized) || "DANGER".equals(normalized)) return "AVOID";
    if ("CAUTION".equals(normalized) || "YELLOW".equals(normalized) || "WARN".equals(normalized) || "WARNING".equals(normalized)) return "CAUTION";
    if ("SAFE".equals(normalized) || "GREEN".equals(normalized) || "OK".equals(normalized)) return "SAFE";
    return "SAFE";
  }

  private static String worse(String a, String b) {
    return severity(a) >= severity(b) ? a : b;
  }

  private static int severity(String light) {
    if (light == null) return 0;
    String v = light.trim().toUpperCase();
    if ("AVOID".equals(v) || "RED".equals(v)) return 2;
    if ("CAUTION".equals(v) || "YELLOW".equals(v)) return 1;
    return 0;
  }
}
