package com.nutritionguard.app.service;

import com.nutritionguard.app.repository.FoodRepository;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

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

  @Test
  void chickenDoesNotTriggerGreeting_andReturnsImageEvenWhenNoDbMatch() {
    StubFoodRepository repo = new StubFoodRepository();
    repo.nextSearchResults = new JsonArray(); // no matches

    VitaminChatService service = new VitaminChatService(repo);
    JsonObject res = service.ask(new JsonObject().put("question", "chicken")).result();

    assertEquals("chicken", repo.lastQuery);
    assertNotNull(res);
    assertNotNull(res.getString("answer"));
    assertNotNull(res.getString("definition"));
    assertFalse(res.getString("answer").startsWith("Hello! I am your NutriGuard AI."));
    assertEquals(FALLBACK_IMAGE_URL, res.getString("imageUrl"));
  }

  @Test
  void hiTriggersGreeting_andDoesNotHitSearch() {
    StubFoodRepository repo = new StubFoodRepository();
    VitaminChatService service = new VitaminChatService(repo);

    JsonObject res = service.ask(new JsonObject().put("question", "hi")).result();

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

    VitaminChatService service = new VitaminChatService(repo);
    JsonObject res = service.ask(new JsonObject().put("question", "this is chicken")).result();

    assertEquals("chicken", repo.lastQuery);
    assertEquals(FALLBACK_IMAGE_URL, res.getString("imageUrl"));
    assertNotNull(res.getString("definition"));
    assertFalse(res.getString("answer").startsWith("Hello! I am your NutriGuard AI."));
  }
}
