package com.nutritionguard.app.service;

import com.nutritionguard.app.repository.FoodRepository;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.List;

public class FoodImportService {
  private final FoodRepository foods;

  public FoodImportService(FoodRepository foods) {
    this.foods = foods;
  }

  /**
   * Imports a batch of food items.
   * Normalizes each item before saving it to the database.
   */
  public Future<JsonArray> importBatch(JsonArray batch) {
    if (batch == null || batch.isEmpty()) {
      return Future.succeededFuture(new JsonArray());
    }

    List<Future<JsonObject>> futures = new ArrayList<>();
    collectUpserts(batch, futures);

    if (futures.isEmpty()) {
      return Future.succeededFuture(new JsonArray());
    }

    return CompositeFuture.join(new ArrayList<>(futures)).map(cf -> {
      JsonArray results = new JsonArray();
      for (int i = 0; i < cf.size(); i++) {
        JsonObject res = cf.resultAt(i);
        if (res != null) {
          results.add(res);
        }
      }
      return results;
    });
  }

  private void collectUpserts(JsonArray array, List<Future<JsonObject>> futures) {
    for (int i = 0; i < array.size(); i++) {
      Object item = array.getValue(i);
      if (item instanceof JsonObject) {
        JsonObject normalized = normalize((JsonObject) item);
        
        // Validation: Required fields check
        if (normalized.getString("food_name", "").isBlank() || 
            normalized.getString("category", "").isBlank() ||
            normalized.getString("item_type", "").isBlank() ||
            normalized.getInteger("calories_kcal") == null) {
          // We could skip invalid items or fail the whole batch. 
          // For now, let's just skip them to be more resilient, or we can add a failed future.
          continue;
        }
        
        futures.add(foods.upsert(normalized));
      } else if (item instanceof JsonArray) {
        collectUpserts((JsonArray) item, futures);
      }
    }
  }

  /**
   * Data Mapper Layer: Maps incoming JSON fields to the correct database fields.
   * Intelligently adapts to inconsistent naming (e.g., carbs_g -> carbohydrates_g).
   */
  public JsonObject normalize(JsonObject raw) {
    JsonObject clean = new JsonObject();

    // Basic Identification
    clean.put("food_name", findString(raw, "Unnamed Imported Food", "food_name", "name", "title", "label", "item_name"));
    clean.put("category", findString(raw, "Vegetables", "category", "food_category", "group", "food_group", "type_category"));
    clean.put("meal_type", findString(raw, "custom", "meal_type", "meal", "occasion", "category_meal"));
    clean.put("food_role", findString(raw, "main", "food_role", "role", "item_role", "side_or_main"));

    // Portion Information
    clean.put("portion_amount", findDouble(raw, 1.0, "portion_amount", "portion_value", "amount", "quantity", "serving_size"));
    clean.put("portion_unit", findString(raw, "serving", "portion_unit", "unit", "measure", "serving_unit"));

    // Nutritional Facts (Mapping variations to database schema)
    clean.put("calories_kcal", findInt(raw, 0, "calories_kcal", "calories", "energy", "energy_kcal", "kcal"));
    clean.put("protein_g", findDouble(raw, 0.0, "protein_g", "protein", "proteins", "protein_content"));
    clean.put("carbs_g", findDouble(raw, 0.0, "carbs_g", "carbohydrates", "carbohydrate_g", "total_carbs", "carbs"));
    clean.put("fat_g", findDouble(raw, 0.0, "fat_g", "fat", "fats", "lipid_g", "total_fat"));
    clean.put("fiber_g", findDouble(raw, 0.0, "fiber_g", "fiber", "dietary_fiber", "fiber_content"));
    clean.put("sugar_g", findDouble(raw, 0.0, "sugar_g", "sugar", "sugars", "total_sugars", "sucrose_g"));
    clean.put("sodium_mg", findDouble(raw, 0.0, "sodium_mg", "sodium", "salt_mg", "natrium_mg"));
    clean.put("cholesterol_mg", findDouble(raw, 0.0, "cholesterol_mg", "cholesterol"));
    clean.put("glycemic_index", findInt(raw, 0, "glycemic_index", "gi", "glycemicIndex"));

    // Safety and Metadata
    clean.put("traffic_light", findString(raw, "SAFE", "traffic_light", "safety", "status", "safety_status", "light_color"));
    clean.put("ingredients", findString(raw, null, "ingredients", "composition", "content_list", "items_included"));
    clean.put("definition", findString(raw, null, "definition", "description", "short_description", "summary", "info"));
    clean.put("image_url", findString(raw, null, "image_url", "imageUrl", "image", "photo", "pic", "thumbnail"));
    clean.put("item_type", findString(raw, "food", "item_type", "type", "kind", "class"));

    // Complex Objects (JSONB fields)
    clean.put("vitamins", findJsonObject(raw, "vitamins", "vitamin_content", "micronutrients"));
    clean.put("health_effect", findJsonObject(raw, "health_effect", "health_effects", "benefits", "properties"));
    clean.put("components_json", findJsonArray(raw, "components_json", "components", "recipe_items", "sub_items"));

    return clean;
  }

  private String findString(JsonObject obj, String fallback, String... keys) {
    for (String key : keys) {
      if (obj.containsKey(key)) {
        Object val = obj.getValue(key);
        if (val instanceof String && !((String) val).isBlank()) return ((String) val).trim();
        if (val != null) return val.toString().trim();
      }
    }
    return fallback;
  }

  private Double findDouble(JsonObject obj, Double fallback, String... keys) {
    for (String key : keys) {
      if (obj.containsKey(key)) {
        Object val = obj.getValue(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val instanceof String && !((String) val).isBlank()) {
          try { return Double.parseDouble(((String) val).trim()); } catch (Exception ignored) {}
        }
      }
    }
    return fallback;
  }

  private Integer findInt(JsonObject obj, Integer fallback, String... keys) {
    for (String key : keys) {
      if (obj.containsKey(key)) {
        Object val = obj.getValue(key);
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String && !((String) val).isBlank()) {
          try { return Integer.parseInt(((String) val).trim()); } catch (Exception ignored) {}
        }
      }
    }
    return fallback;
  }

  private JsonObject findJsonObject(JsonObject obj, String... keys) {
    for (String key : keys) {
      if (obj.containsKey(key)) {
        Object val = obj.getValue(key);
        if (val instanceof JsonObject) return (JsonObject) val;
        if (val instanceof String && !((String) val).isBlank()) {
          try { return new JsonObject(((String) val).trim()); } catch (Exception ignored) {}
        }
      }
    }
    return new JsonObject();
  }

  private JsonArray findJsonArray(JsonObject obj, String... keys) {
    for (String key : keys) {
      if (obj.containsKey(key)) {
        Object val = obj.getValue(key);
        if (val instanceof JsonArray) return (JsonArray) val;
        if (val instanceof String && !((String) val).isBlank()) {
          try { return new JsonArray(((String) val).trim()); } catch (Exception ignored) {}
        }
      }
    }
    return null;
  }
}
