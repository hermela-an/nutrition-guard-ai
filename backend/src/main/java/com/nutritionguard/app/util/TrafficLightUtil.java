package com.nutritionguard.app.util;

import io.vertx.core.json.JsonObject;

public final class TrafficLightUtil {
  private TrafficLightUtil() {}

  // =========================================================================
  // Centralized Nutritional & Clinical Threshold Constants
  // =========================================================================

  // --- General Population Meal Ceilings (Approx. 30-35% of daily budget: 2,300mg sodium, 2,000 kcal) ---
  public static final double MEAL_SODIUM_SAFE_MAX = 750.0;        // mg per main dish
  public static final double MEAL_SODIUM_CAUTION_MAX = 1050.0;    // mg per main dish
  public static final double MEAL_CHOLESTEROL_SAFE_MAX = 100.0;   // mg per main dish (wholesome proteins)
  public static final double MEAL_CHOLESTEROL_CAUTION_MAX = 180.0;// mg per main dish
  public static final double MEAL_SUGAR_SAFE_MAX = 14.0;          // g per main dish
  public static final double MEAL_SUGAR_CAUTION_MAX = 24.0;       // g per main dish

  // --- General Snack Ceilings (Approx. 10-15% of daily budget) ---
  public static final double SNACK_SODIUM_SAFE_MAX = 250.0;
  public static final double SNACK_SODIUM_CAUTION_MAX = 450.0;
  public static final double SNACK_SODIUM_AVOID_MAX = 450.0;
  public static final double SNACK_SUGAR_SAFE_MAX = 10.0;
  public static final double SNACK_SUGAR_CAUTION_MAX = 18.0;
  public static final double SNACK_SUGAR_AVOID_MAX = 18.0;
  public static final double SNACK_CALORIES_SAFE_MAX = 250.0;
  public static final double SNACK_CALORIES_CAUTION_MAX = 400.0;
  public static final double SNACK_CALORIES_AVOID_MAX = 400.0;

  // --- Clinical Condition-Specific Thresholds ---
  // 1. Cardiovascular & Renal (High BP, Heart Disease, Kidney Disease)
  public static final double CLINICAL_SODIUM_CAUTION_MAX = 450.0; // mg (restricted sodium budget)
  public static final double CLINICAL_SODIUM_AVOID_MAX = 700.0;   // mg

  // 2. Dyslipidemia (High Cholesterol, Heart Disease)
  public static final double CLINICAL_CHOLESTEROL_CAUTION_MAX = 40.0; // mg
  public static final double CLINICAL_CHOLESTEROL_AVOID_MAX = 80.0;   // mg

  // 3. Metabolic & Diabetes (Carb, Sugar, and Glycemic Impact)
  public static final double DIABETES_SUGAR_CAUTION_MAX = 8.0;     // g
  public static final double DIABETES_SUGAR_AVOID_MAX = 18.0;      // g
  public static final double DIABETES_MEAL_CARBS_CAUTION_MAX = 55.0;// g total carbs in main meal
  public static final double DIABETES_MEAL_CARBS_AVOID_MAX = 80.0;  // g total carbs in main meal
  public static final int    GI_HIGH_THRESHOLD = 70;
  public static final int    GI_MODERATE_THRESHOLD = 55;

  // 4. Weight Management & BMI (BMI >= 30, Goal = weight_loss)
  public static final double OBESITY_MEAL_CALORIES_CAUTION_MAX = 650.0; // kcal per single dish
  public static final double OBESITY_MEAL_CALORIES_AVOID_MAX = 850.0;   // kcal per single dish

