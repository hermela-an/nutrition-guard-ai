package com.nutritionguard.app.service;

import com.nutritionguard.app.repository.FoodRepository;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import com.nutritionguard.app.config.AppConfig;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class VitaminChatServiceTest {

  private static final String FALLBACK_IMAGE_URL =
    "https://images.unsplash.com/photo-1490645935967-10de6ba17061?auto=format&fit=crop&w=400&q=80";

  private static final class StubFoodRepository extends FoodRepository {
    private JsonArray nextSearchResults = new JsonArray();
    private String lastQuery;

    private StubFoodRepository() {
      super(null);
    }

    @Override
    public Future<JsonArray> search(String query) {
      lastQuery = query;
      return Future.succeededFuture(nextSearchResults);
    }

    @Override
    public Future<JsonArray> vitaminLeaderboard(String vitaminKey, int limit) {
      return Future.succeededFuture(new JsonArray());
    }
  }

  private static AppConfig withGeminiKey(AppConfig base, String key) {
    return new AppConfig(
      base.httpPort(),
      base.dbHost(),
      base.dbPort(),
      base.dbName(),
      base.dbSchema(),
      base.dbUser(),
      base.dbPassword(),
      base.jwtSecret(),
      base.adminEmails(),
      base.frontendBaseUrl(),
      base.passwordResetTtlMinutes(),
      base.smtpHost(),
      base.smtpPort(),
      base.smtpStartTls(),
      base.smtpUsername(),
      base.smtpPassword(),
      base.smtpFrom(),
      key
    );
  }

  @Test
  void chickenDoesNotTriggerGreeting_andReturnsImageEvenWhenNoDbMatch() {
    StubFoodRepository repo = new StubFoodRepository();
    repo.nextSearchResults = new JsonArray(); // no matches

    AppConfig config = withGeminiKey(AppConfig.fromEnv(), "test-key");
    Vertx vertx = Vertx.vertx();
    VitaminChatService service = new VitaminChatService(
      vertx,
      config,
      repo,
      prompt -> Future.succeededFuture("AI fallback answer")
    );
    JsonObject res = service.ask(new JsonObject().put("question", "chicken")).result();
    vertx.close();

    assertEquals("chicken", repo.lastQuery);
    assertNotNull(res);
    assertNotNull(res.getString("answer"));
    assertNotNull(res.getString("definition"));
    assertFalse(res.getString("answer").startsWith("Hello! I am your NutriGuard AI."));
    assertEquals(FALLBACK_IMAGE_URL, res.getString("imageUrl"));
  }

  @Test
  void caloriesQuestionExtractsFoodQuery_andReturnsDbFacts() {
    StubFoodRepository repo = new StubFoodRepository();
    repo.nextSearchResults = new JsonArray().add(new JsonObject()
      .put("food_name", "Chicken breast")
      .put("calories", 165)
      .put("protein_g", 31.0)
      .put("carbs_g", 0.0)
      .put("fat_g", 3.6)
      .put("portion_amount", 100.0)
      .put("portion_unit", "g")
      .put("vitamins", new JsonObject().put("vitamin_b3", "13.7 mg"))
    );

    AppConfig config = withGeminiKey(AppConfig.fromEnv(), "test-key");
    Vertx vertx = Vertx.vertx();
    try {
      VitaminChatService service = new VitaminChatService(vertx, config, repo);
      JsonObject res = service.ask(new JsonObject().put("question", "how many calories in chicken?")).result();

      assertEquals("chicken", repo.lastQuery);
      assertEquals(165, res.getInteger("calories"));
      assertEquals(31.0, res.getDouble("protein_g"));
      assertTrue(res.getString("answer").contains("Calories: 165"));
      assertTrue(res.getString("answer").contains("Protein"));
    } finally {
      vertx.close();
    }
  }

  @Test
  void dbFirstDoesNotCallAiForNutritionFactsQuestion_evenWhenKeyPresent() {
    StubFoodRepository repo = new StubFoodRepository();
    repo.nextSearchResults = new JsonArray().add(new JsonObject()
      .put("food_name", "Chicken")
      .put("calories", 165)
      .put("protein_g", 31.0)
      .put("carbs_g", 0.0)
      .put("fat_g", 3.6)
    );

    AtomicBoolean aiCalled = new AtomicBoolean(false);
    AppConfig config = withGeminiKey(AppConfig.fromEnv(), "test-key");
    Vertx vertx = Vertx.vertx();
    try {
      VitaminChatService service = new VitaminChatService(
        vertx,
        config,
        repo,
        prompt -> {
          aiCalled.set(true);
          return Future.succeededFuture("AI");
        }
      );

      JsonObject res = service.ask(new JsonObject().put("question", "protein in chicken")).result();
      assertEquals("chicken", repo.lastQuery);
      assertFalse(aiCalled.get());
      assertFalse(res.getString("answer").contains("AI insight"));
      assertEquals(165, res.getInteger("calories"));
    } finally {
      vertx.close();
    }
  }

  @Test
  void dbFirstCanAppendAiInsight_whenUserAsksForExplanation() {
    StubFoodRepository repo = new StubFoodRepository();
    repo.nextSearchResults = new JsonArray().add(new JsonObject()
      .put("food_name", "Chicken")
      .put("calories", 165)
      .put("protein_g", 31.0)
      .put("carbs_g", 0.0)
      .put("fat_g", 3.6)
    );

    AppConfig config = withGeminiKey(AppConfig.fromEnv(), "test-key");
    Vertx vertx = Vertx.vertx();
    try {
      VitaminChatService service = new VitaminChatService(
        vertx,
        config,
        repo,
        prompt -> Future.succeededFuture("Some AI advice.")
      );

      JsonObject res = service.ask(new JsonObject().put("question", "explain chicken")).result();
      assertEquals("chicken", repo.lastQuery);
      assertTrue(res.getString("answer").contains("AI insight"));
      assertTrue(res.getString("answer").contains("Some AI advice."));
      assertTrue(res.getString("answer").contains("Calories: 165"));
    } finally {
      vertx.close();
    }
  }

  @Test
  void aiInsightAllowsBasicHtmlFormattingTags() {
    StubFoodRepository repo = new StubFoodRepository();
    repo.nextSearchResults = new JsonArray().add(new JsonObject()
      .put("food_name", "Chicken")
      .put("calories", 165)
      .put("protein_g", 31.0)
    );

    AppConfig config = withGeminiKey(AppConfig.fromEnv(), "test-key");
    Vertx vertx = Vertx.vertx();
    try {
      VitaminChatService service = new VitaminChatService(
        vertx,
        config,
        repo,
        prompt -> Future.succeededFuture("<p><strong>Tip</strong>: Try baking.</p>")
      );

      JsonObject res = service.ask(new JsonObject().put("question", "explain chicken")).result();
      assertTrue(res.getString("answer").contains("<p>"));
      assertTrue(res.getString("answer").contains("<strong>Tip</strong>"));
      assertFalse(res.getString("answer").contains("&lt;p&gt;"));
    } finally {
      vertx.close();
    }
  }

  @Test
  void cookingQuestionWithoutGeminiKey_returnsHelpfulCookingSteps() {
    StubFoodRepository repo = new StubFoodRepository();
    repo.nextSearchResults = new JsonArray(); // no matches

    AppConfig config = withGeminiKey(AppConfig.fromEnv(), "");
    Vertx vertx = Vertx.vertx();
    try {
      VitaminChatService service = new VitaminChatService(vertx, config, repo);
      JsonObject res = service.ask(new JsonObject().put("question", "how to cook spianch?")).result();

      assertEquals("spianch", repo.lastQuery);
      assertNotNull(res);
      assertTrue(res.getString("answer").toLowerCase().contains("how to cook"));
      assertTrue(res.getString("answer").contains("<ul>"));
      assertEquals(FALLBACK_IMAGE_URL, res.getString("imageUrl"));
    } finally {
      vertx.close();
    }
  }

  @Test
  void hiTriggersGreeting_andDoesNotHitSearch() {
    StubFoodRepository repo = new StubFoodRepository();
    AppConfig config = withGeminiKey(AppConfig.fromEnv(), "");
    Vertx vertx = Vertx.vertx();
    VitaminChatService service = new VitaminChatService(vertx, config, repo);

    JsonObject res = service.ask(new JsonObject().put("question", "hi")).result();
    vertx.close();

    assertNull(repo.lastQuery);
    assertEquals(
      "Hello! I am your NutriGuard AI. I can help you find vitamin information, suggest healthy meals, or track your progress!",
      res.getString("answer")
    );
    assertEquals(FALLBACK_IMAGE_URL, res.getString("imageUrl"));
    assertNotNull(res.getString("definition"));
  }

  @Test
  void wordContainingHiDoesNotTriggerGreeting() {
    StubFoodRepository repo = new StubFoodRepository();
    repo.nextSearchResults = new JsonArray(); // no matches

    AppConfig config = withGeminiKey(AppConfig.fromEnv(), "");
    Vertx vertx = Vertx.vertx();
    VitaminChatService service = new VitaminChatService(vertx, config, repo);
    JsonObject res = service.ask(new JsonObject().put("question", "this is chicken")).result();
    vertx.close();

    assertEquals("chicken", repo.lastQuery);
    assertEquals(FALLBACK_IMAGE_URL, res.getString("imageUrl"));
    assertNotNull(res.getString("definition"));
    assertFalse(res.getString("answer").startsWith("Hello! I am your NutriGuard AI."));
  }
}
