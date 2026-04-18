-- Standardize `public.food_items` for the admin dashboard.
-- Safe to run multiple times; never deletes existing rows (only drops legacy/duplicate columns).
--
-- Target columns (logical order; Postgres doesn't support reordering without rebuild):
-- id, food_name, meal_type, category, traffic_light, portion_amount, portion_unit, calories_kcal,
-- protein_g, carbs_g, fat_g, sugar_g, sodium_mg, cholesterol_mg, fiber_g, glycemic_index,
-- ingredients, image_url, definition, health_effect, vitamins

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = 'public'
      AND table_name = 'food_items'
  ) THEN
    CREATE TABLE public.food_items (
      id SERIAL PRIMARY KEY,
      food_name TEXT NOT NULL,
      meal_type TEXT NOT NULL,
      category TEXT,
      traffic_light TEXT NOT NULL DEFAULT 'SAFE',
      portion_amount DOUBLE PRECISION NOT NULL DEFAULT 0,
      portion_unit TEXT NOT NULL DEFAULT 'serving',
      calories_kcal NUMERIC NOT NULL DEFAULT 0,
      protein_g DOUBLE PRECISION NOT NULL DEFAULT 0,
      carbs_g DOUBLE PRECISION NOT NULL DEFAULT 0,
      fat_g DOUBLE PRECISION NOT NULL DEFAULT 0,
      sugar_g DOUBLE PRECISION NOT NULL DEFAULT 0,
      sodium_mg DOUBLE PRECISION NOT NULL DEFAULT 0,
      cholesterol_mg DOUBLE PRECISION NOT NULL DEFAULT 0,
      fiber_g DOUBLE PRECISION NOT NULL DEFAULT 0,
      glycemic_index INT NOT NULL DEFAULT 0,
      ingredients TEXT,
      image_url VARCHAR(2048),
      definition TEXT,
      health_effect TEXT,
      vitamins JSONB NOT NULL DEFAULT '{}'::jsonb
    );
  END IF;
END
$$;

-- Add missing columns (non-destructive).
ALTER TABLE public.food_items
  ADD COLUMN IF NOT EXISTS food_name TEXT,
  ADD COLUMN IF NOT EXISTS meal_type TEXT,
  ADD COLUMN IF NOT EXISTS category TEXT,
  ADD COLUMN IF NOT EXISTS traffic_light TEXT,
  ADD COLUMN IF NOT EXISTS portion_amount DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS portion_unit TEXT,
  ADD COLUMN IF NOT EXISTS calories_kcal NUMERIC,
  ADD COLUMN IF NOT EXISTS protein_g DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS carbs_g DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS fat_g DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS sugar_g DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS sodium_mg DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS cholesterol_mg DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS fiber_g DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS glycemic_index INT,
  ADD COLUMN IF NOT EXISTS ingredients TEXT,
  ADD COLUMN IF NOT EXISTS image_url VARCHAR(2048),
  ADD COLUMN IF NOT EXISTS definition TEXT,
  ADD COLUMN IF NOT EXISTS health_effect TEXT,
  ADD COLUMN IF NOT EXISTS vitamins JSONB;

-- Backfill from legacy columns when present.
DO $$
BEGIN
  -- calories -> calories_kcal
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'food_items'
      AND column_name = 'calories'
  ) THEN
    UPDATE public.food_items
    SET calories_kcal = COALESCE(calories_kcal, calories::numeric)
    WHERE calories_kcal IS NULL AND calories IS NOT NULL;
  END IF;

  -- portion_value -> portion_amount
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'food_items'
      AND column_name = 'portion_value'
  ) THEN
    UPDATE public.food_items
    SET portion_amount = COALESCE(portion_amount, (portion_value)::double precision)
    WHERE portion_amount IS NULL AND portion_value IS NOT NULL;
  END IF;
END
$$;

-- Convert health_effect to TEXT if it exists as JSONB.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'food_items'
      AND column_name = 'health_effect'
      AND udt_name = 'jsonb'
  ) THEN
    -- A GIN index on a JSONB column can't remain once we convert it to TEXT.
    DROP INDEX IF EXISTS public.idx_food_items_health_effect;

    ALTER TABLE public.food_items
      ALTER COLUMN health_effect DROP DEFAULT,
      ALTER COLUMN health_effect DROP NOT NULL;

    ALTER TABLE public.food_items
      ALTER COLUMN health_effect TYPE TEXT
      USING health_effect::text;
  END IF;
