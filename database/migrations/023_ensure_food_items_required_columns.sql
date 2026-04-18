-- Ensure `public.food_items` contains the nutrition + metadata columns needed by the app.
-- Safe to run multiple times; never deletes existing data.

-- NOTE:
-- - Keep `calories_kcal` as the stored calories column.
-- - Keep `portion_amount` as the stored portion size amount.
-- - If legacy columns exist (`calories`, `portion_value`), we backfill then drop them.

CREATE TABLE IF NOT EXISTS public.food_items (
  id SERIAL PRIMARY KEY,
  food_name TEXT NOT NULL,
  meal_type TEXT NOT NULL,
  category TEXT,
  portion_amount DOUBLE PRECISION NOT NULL,
  portion_unit TEXT NOT NULL,
  calories_kcal NUMERIC,
  protein_g DOUBLE PRECISION NOT NULL DEFAULT 0,
  carbs_g DOUBLE PRECISION NOT NULL DEFAULT 0,
  fat_g DOUBLE PRECISION NOT NULL DEFAULT 0,
  fiber_g DOUBLE PRECISION NOT NULL DEFAULT 0,
  sugar_g DOUBLE PRECISION NOT NULL DEFAULT 0,
  cholesterol_mg DOUBLE PRECISION NOT NULL DEFAULT 0,
  sodium_mg DOUBLE PRECISION NOT NULL DEFAULT 0,
  glycemic_index INT NOT NULL DEFAULT 0,
  vitamins JSONB NOT NULL DEFAULT '{}'::jsonb,
  ingredients TEXT,
  definition TEXT,
  health_effect JSONB NOT NULL DEFAULT '{}'::jsonb,
  image_url VARCHAR(2048),
  traffic_light TEXT NOT NULL DEFAULT 'SAFE'
);

ALTER TABLE public.food_items
  ADD COLUMN IF NOT EXISTS food_name TEXT,
  ADD COLUMN IF NOT EXISTS meal_type TEXT,
  ADD COLUMN IF NOT EXISTS category TEXT,
  ADD COLUMN IF NOT EXISTS portion_amount DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS portion_unit TEXT,
  ADD COLUMN IF NOT EXISTS calories_kcal NUMERIC,
  ADD COLUMN IF NOT EXISTS protein_g DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS carbs_g DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS fat_g DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS fiber_g DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS sugar_g DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS cholesterol_mg DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS sodium_mg DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS glycemic_index INT,
  ADD COLUMN IF NOT EXISTS vitamins JSONB,
  ADD COLUMN IF NOT EXISTS ingredients TEXT,
  ADD COLUMN IF NOT EXISTS definition TEXT,
  ADD COLUMN IF NOT EXISTS image_url VARCHAR(2048),
  ADD COLUMN IF NOT EXISTS traffic_light TEXT;

-- Ensure health_effect is JSONB for backend compatibility.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'food_items'
      AND column_name = 'health_effect'
  ) THEN
    -- If someone created it as TEXT, keep their data by renaming it, then create JSONB column.
    IF EXISTS (
      SELECT 1
      FROM information_schema.columns
      WHERE table_schema = 'public'
        AND table_name = 'food_items'
        AND column_name = 'health_effect'
        AND udt_name = 'text'
    ) THEN
      ALTER TABLE public.food_items
        RENAME COLUMN health_effect TO health_effect_text;

      ALTER TABLE public.food_items
        ADD COLUMN health_effect JSONB NOT NULL DEFAULT '{}'::jsonb;
    END IF;
  ELSE
    ALTER TABLE public.food_items
      ADD COLUMN health_effect JSONB NOT NULL DEFAULT '{}'::jsonb;
  END IF;
END
$$;

UPDATE public.food_items
SET vitamins = '{}'::jsonb
WHERE vitamins IS NULL;

UPDATE public.food_items
SET health_effect = '{}'::jsonb
WHERE health_effect IS NULL;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'food_items'
      AND column_name = 'vitamins'
  ) THEN
    ALTER TABLE public.food_items
      ALTER COLUMN vitamins SET DEFAULT '{}'::jsonb;
  END IF;

  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'food_items'
      AND column_name = 'health_effect'
  ) THEN
    ALTER TABLE public.food_items
      ALTER COLUMN health_effect SET DEFAULT '{}'::jsonb;
  END IF;
END
$$;

-- Non-negative checks (NULL allowed so this migration doesn't fail on legacy rows).
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'chk_food_items_calories_kcal_nonneg'
      AND conrelid = 'public.food_items'::regclass
  ) THEN
    ALTER TABLE public.food_items
      ADD CONSTRAINT chk_food_items_calories_kcal_nonneg
      CHECK (calories_kcal IS NULL OR calories_kcal >= 0);
  END IF;
END
$$;

-- If legacy columns exist, backfill and then drop them (avoids "duplicate meaning" columns).
DO $$
BEGIN
  -- portion_value -> portion_amount
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'food_items'
      AND column_name = 'portion_value'
  ) THEN
    UPDATE public.food_items
    SET portion_amount = portion_value::double precision
    WHERE portion_amount IS NULL AND portion_value IS NOT NULL;
  END IF;

  -- calories -> calories_kcal
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'food_items'
      AND column_name = 'calories'
  ) THEN
    UPDATE public.food_items
    SET calories_kcal = calories::numeric
    WHERE calories_kcal IS NULL AND calories IS NOT NULL;
  END IF;
END
$$;

-- Remove any old sync trigger/function from previous attempts.
DROP TRIGGER IF EXISTS trg_food_items_sync_compat_columns ON public.food_items;
DROP FUNCTION IF EXISTS public.tg_food_items_sync_compat_columns();

-- Drop legacy columns (requested).
ALTER TABLE public.food_items
  DROP COLUMN IF EXISTS portion_value;

ALTER TABLE public.food_items
  DROP COLUMN IF EXISTS calories;

-- Enforce uniqueness so (food_name, meal_type) cannot be duplicated.
DROP INDEX IF EXISTS public.ux_food_items_food_name_meal_type;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM (
      SELECT food_name, meal_type, COUNT(*) AS c
      FROM public.food_items
      GROUP BY food_name, meal_type
      HAVING COUNT(*) > 1
    ) d
  ) THEN
    RAISE EXCEPTION 'Duplicate food_items detected for the same (food_name, meal_type). Resolve duplicates before applying the uniqueness constraint.';
  END IF;
END
$$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_food_items_food_name_meal_type
  ON public.food_items (food_name, meal_type);
