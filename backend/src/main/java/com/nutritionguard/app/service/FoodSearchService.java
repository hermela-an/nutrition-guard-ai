package com.nutritionguard.app.service;

import com.nutritionguard.app.repository.FoodRepository;
import com.nutritionguard.app.util.TrafficLightUtil;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class FoodSearchService {
  private final FoodRepository foodRepository;

  public FoodSearchService(FoodRepository foodRepository) {
    this.foodRepository = foodRepository;
  }

  public Future<JsonArray> search(String query, JsonObject healthMetadata) {
    return foodRepository.search(query).map(items -> {
      JsonArray enriched = new JsonArray();
      for (int i = 0; i < items.size(); i++) {
        JsonObject item = items.getJsonObject(i);
        item.put("traffic_light", TrafficLightUtil.evaluate(healthMetadata, item));
        enriched.add(item);
      }
      return enriched;
    });
  }
}
