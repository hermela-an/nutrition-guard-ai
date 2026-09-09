package com.nutritionguard.app.util;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TrafficLightUtilTest {

  @Test
  void testHealthyUserCookedProteinMealIsSafe() {
    JsonObject profile = new JsonObject()
      .put("age", 28)
      .put("height_cm", 175.0)
      .put("weight_kg", 70.0) // BMI = 22.86
      .put("health_metadata", new JsonObject());

    // 520 kcal, 290mg sodium, 55mg cholesterol, 1g sugar (e.g. Fish/Salmon & Grain)
    JsonObject food = new JsonObject()
      .put("meal_type", "dinner")
      .put("calories_kcal", 520)
      .put("protein_g", 33.0)
      .put("carbs_g", 41.0)
      .put("fat_g", 19.0)
      .put("sugar_g", 1.0)
      .put("sodium_mg", 290.0)
      .put("cholesterol_mg", 55.0)
      .put("traffic_light", "SAFE");

    String result = TrafficLightUtil.evaluateWithProfile(profile, food);
    assertEquals("SAFE", result, "Wholesome protein meal with moderate cholesterol should be SAFE for healthy users");
  }

  @Test
  void testHealthyUserSavoryStewIsSafe() {
    JsonObject profile = new JsonObject()
      .put("height_cm", 175.0)
      .put("weight_kg", 70.0)
      .put("health_metadata", new JsonObject());

    // 320 kcal, 480mg sodium, 0mg cholesterol, 3g sugar (e.g. Legume stew)
    JsonObject food = new JsonObject()
      .put("meal_type", "lunch")
      .put("calories_kcal", 320)
      .put("sodium_mg", 480.0)
      .put("cholesterol_mg", 0.0)
      .put("sugar_g", 3.0)
      .put("carbs_g", 34.0)
      .put("traffic_light", "SAFE");

    String result = TrafficLightUtil.evaluateWithProfile(profile, food);
    assertEquals("SAFE", result, "Savory stew with 480mg sodium should be SAFE for healthy users under 750mg meal limit");
  }

  @Test
  void testDatabaseAvoidDoesNotDowngradeSafeEvaluationWithoutUserConflict() {
    JsonObject profile = new JsonObject()
      .put("height_cm", 170.0)
      .put("weight_kg", 65.0)
      .put("health_metadata", new JsonObject()); // Healthy user

    // Wholesome dish but database had legacy/static AVOID tag
    JsonObject food = new JsonObject()
      .put("meal_type", "breakfast")
      .put("calories_kcal", 260)
      .put("sodium_mg", 380.0)
      .put("sugar_g", 3.0)
      .put("cholesterol_mg", 0.0)
      .put("traffic_light", "AVOID"); // Static database tag

    String result = TrafficLightUtil.evaluateWithProfile(profile, food);
    assertEquals("SAFE", result, "Static database traffic_light should not downgrade a dynamic SAFE evaluation without a user conflict");
  }

  @Test
  void testHypertensionConditionDifferentiatesSodium() {
    // Healthy User
    JsonObject healthyProfile = new JsonObject()
      .put("height_cm", 175.0)
      .put("weight_kg", 70.0)
      .put("health_metadata", new JsonObject().put("high_bp", false));

    // Hypertensive User
    JsonObject bpProfile = new JsonObject()
      .put("height_cm", 175.0)
      .put("weight_kg", 70.0)
      .put("health_metadata", new JsonObject().put("high_bp", true));

    // Dish with 480mg sodium (e.g. savory stew)
    JsonObject stew = new JsonObject()
      .put("meal_type", "lunch")
      .put("calories_kcal", 320)
      .put("sodium_mg", 480.0)
      .put("cholesterol_mg", 0.0)
      .put("sugar_g", 3.0);

    assertEquals("SAFE", TrafficLightUtil.evaluateWithProfile(healthyProfile, stew));
    assertEquals("CAUTION", TrafficLightUtil.evaluateWithProfile(bpProfile, stew), "480mg sodium warrants CAUTION for high BP");

    // Very high sodium dish (850mg sodium)
    JsonObject saltyFood = new JsonObject()
      .put("meal_type", "dinner")
      .put("calories_kcal", 400)
      .put("sodium_mg", 850.0)
      .put("cholesterol_mg", 10.0)
      .put("sugar_g", 2.0);

    assertEquals("CAUTION", TrafficLightUtil.evaluateWithProfile(healthyProfile, saltyFood));
    assertEquals("AVOID", TrafficLightUtil.evaluateWithProfile(bpProfile, saltyFood), "850mg sodium warrants AVOID for high BP");
  }

  @Test
  void testHighCholesterolConditionDifferentiatesDietaryCholesterol() {
    JsonObject healthyProfile = new JsonObject()
      .put("height_cm", 175.0)
      .put("weight_kg", 70.0)
      .put("health_metadata", new JsonObject().put("high_cholesterol", false));

    JsonObject cholProfile = new JsonObject()
      .put("height_cm", 175.0)
      .put("weight_kg", 70.0)
      .put("health_metadata", new JsonObject().put("high_cholesterol", true));

    // Dish with 55mg cholesterol (e.g. fresh salmon)
    JsonObject fishDish = new JsonObject()
      .put("meal_type", "dinner")
      .put("calories_kcal", 500)
      .put("cholesterol_mg", 55.0)
      .put("sodium_mg", 250.0)
      .put("sugar_g", 1.0);

    assertEquals("SAFE", TrafficLightUtil.evaluateWithProfile(healthyProfile, fishDish));
    assertEquals("CAUTION", TrafficLightUtil.evaluateWithProfile(cholProfile, fishDish), "55mg cholesterol warrants CAUTION for dyslipidemia");

    // Dish with 95mg cholesterol
    JsonObject highCholDish = new JsonObject()
      .put("meal_type", "dinner")
      .put("calories_kcal", 450)
      .put("cholesterol_mg", 95.0)
      .put("sodium_mg", 300.0)
      .put("sugar_g", 0.0);

    assertEquals("SAFE", TrafficLightUtil.evaluateWithProfile(healthyProfile, highCholDish));
    assertEquals("AVOID", TrafficLightUtil.evaluateWithProfile(cholProfile, highCholDish), "95mg cholesterol warrants AVOID for dyslipidemia");
  }

  @Test
  void testHolisticDiabetesEvaluation() {
    JsonObject diabeticProfile = new JsonObject()
      .put("height_cm", 170.0)
      .put("weight_kg", 68.0)
      .put("health_metadata", new JsonObject().put("diabetes", true));

    // Balanced meal: 40g carbs, 3g sugar, GI 45
    JsonObject balancedDish = new JsonObject()
      .put("meal_type", "lunch")
      .put("calories_kcal", 350)
      .put("carbs_g", 40.0)
      .put("sugar_g", 3.0)
      .put("glycemic_index", 45)
      .put("sodium_mg", 300.0);
    assertEquals("SAFE", TrafficLightUtil.evaluateWithProfile(diabeticProfile, balancedDish));

    // High sugar item: 20g sugar
    JsonObject sweetItem = new JsonObject()
      .put("meal_type", "snack")
      .put("calories_kcal", 200)
      .put("carbs_g", 30.0)
      .put("sugar_g", 20.0)
      .put("sodium_mg", 50.0);
    assertEquals("AVOID", TrafficLightUtil.evaluateWithProfile(diabeticProfile, sweetItem), "High sugar (>18g) warrants AVOID for diabetes");

    // High glycemic load item: 65g carbs with GI 75
    JsonObject highGlItem = new JsonObject()
      .put("meal_type", "lunch")
      .put("calories_kcal", 450)
      .put("carbs_g", 65.0)
      .put("sugar_g", 4.0)
      .put("glycemic_index", 75)
      .put("sodium_mg", 200.0);
    assertEquals("AVOID", TrafficLightUtil.evaluateWithProfile(diabeticProfile, highGlItem), "High carbs + high GI combination warrants AVOID for diabetes");
  }

  @Test
  void testAllergenHardConflictTriggersAvoid() {
    JsonObject allergicProfile = new JsonObject()
      .put("health_metadata", new JsonObject().put("allergy", true));

    JsonObject allergenFood = new JsonObject()
      .put("meal_type", "dinner")
      .put("calories_kcal", 300)
      .put("sodium_mg", 200.0)
      .put("sugar_g", 2.0)
      .put("health_effect", new JsonObject().put("allergy", "bad"));

    assertEquals("AVOID", TrafficLightUtil.evaluateWithProfile(allergicProfile, allergenFood));
  }
}
