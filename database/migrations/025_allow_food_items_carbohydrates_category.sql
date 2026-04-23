-- Allow Carbohydrates as a valid food_items.category value.
-- Safe to run multiple times.

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'food_items'
      AND column_name = 'category'
  ) THEN
    -- Drop existing constraint first so we can safely normalize and expand allowed values.
    IF EXISTS (
      SELECT 1
      FROM pg_constraint
      WHERE conname = 'chk_food_items_category_valid'
        AND conrelid = 'public.food_items'::regclass
    ) THEN
      ALTER TABLE public.food_items
        DROP CONSTRAINT chk_food_items_category_valid;
    END IF;

    -- Canonical casing + common synonyms.
    UPDATE public.food_items
    SET category = CASE LOWER(COALESCE(category, ''))
      WHEN 'protein' THEN 'Protein'
      WHEN 'fruits' THEN 'Fruits'
      WHEN 'fruit' THEN 'Fruits'
      WHEN 'vegetables' THEN 'Vegetables'
      WHEN 'vegetable' THEN 'Vegetables'
      WHEN 'veggies' THEN 'Vegetables'
      WHEN 'veggie' THEN 'Vegetables'
      WHEN 'dairy' THEN 'Dairy'
      WHEN 'grains' THEN 'Grains'
      WHEN 'grain' THEN 'Grains'
      WHEN 'carbohydrates' THEN 'Carbohydrates'
      WHEN 'carbohydrate' THEN 'Carbohydrates'
      WHEN 'carbs' THEN 'Carbohydrates'
      WHEN 'carb' THEN 'Carbohydrates'
      WHEN 'nuts and seeds' THEN 'Nuts and Seeds'
      WHEN 'nuts & seeds' THEN 'Nuts and Seeds'
      WHEN 'nuts' THEN 'Nuts and Seeds'
      WHEN 'nut' THEN 'Nuts and Seeds'
      WHEN 'seeds' THEN 'Nuts and Seeds'
      WHEN 'seed' THEN 'Nuts and Seeds'
      ELSE category
    END;

    -- Normalize any lingering invalids to Vegetables.
    UPDATE public.food_items
    SET category = 'Vegetables'
    WHERE category IS NULL
      OR BTRIM(category) = ''
      OR LOWER(category) = 'general'
      OR LOWER(category) NOT IN ('protein', 'fruits', 'vegetables', 'dairy', 'grains', 'carbohydrates', 'nuts and seeds');

    -- Default for new rows (backend may still omit category).
    ALTER TABLE public.food_items
      ALTER COLUMN category SET DEFAULT 'Vegetables';

    -- Add strict check constraint including Carbohydrates.
    IF NOT EXISTS (
      SELECT 1
      FROM pg_constraint
      WHERE conname = 'chk_food_items_category_valid'
        AND conrelid = 'public.food_items'::regclass
    ) THEN
      ALTER TABLE public.food_items
        ADD CONSTRAINT chk_food_items_category_valid
        CHECK (category IN ('Protein', 'Fruits', 'Vegetables', 'Dairy', 'Grains', 'Carbohydrates', 'Nuts and Seeds'));
    END IF;

    -- Keep it required.
    ALTER TABLE public.food_items
      ALTER COLUMN category SET NOT NULL;
  END IF;
END
$$;

