package com.nutritionguard.app.repository;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FoodRepositoryCategoryTest {
  @Test
  void normalizeCategory_canonicalizesCommonInputs() throws Exception {
    FoodRepository repo = new FoodRepository(null);
    Method normalize = FoodRepository.class.getDeclaredMethod("normalizeCategory", String.class);
    normalize.setAccessible(true);

    assertEquals("Protein", normalize.invoke(repo, "protein"));
    assertEquals("Fruits", normalize.invoke(repo, "fruit"));
    assertEquals("Vegetables", normalize.invoke(repo, "veggies"));
    assertEquals("Dairy", normalize.invoke(repo, "dairy"));
    assertEquals("Grains", normalize.invoke(repo, "grain"));
    assertEquals("Nuts and Seeds", normalize.invoke(repo, "nuts"));
    assertEquals("Carbohydrates", normalize.invoke(repo, "carbs"));
    assertEquals("Vegetables", normalize.invoke(repo, "unknown-category"));
    assertEquals("Vegetables", normalize.invoke(repo, null));
  }
}

