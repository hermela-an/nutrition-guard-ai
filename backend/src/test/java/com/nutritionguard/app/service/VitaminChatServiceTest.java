package com.nutritionguard.app.service;

import com.nutritionguard.app.model.QueryUnderstandingResult;
import com.nutritionguard.app.model.RetrievalQuery;
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
      "",   // ragApiUrl
      ""    // groqApiKey
    );
  }

  @Test
  void testIsImageRequestIdentifiesImageIntents() {
    Vertx vertx = Vertx.vertx();
    try {
      VitaminChatService service = new VitaminChatService(vertx, testConfig(), new StubFoodRepository(), new RagService(vertx, ""));

      // Positive cases (Single words)
      assertTrue(service.isImageRequest("could you give me the image of Chicken Salad?"));
      assertTrue(service.isImageRequest("Show me a picture of spinach"));
      assertTrue(service.isImageRequest("Give me pictures of avocado"));
      assertTrue(service.isImageRequest("Send photo of salmon"));
      assertTrue(service.isImageRequest("Food photos for lunch"));
      assertTrue(service.isImageRequest("Show pic of oatmeal"));
      assertTrue(service.isImageRequest("pics of apples"));

      // Positive cases (Phrases)
      assertTrue(service.isImageRequest("show me chicken breast"));
      assertTrue(service.isImageRequest("what does it look like?"));
      assertTrue(service.isImageRequest("what does quinoa look like"));
      assertTrue(service.isImageRequest("i want to see greek yogurt"));
      assertTrue(service.isImageRequest("let me see the dish"));
      assertTrue(service.isImageRequest("visual representation of kale"));

      // Negative cases
      assertFalse(service.isImageRequest("How much protein in Chicken Salad?"));
      assertFalse(service.isImageRequest("What are the calories in an apple?"));
      assertFalse(service.isImageRequest("Is spinach good for iron?"));
      assertFalse(service.isImageRequest("What is a good diet for anemia?"));
      assertFalse(service.isImageRequest(""));
      assertFalse(service.isImageRequest(null));
    } finally {
      vertx.close();
    }
  }

  @Test
  void testSanitizeForLLMStripsAllImageFieldsWithoutMutatingOriginal() {
    Vertx vertx = Vertx.vertx();
    try {
      VitaminChatService service = new VitaminChatService(vertx, testConfig(), new StubFoodRepository(), new RagService(vertx, ""));

      JsonObject rawFood = new JsonObject()
          .put("food_name", "Chicken Salad")
          .put("calories", 280)
          .put("protein_g", 25.0)
          .put("image_url", "data:image/jpeg;base64,/9j/4AAQSkZJRg...")
          .put("imageUrl", "data:image/jpeg;base64,/9j/4AAQSkZJRg...")
          .put("image", "https://example.com/img.jpg")
          .put("image_data", "rawbytes");

      JsonObject sanitized = service.sanitizeForLLM(rawFood);

      // Verify sanitized has nutritional data
      assertEquals("Chicken Salad", sanitized.getString("food_name"));
      assertEquals(280, sanitized.getInteger("calories"));
      assertEquals(25.0, sanitized.getDouble("protein_g"));

      // Verify all image keys removed
      assertNull(sanitized.getString("image_url"));
      assertNull(sanitized.getString("imageUrl"));
      assertNull(sanitized.getString("image"));
      assertNull(sanitized.getString("image_data"));

      // Verify original object was not mutated
      assertTrue(rawFood.containsKey("image_url"));
      assertTrue(rawFood.containsKey("imageUrl"));
      assertTrue(rawFood.containsKey("image"));
      assertTrue(rawFood.containsKey("image_data"));
    } finally {
      vertx.close();
    }
  }

  @Test
  void testResolvePrimaryFoodMatchesExactAndAvoidsMismatches() {
    Vertx vertx = Vertx.vertx();
    try {
      VitaminChatService service = new VitaminChatService(vertx, testConfig(), new StubFoodRepository(), new RagService(vertx, ""));

      JsonObject chickenSalad = new JsonObject().put("food_name", "Chicken Salad");
      JsonObject fruitSalad = new JsonObject().put("food_name", "Fruit Salad");
      JsonObject greekSalad = new JsonObject().put("food_name", "Greek Salad");

      JsonArray items = new JsonArray().add(chickenSalad).add(fruitSalad).add(greekSalad);

      // Exact food match found in question
      JsonObject match = service.resolvePrimaryFood(items, "could you give me the image of Chicken Salad?");
      assertNotNull(match);
      assertEquals("Chicken Salad", match.getString("food_name"));

      JsonObject matchGreek = service.resolvePrimaryFood(items, "show me Greek Salad please");
      assertNotNull(matchGreek);
      assertEquals("Greek Salad", matchGreek.getString("food_name"));

      // Ambiguous query with multiple candidates but no confident exact match -> returns null
      JsonObject noMatch = service.resolvePrimaryFood(items, "show me an image of food");
      assertNull(noMatch);

      // Single item list -> returns the single item
      JsonArray single = new JsonArray().add(chickenSalad);
      assertEquals("Chicken Salad", service.resolvePrimaryFood(single, "show me an image").getString("food_name"));
    } finally {
      vertx.close();
    }
  }

  @Test
  void testImageRequestReturnsImageInImageUrlNotInAnswer() {
    JsonObject chickenSalad = new JsonObject()
        .put("food_name", "Chicken Salad")
        .put("calories", 280)
        .put("image_url", "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD...");

    JsonArray localItems = new JsonArray().add(chickenSalad);
    JsonObject masterPackage = new JsonObject()
        .put("local", localItems)
        .put("usda", new JsonObject())
        .put("rag", "");

    QueryUnderstandingResult mockUnderstanding = QueryUnderstandingResult.minimal("Show me a picture of Chicken Salad");
    RetrievalQuery rq = new RetrievalQuery("Show me a picture of Chicken Salad", mockUnderstanding);

    Vertx vertx = Vertx.vertx();
    try {
      VitaminChatService service = new VitaminChatService(
          vertx,
          testConfig(),
          new StubFoodRepository(),
          new RagService(vertx, ""),
          new QueryUnderstandingService(testConfig(), null),
          null,
          (sys, prompt) -> Future.succeededFuture("Chicken salad is a protein-rich dish made with cooked chicken and dressing.")
      );

      JsonObject result = service.synthesizeFinal(rq, masterPackage, "").result();

      assertNotNull(result);

      // Verify: imageUrl contains the base64 image
      String imageUrl = result.getString("imageUrl");
      assertNotNull(imageUrl, "imageUrl should not be null");
      assertTrue(imageUrl.startsWith("data:image/"), "imageUrl should start with data:image/");

      // Verify: answer does NOT contain base64 data
      String answer = result.getString("answer");
      assertNotNull(answer);
      assertFalse(answer.contains("data:image/"), "answer should not contain data:image/");
      assertFalse(answer.contains("base64"), "answer should not contain base64");
      assertFalse(answer.contains("/9j/"), "answer should not contain /9j/");
    } finally {
      vertx.close();
    }
  }

  @Test
  void testNonImageRequestDoesNotReturnImage() {
    JsonObject chickenSalad = new JsonObject()
        .put("food_name", "Chicken Salad")
        .put("calories", 280)
        .put("image_url", "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD...");

    JsonArray localItems = new JsonArray().add(chickenSalad);
    JsonObject masterPackage = new JsonObject()
        .put("local", localItems)
        .put("usda", new JsonObject())
        .put("rag", "");

    QueryUnderstandingResult mockUnderstanding = QueryUnderstandingResult.minimal("How much protein in Chicken Salad?");
    RetrievalQuery rq = new RetrievalQuery("How much protein in Chicken Salad?", mockUnderstanding);

    Vertx vertx = Vertx.vertx();
    try {
      VitaminChatService service = new VitaminChatService(
          vertx,
          testConfig(),
          new StubFoodRepository(),
          new RagService(vertx, ""),
          new QueryUnderstandingService(testConfig(), null),
          null,
          (sys, prompt) -> Future.succeededFuture("Chicken salad contains approximately 25g of protein per serving.")
      );

      JsonObject result = service.synthesizeFinal(rq, masterPackage, "").result();

      assertNotNull(result);

      // Verify: imageUrl is empty/null for non-image queries (preserving existing behavior)
      String imageUrl = result.getString("imageUrl");
      assertTrue(imageUrl == null || imageUrl.isBlank(), "imageUrl should be empty for non-image request");

      // Verify: answer has clean text
      String answer = result.getString("answer");
      assertNotNull(answer);
      assertTrue(answer.contains("25g of protein"));
    } finally {
      vertx.close();
    }
  }

  @Test
  void testDefenseInDepthStripsAccidentalBase64FromAnswer() {
    JsonObject masterPackage = new JsonObject()
        .put("local", new JsonArray())
        .put("usda", new JsonObject())
        .put("rag", "");

    QueryUnderstandingResult mockUnderstanding = QueryUnderstandingResult.minimal("What is this?");
    RetrievalQuery rq = new RetrievalQuery("What is this?", mockUnderstanding);

    Vertx vertx = Vertx.vertx();
    try {
      // Simulate an AI that hallucinates a raw data URI
      VitaminChatService service = new VitaminChatService(
          vertx,
          testConfig(),
          new StubFoodRepository(),
          new RagService(vertx, ""),
          new QueryUnderstandingService(testConfig(), null),
          null,
          (sys, prompt) -> Future.succeededFuture("Here is the food data:image/jpeg;base64,/9j/4AAQSkZJRg== and image_url: http://example.com/bad")
      );

      JsonObject result = service.synthesizeFinal(rq, masterPackage, "").result();
      assertNotNull(result);

      String answer = result.getString("answer");
      assertFalse(answer.contains("data:image/jpeg;base64"));
      assertTrue(answer.contains("[Image data hidden]"));
    } finally {
      vertx.close();
    }
  }

  @Test
  void hiTriggersGreeting_andDoesNotHitSearch() {
    StubFoodRepository repo = new StubFoodRepository();
    Vertx vertx = Vertx.vertx();
    try {
      VitaminChatService service = new VitaminChatService(vertx, testConfig(), repo, new RagService(vertx, ""));

      JsonObject res = service.ask(new JsonObject().put("question", "hi")).result();

      assertNull(repo.lastQuery);
      assertEquals(
        "Hello! I am your NutriGuard AI. I can help you find vitamin information, suggest healthy meals, or track your progress!",
        res.getString("answer")
      );
      assertEquals("", res.getString("imageUrl"));
      assertNotNull(res.getString("definition"));
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