  /**
   * Evaluates food safety using dynamic user-specific evaluation as the primary authority.
   * Database traffic_light and health_effect metadata do NOT downgrade a SAFE evaluation
   * unless they correspond to a real, applicable clinical conflict for that specific user.
   */
  public static String evaluate(JsonObject health, JsonObject food) {
    if (food == null) return "SAFE";

    double sugar = food.getDouble("sugar_g", 0.0);
    double carbs = food.getDouble("carbs_g", 0.0);
    double sodium = food.getDouble("sodium_mg", 0.0);
    double cholesterol = food.getDouble("cholesterol_mg", 0.0);
    double calories = food.getDouble("calories", food.getDouble("calories_kcal", 0.0));
    int gi = food.getInteger("glycemic_index", 0);
    String mealType = food.getString("meal_type", "").toLowerCase().trim();
    boolean isSnack = "snack".equals(mealType);

    // -----------------------------------------------------------------------
    // Step 1: True Hard Safety Conflicts (e.g. Confirmed Allergens)
    // -----------------------------------------------------------------------
    if (health != null && health.getBoolean("allergy", false)) {
      JsonObject effects = food.getJsonObject("health_effect", new JsonObject());
      if ("bad".equalsIgnoreCase(effects.getString("allergy", ""))) {
        return "AVOID";
      }
    }

    // -----------------------------------------------------------------------
    // Step 2: Dynamic User-Specific Clinical Evaluations (Primary Authority)
    // -----------------------------------------------------------------------
    String dynamicResult = "SAFE";

    if (health != null) {
      boolean diabetes = health.getBoolean("diabetes", false);
      boolean highBp = health.getBoolean("high_bp", false);
      boolean highCholesterol = health.getBoolean("high_cholesterol", false);
      boolean heartDisease = health.getBoolean("heart_disease", false);
      boolean kidneyDisease = health.getBoolean("kidney_disease", false);
      double bmi = health.getDouble("bmi", 0.0);
      String goal = health.getString("goal", "").toLowerCase().trim();
      boolean isWeightLoss = "weight_loss".equals(goal);

      JsonObject effects = food.getJsonObject("health_effect", new JsonObject());

      // 2a. Cardiovascular & Renal Risk (High BP, Heart Disease, Kidney Disease)
      if (highBp || heartDisease || kidneyDisease) {
        String bpEffect = effects.getString("high_bp", "");
        if (sodium > CLINICAL_SODIUM_AVOID_MAX || ("bad".equalsIgnoreCase(bpEffect) && sodium > CLINICAL_SODIUM_CAUTION_MAX)) {
          dynamicResult = worse(dynamicResult, "AVOID");
        } else if (sodium > CLINICAL_SODIUM_CAUTION_MAX || "caution".equalsIgnoreCase(bpEffect)) {
          dynamicResult = worse(dynamicResult, "CAUTION");
        }
      }

      // 2b. Dyslipidemia Risk (High Cholesterol, Heart Disease)
      if (highCholesterol || heartDisease) {
        String cholEffect = effects.getString("high_cholesterol", "");
        if (cholesterol > CLINICAL_CHOLESTEROL_AVOID_MAX || ("bad".equalsIgnoreCase(cholEffect) && cholesterol > CLINICAL_CHOLESTEROL_CAUTION_MAX)) {
          dynamicResult = worse(dynamicResult, "AVOID");
        } else if (cholesterol > CLINICAL_CHOLESTEROL_CAUTION_MAX || "caution".equalsIgnoreCase(cholEffect)) {
          dynamicResult = worse(dynamicResult, "CAUTION");
        }
      }

      // 2c. Metabolic & Diabetes Risk (Carbs + Sugar + GI + Portion evaluated holistically)
      if (diabetes) {
        String diabEffect = effects.getString("diabetes", "");
        boolean highGlycemicLoad = carbs > 60.0 && gi >= GI_HIGH_THRESHOLD;
        boolean moderateGlycemicLoad = gi >= GI_HIGH_THRESHOLD && carbs > 35.0;

        if (sugar > DIABETES_SUGAR_AVOID_MAX || carbs > DIABETES_MEAL_CARBS_AVOID_MAX || highGlycemicLoad
            || ("bad".equalsIgnoreCase(diabEffect) && (sugar > DIABETES_SUGAR_CAUTION_MAX || carbs > 45.0))) {
          dynamicResult = worse(dynamicResult, "AVOID");
        } else if (sugar > DIABETES_SUGAR_CAUTION_MAX || carbs > DIABETES_MEAL_CARBS_CAUTION_MAX || moderateGlycemicLoad
            || "caution".equalsIgnoreCase(diabEffect)) {
          dynamicResult = worse(dynamicResult, "CAUTION");
        }
      }

      // 2d. Weight Management & BMI Context
      if (bmi >= 30.0 || (bmi >= 25.0 && isWeightLoss)) {
        if (calories > OBESITY_MEAL_CALORIES_AVOID_MAX || (isSnack && calories > SNACK_CALORIES_AVOID_MAX)) {
          dynamicResult = worse(dynamicResult, "AVOID");
        } else if (calories > OBESITY_MEAL_CALORIES_CAUTION_MAX || (isSnack && calories > SNACK_CALORIES_SAFE_MAX)) {
          dynamicResult = worse(dynamicResult, "CAUTION");
        }
      }
    }

    // -----------------------------------------------------------------------
    // Step 3: General Population Safety Ceilings (Meal vs Snack context)
    // -----------------------------------------------------------------------
    String generalNutrientResult = fromNutrients(sugar, sodium, cholesterol, calories, isSnack);
    dynamicResult = worse(dynamicResult, generalNutrientResult);

    // -----------------------------------------------------------------------
    // Step 4: Metadata Non-Downgrade Safeguard
    // Dynamic user-specific evaluation is primary authority.
    // Database traffic_light or health_effect must NOT downgrade a SAFE result
    // unless they correspond to an applicable safety reason for that user.
    // -----------------------------------------------------------------------
    if ("SAFE".equals(dynamicResult)) {
      return "SAFE";
    }

    return dynamicResult;
  }

