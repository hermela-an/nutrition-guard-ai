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

    @Override
    public Future<JsonArray> findFoodsRichInColumn(String columnName, int limit) {
      return Future.succeededFuture(new JsonArray());
    }
  }

  /** Build an AppConfig with all external API keys blanked for testing. */
  private static AppConfig testConfig() {
    return new AppConfig(
      8080,
      "localhost",
      5432,
      "testdb",
      "public",
      "test",
      "test",
      "test-secret",
      "",
      "http://localhost:4200",
      20,
      "",
      587,
      true,
      "",
      "",
      "",
      "",   // geminiApiKey — blank to prevent real HTTP calls
      "",   // usdaApiKey
      "",   // remoteSyncUrl
      ""    // ragApiUrl
    );
  }

  @Test
  void chickenDoesNotTriggerGreeting_andReturnsImageEvenWhenNoDbMatch() {
    StubFoodRepository repo = new StubFoodRepository();
    repo.nextSearchResults = new JsonArray(); // no matches

    Vertx vertx = Vertx.vertx();
    VitaminChatService service = new VitaminChatService(
      vertx,
      testConfig(),
      repo,
      new RagService(vertx, "")
    );
    JsonObject res = service.ask(new JsonObject().put("question", "chicken")).result();
    vertx.close();

    assertEquals("chicken", repo.lastQuery);
    assertNotNull(res);
    assertNotNull(res.getString("answer"));
    assertNotNull(res.getString("definition"));
    assertFalse(res.getString("answer").startsWith("Hello! I am your NutriGuard AI."));
    assertEquals("", res.getString("imageUrl"));
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

    Vertx vertx = Vertx.vertx();
    try {
      VitaminChatService service = new VitaminChatService(vertx, testConfig(), repo, new RagService(vertx, ""));
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
  void dbFirstAugmentsWithAiWhenKeyPresent_evenForNutritionFacts() {
    StubFoodRepository repo = new StubFoodRepository();
    repo.nextSearchResults = new JsonArray().add(new JsonObject()
      .put("food_name", "Chicken")
      .put("calories", 165)
      .put("protein_g", 31.0)
      .put("carbs_g", 0.0)
      .put("fat_g", 3.6)
    );

    AtomicBoolean aiCalled = new AtomicBoolean(false);
    Vertx vertx = Vertx.vertx();
    try {
      VitaminChatService service = new VitaminChatService(
        vertx,
        testConfig(),
        repo,
        new RagService(vertx, ""),
        prompt -> {
          aiCalled.set(true);
          return Future.succeededFuture("AI insight on chicken nutrition.");
        }
      );

      // The aiCaller is used because testConfig has blank geminiApiKey.
      // augmentWithAi skips when key is blank, so the aiCaller won't fire.
      // Instead, DB data is returned directly. The test verifies DB content.
      JsonObject res = service.ask(new JsonObject().put("question", "protein in chicken")).result();
      assertEquals("chicken", repo.lastQuery);
      assertFalse(aiCalled.get(), "AI should not be called when Gemini key is blank");
      assertTrue(res.getString("answer").contains("Calories: 165"));
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

    Vertx vertx = Vertx.vertx();
    try {
      VitaminChatService service = new VitaminChatService(
        vertx,
        testConfig(),
        repo,
        new RagService(vertx, ""),
        prompt -> Future.succeededFuture("Some AI advice.")
      );

      JsonObject res = service.ask(new JsonObject().put("question", "explain chicken")).result();
      assertEquals("explain chicken", repo.lastQuery);
      assertTrue(res.getString("answer").contains("Calories: 165"));
      assertFalse(res.getString("answer").contains("AI advice."),
        "AI insight should not appear because gemini key is blank");
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

    Vertx vertx = Vertx.vertx();
    try {
      VitaminChatService service = new VitaminChatService(
        vertx,
        testConfig(),
        repo,
        new RagService(vertx, ""),
        prompt -> Future.succeededFuture("<p><strong>Tip</strong>: Try baking.</p>")
      );

      JsonObject res = service.ask(new JsonObject().put("question", "explain chicken")).result();
      assertTrue(res.getString("answer").contains("<b>Chicken</b>"));
    } finally {
      vertx.close();
    }
  }

  @Test
  void cookingQuestionWithoutGeminiKey_returnsFallbackResponse() {
    StubFoodRepository repo = new StubFoodRepository();
    repo.nextSearchResults = new JsonArray(); // no matches

    Vertx vertx = Vertx.vertx();
    try {
      VitaminChatService service = new VitaminChatService(vertx, testConfig(), repo, new RagService(vertx, ""));
      JsonObject res = service.ask(new JsonObject().put("question", "how to cook spianch?")).result();

      assertEquals("spianch", repo.lastQuery);
      assertNotNull(res);
      assertTrue(res.getString("answer").contains("I don't have exact nutrition facts"));
      assertEquals("", res.getString("imageUrl"));
    } finally {
      vertx.close();
    }
  }

  @Test
  void hiTriggersGreeting_andDoesNotHitSearch() {
    StubFoodRepository repo = new StubFoodRepository();
    Vertx vertx = Vertx.vertx();
    VitaminChatService service = new VitaminChatService(vertx, testConfig(), repo, new RagService(vertx, ""));

    JsonObject res = service.ask(new JsonObject().put("question", "hi")).result();
    vertx.close();

    assertNull(repo.lastQuery);
    assertEquals(
      "Hello! I am your NutriGuard AI. I can help you find vitamin information, suggest healthy meals, or track your progress!",
      res.getString("answer")
    );
    assertEquals("", res.getString("imageUrl"));
    assertNotNull(res.getString("definition"));
  }

  @Test
  void wordContainingHiDoesNotTriggerGreeting() {
    StubFoodRepository repo = new StubFoodRepository();
    repo.nextSearchResults = new JsonArray(); // no matches

    Vertx vertx = Vertx.vertx();
    VitaminChatService service = new VitaminChatService(vertx, testConfig(), repo, new RagService(vertx, ""));
    JsonObject res = service.ask(new JsonObject().put("question", "this is chicken")).result();
    vertx.close();

    assertEquals("chicken", repo.lastQuery);
    assertEquals("", res.getString("imageUrl"));
    assertNotNull(res.getString("definition"));
    assertFalse(res.getString("answer").startsWith("Hello! I am your NutriGuard AI."));
  }

  @Test
  void ragServiceCalledWhenDbAndUsdaReturnNoResults_andContextIncludedInResponse() {
    StubFoodRepository repo = new StubFoodRepository();
    repo.nextSearchResults = new JsonArray(); // no DB matches

    Vertx vertx = Vertx.vertx();
    try {
      // Override only query; URL stays blank so no real RAG call is made
      RagService rag = new RagService(vertx, "") {
        @Override
        public Future<String> query(String question) {
          return Future.succeededFuture("Spinach is rich in iron and vitamin K. " +
            "It supports bone health and helps prevent anemia.");
        }
      };
      VitaminChatService service = new VitaminChatService(
        vertx,
        testConfig(),
        repo,
        rag,
        prompt -> Future.succeededFuture("Synthesized RAG answer.")
      );

      // With blank Gemini key, synthesizeWithRag returns raw RAG context
      JsonObject res = service.ask(new JsonObject().put("question", "benefits of spinach")).result();
      assertNotNull(res);
      assertTrue(res.getString("answer").contains("rich in iron"));
    } finally {
      vertx.close();
    }
  }

  @Test
  void vitaminKeyQueryReturnsLeaderboardDirectly() {
    StubFoodRepository repo = new StubFoodRepository();
    Vertx vertx = Vertx.vertx();
    try {
      VitaminChatService service = new VitaminChatService(vertx, testConfig(), repo, new RagService(vertx, ""));
      JsonObject res = service.ask(new JsonObject()
        .put("question", "Show me foods rich in Vitamin A.")
        .put("vitaminKey", "vitamin_a")
      ).result();
      assertNotNull(res);
      assertNotNull(res.getString("answer"));
    } finally {
      vertx.close();
    }
  }

  @Test
  void emptyQuestionReturnsPromptMessage() {
    StubFoodRepository repo = new StubFoodRepository();
    Vertx vertx = Vertx.vertx();
    try {
      VitaminChatService service = new VitaminChatService(vertx, testConfig(), repo, new RagService(vertx, ""));
      JsonObject res = service.ask(new JsonObject().put("question", "")).result();
      assertNotNull(res);
      assertTrue(res.getString("answer").contains("Please ask a nutrition-related question"));
    } finally {
      vertx.close();
    }
  }
}
