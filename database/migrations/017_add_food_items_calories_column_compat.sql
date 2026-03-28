-- Compatibility migration: some existing databases used calories_kcal instead of calories.
-- This adds a canonical `calories` INT column expected by the app and backfills it.
-- Safe to run multiple times.

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = 'public'
      AND table_name = 'food_items'
  ) THEN
    IF NOT EXISTS (
      SELECT 1
      FROM information_schema.columns
      WHERE table_schema = 'public'
        AND table_name = 'food_items'
        AND column_name = 'calories'
    ) THEN
      ALTER TABLE public.food_items
        ADD COLUMN calories INT NOT NULL DEFAULT 0;

      -- Backfill from legacy calories_kcal when present.
      IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'food_items'
          AND column_name = 'calories_kcal'
      ) THEN
        UPDATE public.food_items
        SET calories = COALESCE(NULLIF(calories, 0), (calories_kcal)::numeric::int, 0);
      END IF;

      -- Enforce non-negative calories.
      IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_food_items_calories_nonneg'
          AND conrelid = 'public.food_items'::regclass
      ) THEN
        ALTER TABLE public.food_items
          ADD CONSTRAINT chk_food_items_calories_nonneg
          CHECK (calories >= 0);
      END IF;
    END IF;
  END IF;
END
$$;