END
$$;

-- Defaults + NOT NULL constraints (after backfills).
UPDATE public.food_items SET traffic_light = 'SAFE' WHERE traffic_light IS NULL OR BTRIM(traffic_light) = '';
UPDATE public.food_items SET portion_amount = 0 WHERE portion_amount IS NULL;
UPDATE public.food_items SET portion_unit = 'serving' WHERE portion_unit IS NULL OR BTRIM(portion_unit) = '';
UPDATE public.food_items SET calories_kcal = 0 WHERE calories_kcal IS NULL;
UPDATE public.food_items SET protein_g = 0 WHERE protein_g IS NULL;
UPDATE public.food_items SET carbs_g = 0 WHERE carbs_g IS NULL;
UPDATE public.food_items SET fat_g = 0 WHERE fat_g IS NULL;
UPDATE public.food_items SET sugar_g = 0 WHERE sugar_g IS NULL;
UPDATE public.food_items SET sodium_mg = 0 WHERE sodium_mg IS NULL;
UPDATE public.food_items SET cholesterol_mg = 0 WHERE cholesterol_mg IS NULL;
UPDATE public.food_items SET fiber_g = 0 WHERE fiber_g IS NULL;
UPDATE public.food_items SET glycemic_index = 0 WHERE glycemic_index IS NULL;
UPDATE public.food_items SET vitamins = '{}'::jsonb WHERE vitamins IS NULL;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM public.food_items WHERE food_name IS NULL OR BTRIM(food_name) = '') THEN
    RAISE EXCEPTION 'food_items has NULL/blank food_name rows. Fix them before enforcing NOT NULL.';
  END IF;
  IF EXISTS (SELECT 1 FROM public.food_items WHERE meal_type IS NULL OR BTRIM(meal_type) = '') THEN
    RAISE EXCEPTION 'food_items has NULL/blank meal_type rows. Fix them before enforcing NOT NULL.';
  END IF;
END
$$;

ALTER TABLE public.food_items
  ALTER COLUMN traffic_light SET DEFAULT 'SAFE',
  ALTER COLUMN portion_amount SET DEFAULT 0,
  ALTER COLUMN portion_unit SET DEFAULT 'serving',
  ALTER COLUMN calories_kcal SET DEFAULT 0,
  ALTER COLUMN protein_g SET DEFAULT 0,
  ALTER COLUMN carbs_g SET DEFAULT 0,
  ALTER COLUMN fat_g SET DEFAULT 0,
  ALTER COLUMN sugar_g SET DEFAULT 0,
  ALTER COLUMN sodium_mg SET DEFAULT 0,
  ALTER COLUMN cholesterol_mg SET DEFAULT 0,
  ALTER COLUMN fiber_g SET DEFAULT 0,
  ALTER COLUMN glycemic_index SET DEFAULT 0,
  ALTER COLUMN vitamins SET DEFAULT '{}'::jsonb;

ALTER TABLE public.food_items
  ALTER COLUMN food_name SET NOT NULL,
  ALTER COLUMN meal_type SET NOT NULL,
  ALTER COLUMN portion_amount SET NOT NULL,
  ALTER COLUMN portion_unit SET NOT NULL,
  ALTER COLUMN calories_kcal SET NOT NULL,
  ALTER COLUMN vitamins SET NOT NULL;

-- Traffic light constraint (keep existing UI values; also accepts GOOD/NEUTRAL/BAD if used).
DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'chk_food_items_traffic_light_valid'
      AND conrelid = 'public.food_items'::regclass
  ) THEN
    ALTER TABLE public.food_items
      DROP CONSTRAINT chk_food_items_traffic_light_valid;
  END IF;

  ALTER TABLE public.food_items
    ADD CONSTRAINT chk_food_items_traffic_light_valid
    CHECK (traffic_light IN ('SAFE', 'CAUTION', 'AVOID', 'GOOD', 'NEUTRAL', 'BAD'));
END
$$;

-- Drop legacy/duplicate columns (requested).
ALTER TABLE public.food_items
  DROP COLUMN IF EXISTS calories;

ALTER TABLE public.food_items
  DROP COLUMN IF EXISTS portion_value;

-- Enforce uniqueness so (food_name, meal_type) cannot be duplicated.
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