  /**
   * Centralized method to extract health metadata from a full profile and evaluate a food item.
   * Ensures consistent results across all pages.
   */
  public static String evaluateWithProfile(JsonObject profile, JsonObject food) {
    if (profile == null) return evaluate(new JsonObject(), food);

    JsonObject health = profile.getJsonObject("health_metadata", new JsonObject()).copy();
    health.put("goal", profile.getString("goal", ""));

    double height = profile.getDouble("height_cm", 0.0);
    double weight = profile.getDouble("weight_kg", 0.0);
    if (height > 0 && weight > 0) {
      double bmi = weight / Math.pow(height / 100.0, 2);
      health.put("bmi", bmi);
    }

    return evaluate(health, food);
  }

  /**
   * Evaluates general nutritional thresholds based on meal context (meal vs snack).
   */
  private static String fromNutrients(double sugar, double sodium, double cholesterol, double calories, boolean isSnack) {
    if (isSnack) {
      if (sodium > SNACK_SODIUM_AVOID_MAX || sugar > SNACK_SUGAR_AVOID_MAX || calories > SNACK_CALORIES_AVOID_MAX) {
        return "AVOID";
      }
      if (sodium > SNACK_SODIUM_SAFE_MAX || sugar > SNACK_SUGAR_SAFE_MAX || calories > SNACK_CALORIES_SAFE_MAX) {
        return "CAUTION";
      }
      return "SAFE";
    }

    // Main Meal Dishes
    if (sodium > MEAL_SODIUM_CAUTION_MAX || cholesterol > MEAL_CHOLESTEROL_CAUTION_MAX || sugar > MEAL_SUGAR_CAUTION_MAX) {
      return "AVOID";
    }
    if (sodium > MEAL_SODIUM_SAFE_MAX || cholesterol > MEAL_CHOLESTEROL_SAFE_MAX || sugar > MEAL_SUGAR_SAFE_MAX) {
      return "CAUTION";
    }
    return "SAFE";
  }

  public static String normalizeLight(String raw) {
    if (raw == null) return "SAFE";
    String normalized = raw.trim().toUpperCase();
    if ("AVOID".equals(normalized) || "RED".equals(normalized) || "DANGER".equals(normalized)) return "AVOID";
    if ("CAUTION".equals(normalized) || "YELLOW".equals(normalized) || "WARN".equals(normalized) || "WARNING".equals(normalized)) return "CAUTION";
    if ("SAFE".equals(normalized) || "GREEN".equals(normalized) || "OK".equals(normalized)) return "SAFE";
    return "SAFE";
  }

  public static String worse(String a, String b) {
    return severity(a) >= severity(b) ? a : b;
  }

  public static int severity(String light) {
    if (light == null) return 0;
    String v = light.trim().toUpperCase();
    if ("AVOID".equals(v) || "RED".equals(v)) return 2;
    if ("CAUTION".equals(v) || "YELLOW".equals(v)) return 1;
    return 0;
  }
}
