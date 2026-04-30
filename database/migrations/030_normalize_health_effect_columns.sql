-- Normalize health-effect columns into a single canonical column:
--   public.food_items.health_effect (JSONB NOT NULL DEFAULT '{}')
--
-- This migration is designed to clean up after inconsistent/typo column names such as:
--   - healthy_effect (TEXT)
--   - helath_effect (TEXT)   -- common misspelling
--   - health_effect_text (TEXT)
--   - helat_effect_text (TEXT) -- misspelling variant
--   - health_effect_jsonb / helath_effect_jsonb (JSONB temp columns)
--
-- It preserves data by:
--   1) Ensuring a JSONB target column exists.
--   2) Filling it from the first non-empty source text column per row (without overwriting non-empty JSONB).
--   3) Dropping legacy/typo columns after a safety check.

DO $$
BEGIN
  IF to_regclass('public.food_items') IS NULL THEN
    RAISE EXCEPTION 'Table public.food_items does not exist.';
  END IF;
END $$;

-- 1) Ensure canonical `health_effect` JSONB exists.
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'food_items'
      AND column_name = 'health_effect'
  ) THEN
    ALTER TABLE public.food_items ADD COLUMN health_effect JSONB;
  END IF;
END $$;

-- 2) If canonical health_effect is TEXT, move it aside to health_effect_text so we can create JSONB.
DO $$
DECLARE
  udt text;
BEGIN
  SELECT udt_name INTO udt
  FROM information_schema.columns
  WHERE table_schema='public' AND table_name='food_items' AND column_name='health_effect';

  IF udt = 'text' THEN
    IF NOT EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_schema='public' AND table_name='food_items' AND column_name='health_effect_text'
    ) THEN
      ALTER TABLE public.food_items RENAME COLUMN health_effect TO health_effect_text;
      ALTER TABLE public.food_items ADD COLUMN health_effect JSONB;
    ELSE
      -- health_effect_text already exists; keep it and create a JSONB health_effect if missing.
      ALTER TABLE public.food_items DROP COLUMN health_effect;
      ALTER TABLE public.food_items ADD COLUMN health_effect JSONB;
    END IF;
  END IF;
END $$;

-- 3) Backfill health_effect JSONB from legacy text columns, without overwriting non-empty JSONB.
DO $$
DECLARE
  r record;
  src text;
  parsed jsonb;
  rowj jsonb;
BEGIN
  FOR r IN
    SELECT id, to_jsonb(food_items) AS rowj
    FROM public.food_items
  LOOP
    rowj := r.rowj;
    -- Choose the first non-empty text source available (works even if columns don't exist).
    src := COALESCE(
      NULLIF(btrim(COALESCE(rowj->>'health_effect_text', '')), ''),
      NULLIF(btrim(COALESCE(rowj->>'healthy_effect', '')), ''),
      NULLIF(btrim(COALESCE(rowj->>'helath_effect', '')), ''),
      NULLIF(btrim(COALESCE(rowj->>'helat_effect_text', '')), '')
    );
    IF src IS NULL THEN
      CONTINUE;
    END IF;

    -- Only fill when empty.
    IF EXISTS (
      SELECT 1
      FROM public.food_items fi
      WHERE fi.id = r.id
        AND (fi.health_effect IS NULL OR fi.health_effect = '{}'::jsonb)
    ) THEN
      parsed := NULL;
      IF left(src, 1) = '{' OR left(src, 1) = '[' THEN
        BEGIN
          parsed := src::jsonb;
        EXCEPTION WHEN others THEN
          parsed := NULL;
        END;
      END IF;

      UPDATE public.food_items
      SET health_effect = COALESCE(parsed, jsonb_build_object('text', src))
      WHERE id = r.id
        AND (health_effect IS NULL OR health_effect = '{}'::jsonb);
    END IF;
  END LOOP;
END $$;

-- 4) Enforce NOT NULL + default on canonical column.
UPDATE public.food_items SET health_effect = '{}'::jsonb WHERE health_effect IS NULL;
ALTER TABLE public.food_items ALTER COLUMN health_effect SET DEFAULT '{}'::jsonb;
ALTER TABLE public.food_items ALTER COLUMN health_effect SET NOT NULL;

-- 5) Safety check: refuse to drop sources if they'd lose data that wasn't migrated.
DO $$
DECLARE
  losing int;
BEGIN
  SELECT COUNT(*)::int INTO losing
  FROM public.food_items
  WHERE (health_effect IS NULL OR health_effect = '{}'::jsonb)
    AND COALESCE(
      NULLIF(btrim(COALESCE(to_jsonb(food_items)->>'health_effect_text', '')), ''),
      NULLIF(btrim(COALESCE(to_jsonb(food_items)->>'healthy_effect', '')), ''),
      NULLIF(btrim(COALESCE(to_jsonb(food_items)->>'helath_effect', '')), ''),
      NULLIF(btrim(COALESCE(to_jsonb(food_items)->>'helat_effect_text', '')), '')
    ) IS NOT NULL;

  IF losing > 0 THEN
    RAISE EXCEPTION 'Refusing to drop legacy health-effect columns: % row(s) still have non-empty text but empty health_effect JSONB.', losing;
  END IF;
END $$;

-- 6) Drop legacy/typo columns if present.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='food_items' AND column_name='health_effect_text') THEN
    ALTER TABLE public.food_items DROP COLUMN health_effect_text;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='food_items' AND column_name='healthy_effect') THEN
    ALTER TABLE public.food_items DROP COLUMN healthy_effect;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='food_items' AND column_name='helath_effect') THEN
    ALTER TABLE public.food_items DROP COLUMN helath_effect;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='food_items' AND column_name='helat_effect_text') THEN
    ALTER TABLE public.food_items DROP COLUMN helat_effect_text;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='food_items' AND column_name='health_effect_jsonb') THEN
    ALTER TABLE public.food_items DROP COLUMN health_effect_jsonb;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='food_items' AND column_name='helath_effect_jsonb') THEN
    ALTER TABLE public.food_items DROP COLUMN helath_effect_jsonb;
  END IF;
END $$;
