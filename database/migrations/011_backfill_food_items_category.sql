-- Classify existing foods into high-level categories based on food_name and macros.
-- Categories:
--   Fruits, Vegetables, Grains, Protein, Dairy
-- Safe to run multiple times (only fills missing or invalid categories; never leaves `general`).

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'food_items'
      AND column_name = 'category'
  ) THEN
    -- Normalize existing values (blanks/`general`/invalid -> NULL; allowed -> canonical casing).
    UPDATE public.food_items
    SET category = NULL
    WHERE category IS NOT NULL AND (
      BTRIM(category) = ''
      OR LOWER(category) = 'general'
      OR LOWER(category) NOT IN ('protein', 'fruits', 'vegetables', 'dairy', 'grains')
    );

    UPDATE public.food_items
    SET category = CASE LOWER(category)
      WHEN 'protein' THEN 'Protein'
      WHEN 'fruits' THEN 'Fruits'
      WHEN 'vegetables' THEN 'Vegetables'
      WHEN 'dairy' THEN 'Dairy'
      WHEN 'grains' THEN 'Grains'
      ELSE category
    END
    WHERE category IS NOT NULL;

    -- Dairy
    UPDATE public.food_items
    SET category = 'Dairy'
    WHERE category IS NULL
      AND COALESCE(food_name, '') ~* '\\b(milk|cheese|yogurt|butter|cream|kefir|whey|casein)\\b';

    -- Protein (meats, eggs, legumes, soy)
    UPDATE public.food_items
    SET category = 'Protein'
    WHERE category IS NULL
      AND COALESCE(food_name, '') ~* '\\b(chicken|beef|pork|lamb|turkey|fish|salmon|tuna|sardine|shrimp|egg|eggs|lentil|lentils|bean|beans|chickpea|chickpeas|tofu|tempeh|ham|bacon)\\b';

    -- Grains
    UPDATE public.food_items
    SET category = 'Grains'
    WHERE category IS NULL
      AND COALESCE(food_name, '') ~* '\\b(rice|bread|oat|oats|wheat|pasta|noodle|noodles|quinoa|barley|corn|maize|cereal|granola|flour)\\b';

    -- Vegetables
    UPDATE public.food_items
    SET category = 'Vegetables'
    WHERE category IS NULL
      AND COALESCE(food_name, '') ~* '\\b(spinach|broccoli|carrot|kale|lettuce|cabbage|cauliflower|pepper|peppers|onion|onions|garlic|potato|potatoes|tomato|tomatoes|cucumber|zucchini|eggplant|celery|beet|beets|okra|pumpkin|squash)\\b';

    -- Fruits
    UPDATE public.food_items
    SET category = 'Fruits'
    WHERE category IS NULL
      AND COALESCE(food_name, '') ~* '\\b(apple|apples|banana|bananas|orange|oranges|mango|mangoes|grape|grapes|strawberry|strawberries|blueberry|blueberries|raspberry|raspberries|pineapple|watermelon|melon|papaya|pear|peach|plum|kiwi|avocado|lemon|lime|cherry|apricot|fig|date|dates)\\b';

    -- Macro-based fallback (ensures every row is one of the 5 categories).
    -- Protein: clearly protein-forward items.
    UPDATE public.food_items
    SET category = 'Protein'
    WHERE category IS NULL
      AND COALESCE(protein_g, 0) >= 12;

    -- Fruits: generally higher sugar/carbs with low fat/protein.
    UPDATE public.food_items
    SET category = 'Fruits'
    WHERE category IS NULL
      AND COALESCE(sugar_g, 0) >= 8
      AND COALESCE(carbs_g, 0) >= 10
      AND COALESCE(protein_g, 0) < 6
      AND COALESCE(fat_g, 0) < 6;

    -- Grains: carb-forward staples.
    UPDATE public.food_items
    SET category = 'Grains'
    WHERE category IS NULL
      AND COALESCE(carbs_g, 0) >= 20
      AND COALESCE(protein_g, 0) < 12;

    -- Final fallback: anything still unclassified becomes Vegetables (never `general`).
    UPDATE public.food_items
    SET category = 'Vegetables'
    WHERE category IS NULL;
  END IF;
END
$$;
