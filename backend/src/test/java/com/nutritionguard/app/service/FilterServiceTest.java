package com.nutritionguard.app.service;

import com.nutritionguard.app.repository.FoodRepository;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
public class FilterServiceTest {

  private static final class StubFoodRepository extends FoodRepository {
    private JsonArray breakfastCandidates = new JsonArray();
    private JsonArray lunchCandidates = new JsonArray();
    private JsonArray dinnerCandidates = new JsonArray();
    private JsonArray snackCandidates = new JsonArray();
    private JsonArray sides = new JsonArray();

    private StubFoodRepository() {
      super(null);
    }

    @Override
    public Future<JsonArray> mealCandidates(String mealType) {
      return switch (mealType.toLowerCase()) {
        case "breakfast" -> Future.succeededFuture(breakfastCandidates);
        case "lunch" -> Future.succeededFuture(lunchCandidates);
        case "dinner" -> Future.succeededFuture(dinnerCandidates);
        case "snack" -> Future.succeededFuture(snackCandidates);
        default -> Future.succeededFuture(new JsonArray());
      };
    }

    @Override
    public Future<JsonArray> findSideFoods() {
      return Future.succeededFuture(sides);
    }
  }

  @Test
  void testDinnerDoesNotStackMultipleHeavyEntrees(VertxTestContext testContext) {
    StubFoodRepository stubRepo = new StubFoodRepository();

    // Dish A: Heavy Entree (Noodles)
    JsonObject noodles = new JsonObject()
      .put("id", 101)
      .put("food_name", "Instant Noodles")
      .put("meal_type", "dinner")
      .put("food_role", "main_food")
      .put("category", "Carbohydrates")
      .put("calories_kcal", 520)
      .put("sodium_mg", 378.0)
      .put("cholesterol_mg", 0.0)
      .put("sugar_g", 2.0);

    // Dish B: Heavy Entree (Fish & Rice)
    JsonObject salmon = new JsonObject()
      .put("id", 102)
      .put("food_name", "Rice and Salmon")
      .put("meal_type", "dinner")
      .put("food_role", "main_food")
      .put("category", "Protein")
      .put("calories_kcal", 520)
      .put("sodium_mg", 290.0)
      .put("cholesterol_mg", 55.0)
      .put("sugar_g", 1.0);

    // Dish C: Vegetable
    JsonObject spinach = new JsonObject()
      .put("id", 103)
      .put("food_name", "Spinach")
      .put("meal_type", "dinner")
      .put("food_role", "side_food")
      .put("category", "Vegetables")
      .put("calories_kcal", 23)
      .put("sodium_mg", 50.0)
      .put("cholesterol_mg", 0.0)
      .put("sugar_g", 0.5);

    stubRepo.dinnerCandidates.add(noodles).add(salmon).add(spinach);

    FilterService service = new FilterService(stubRepo);
    JsonObject profile = new JsonObject()
      .put("goal", "maintenance")
      .put("health_metadata", new JsonObject());

    service.generateMealPlan(profile).onComplete(testContext.succeeding(plan -> {
      testContext.verify(() -> {
        JsonArray dinnerItems = plan.getJsonArray("dinner");
        assertNotNull(dinnerItems);
        assertTrue(dinnerItems.size() <= 2, "Dinner should never contain more than 1 main entree + 1 vegetable");

        // Verify that noodles and salmon are NOT both included in the same dinner
        boolean hasNoodles = false;
        boolean hasSalmon = false;
        for (int i = 0; i < dinnerItems.size(); i++) {
          JsonObject item = dinnerItems.getJsonObject(i);
          if (item.getInteger("id") == 101) hasNoodles = true;
          if (item.getInteger("id") == 102) hasSalmon = true;
        }

        assertFalse(hasNoodles && hasSalmon, "Meal assembly must NEVER stack two heavy entrees in the same dinner");
        testContext.completeNow();
      });
    }));
  }

  @Test
  void testAvoidFoodsAreStrictlyExcluded(VertxTestContext testContext) {
    StubFoodRepository stubRepo = new StubFoodRepository();

    // Very high sodium dish that evaluates to AVOID
    JsonObject verySaltyDish = new JsonObject()
      .put("id", 201)
      .put("food_name", "Excessive Salt Stew")
      .put("meal_type", "lunch")
      .put("food_role", "main_food")
      .put("category", "Protein")
      .put("calories_kcal", 400)
      .put("sodium_mg", 1400.0) // > 1050mg general ceiling -> AVOID
      .put("cholesterol_mg", 0.0)
      .put("sugar_g", 2.0);

    stubRepo.lunchCandidates.add(verySaltyDish);

    FilterService service = new FilterService(stubRepo);
    JsonObject profile = new JsonObject().put("health_metadata", new JsonObject());

    service.generateMealPlan(profile).onComplete(testContext.succeeding(plan -> {
      testContext.verify(() -> {
        JsonArray lunchItems = plan.getJsonArray("lunch");
        assertNotNull(lunchItems);
        assertTrue(lunchItems.isEmpty(), "Foods evaluated as AVOID must never be added to recommended meals");
        testContext.completeNow();
      });
    }));
  }

  @Test
  void testCautionFallbackIsRestrainedToOneItem(VertxTestContext testContext) {
    StubFoodRepository stubRepo = new StubFoodRepository();

    // Multiple caution foods (elevated sodium for a hypertensive user)
    JsonObject cautionFood1 = new JsonObject()
      .put("id", 301)
      .put("food_name", "Caution Dish 1")
      .put("meal_type", "breakfast")
      .put("food_role", "main_food")
      .put("calories_kcal", 350)
      .put("sodium_mg", 500.0) // 500mg > 450mg clinical limit -> CAUTION for high BP
      .put("sugar_g", 1.0);

    JsonObject cautionFood2 = new JsonObject()
      .put("id", 302)
      .put("food_name", "Caution Dish 2")
      .put("meal_type", "breakfast")
      .put("food_role", "main_food")
      .put("calories_kcal", 400)
      .put("sodium_mg", 550.0)
      .put("sugar_g", 2.0);

    stubRepo.breakfastCandidates.add(cautionFood1).add(cautionFood2);

    FilterService service = new FilterService(stubRepo);
    JsonObject profile = new JsonObject()
      .put("health_metadata", new JsonObject().put("high_bp", true));

    service.generateMealPlan(profile).onComplete(testContext.succeeding(plan -> {
      testContext.verify(() -> {
        JsonArray breakfastItems = plan.getJsonArray("breakfast");
        assertNotNull(breakfastItems);
        // Should have at most 1 caution fallback item, not multiple
        assertEquals(1, breakfastItems.size(), "Fallback should select at most 1 single CAUTION main dish, never flood");
        assertEquals("CAUTION", breakfastItems.getJsonObject(0).getString("traffic_light"));
        testContext.completeNow();
      });
    }));
  }
}
