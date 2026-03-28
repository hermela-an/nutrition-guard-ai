-- Enforce that food_items.category is always one of:
--   Protein, Fruits, Vegetables, Dairy, Grains
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
    -- Normalize any lingering invalids to Vegetables.
    UPDATE public.food_items
    SET category = 'Vegetables'
    WHERE category IS NULL
      OR BTRIM(category) = ''
      OR LOWER(category) = 'general'
      OR LOWER(category) NOT IN ('protein', 'fruits', 'vegetables', 'dairy', 'grains');

    -- Canonical casing.
    UPDATE public.food_items
    SET category = CASE LOWER(category)
      WHEN 'protein' THEN 'Protein'
      WHEN 'fruits' THEN 'Fruits'
      WHEN 'vegetables' THEN 'Vegetables'
      WHEN 'dairy' THEN 'Dairy'
      WHEN 'grains' THEN 'Grains'
      ELSE category
    END;

    -- Default for new rows (backend may still omit category).
    ALTER TABLE public.food_items
      ALTER COLUMN category SET DEFAULT 'Vegetables';

    -- Add a strict check constraint.
    IF NOT EXISTS (
      SELECT 1
      FROM pg_constraint
      WHERE conname = 'chk_food_items_category_valid'
        AND conrelid = 'public.food_items'::regclass
    ) THEN
      ALTER TABLE public.food_items
        ADD CONSTRAINT chk_food_items_category_valid
        CHECK (category IN ('Protein', 'Fruits', 'Vegetables', 'Dairy', 'Grains'));
    END IF;

    -- Make it required (now that we've backfilled).
    ALTER TABLE public.food_items
      ALTER COLUMN category SET NOT NULL;
  END IF;
END
$$;

