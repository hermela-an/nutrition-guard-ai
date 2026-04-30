-- Migrate legacy `health_effect_text` into `health_effect` and ensure `health_effect` is JSONB.
-- This migration handles two real-world states:
--  1) `health_effect` is already JSONB (desired): backfill from `health_effect_text` if empty and then drop `health_effect_text`.
--  2) `health_effect` is TEXT (legacy): convert to JSONB safely, prefer existing `health_effect` text, fall back to `health_effect_text`,
--     then drop `health_effect_text` and leave a JSONB `health_effect` column.
--
-- Safety:
-- - Never overwrites a non-empty JSONB `health_effect`.
-- - When converting from text, attempts JSON parse if it "looks like JSON" ({ or [); otherwise stores as {"text": "..."}.
-- - Refuses to drop legacy columns if it would lose non-empty text data.

DO $$
BEGIN
  IF to_regclass('public.food_items') IS NULL THEN
    RAISE EXCEPTION 'Table public.food_items does not exist.';
  END IF;
END $$;

-- If `health_effect` is TEXT, we cannot ALTER TYPE with a "try/catch" parse per-row.
-- Instead we create a temporary JSONB column, backfill safely, then swap.
DO $$
DECLARE
  health_effect_udt text;
  healthy_effect_udt text;
  has_text_column boolean;
BEGIN
  SELECT udt_name INTO health_effect_udt
  FROM information_schema.columns
  WHERE table_schema = 'public'
    AND table_name = 'food_items'
    AND column_name = 'health_effect';

  SELECT udt_name INTO healthy_effect_udt
  FROM information_schema.columns
  WHERE table_schema = 'public'
    AND table_name = 'food_items'
    AND column_name = 'healthy_effect';

  -- Some local DBs may have a legacy typo column name: `healthy_effect` (TEXT).
  -- Treat either `health_effect` or `healthy_effect` as the source of truth for conversion.
  has_text_column := (health_effect_udt = 'text' OR (health_effect_udt IS NULL AND healthy_effect_udt = 'text'));

  IF has_text_column THEN
    -- Create temp JSONB column for safe conversion.
    IF NOT EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_schema = 'public' AND table_name = 'food_items' AND column_name = 'health_effect_jsonb'
    ) THEN
      EXECUTE 'ALTER TABLE public.food_items ADD COLUMN health_effect_jsonb JSONB';
    END IF;
  ELSE
    -- Ensure `health_effect` exists and is JSONB (repo expects JSONB).
    IF NOT EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_schema = 'public' AND table_name = 'food_items' AND column_name = 'health_effect'
    ) THEN
      EXECUTE 'ALTER TABLE public.food_items ADD COLUMN health_effect JSONB';
    END IF;
  END IF;
END $$;

-- Backfill / convert (TEXT -> JSONB) when needed.
DO $$
DECLARE
  health_effect_udt text;
  healthy_effect_udt text;
  using_text boolean;
  r record;
  src text;
  parsed jsonb;
BEGIN
  SELECT udt_name INTO health_effect_udt
  FROM information_schema.columns
  WHERE table_schema = 'public'
    AND table_name = 'food_items'
    AND column_name = 'health_effect';

  SELECT udt_name INTO healthy_effect_udt
  FROM information_schema.columns
  WHERE table_schema = 'public'
    AND table_name = 'food_items'
    AND column_name = 'healthy_effect';

  using_text := (health_effect_udt = 'text' OR (health_effect_udt IS NULL AND healthy_effect_udt = 'text'));

  IF using_text THEN
    -- Convert from text columns into health_effect_jsonb.
    -- Prefer existing `health_effect` text; otherwise fall back to `healthy_effect` (typo column); otherwise `health_effect_text`.
    FOR r IN
      SELECT
        id,
        NULLIF(btrim(health_effect), '') AS he,
        NULLIF(btrim(healthy_effect), '') AS he_typo,
        NULLIF(btrim(health_effect_text), '') AS het
      FROM public.food_items
    LOOP
      src := COALESCE(r.he, r.he_typo, r.het);
      IF src IS NULL THEN
        CONTINUE;
      END IF;

      parsed := NULL;
      IF left(src, 1) = '{' OR left(src, 1) = '[' THEN
        BEGIN
          parsed := src::jsonb;
        EXCEPTION WHEN others THEN
          parsed := NULL;
        END;
      END IF;

      UPDATE public.food_items
      SET health_effect_jsonb = COALESCE(health_effect_jsonb, COALESCE(parsed, jsonb_build_object('text', src)))
      WHERE id = r.id
        AND (health_effect_jsonb IS NULL OR health_effect_jsonb = '{}'::jsonb);
    END LOOP;
  ELSE
    -- health_effect is JSONB already: fill from health_effect_text if health_effect is empty.
    IF EXISTS (
      SELECT 1
      FROM information_schema.columns
      WHERE table_schema = 'public'
        AND table_name = 'food_items'
        AND column_name = 'health_effect_text'
    ) THEN
      FOR r IN
        SELECT id, health_effect_text
        FROM public.food_items
        WHERE (health_effect IS NULL OR health_effect = '{}'::jsonb)
          AND health_effect_text IS NOT NULL
          AND btrim(health_effect_text) <> ''
      LOOP
        src := btrim(r.health_effect_text);
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
      END LOOP;
    END IF;
  END IF;
END $$;

-- If we used the temp column, swap it into place now.
DO $$
DECLARE
  health_effect_udt text;
  healthy_effect_udt text;
  missing int;
BEGIN
  SELECT udt_name INTO health_effect_udt
  FROM information_schema.columns
  WHERE table_schema = 'public'
    AND table_name = 'food_items'
    AND column_name = 'health_effect';

  SELECT udt_name INTO healthy_effect_udt
  FROM information_schema.columns
  WHERE table_schema = 'public'
    AND table_name = 'food_items'
    AND column_name = 'healthy_effect';

  IF health_effect_udt = 'text' OR (health_effect_udt IS NULL AND healthy_effect_udt = 'text') THEN
    -- Refuse to proceed if we'd lose non-empty text.
    SELECT COUNT(*)::int INTO missing
    FROM public.food_items
    WHERE COALESCE(
        NULLIF(btrim(health_effect), ''),
        NULLIF(btrim(healthy_effect), ''),
        NULLIF(btrim(health_effect_text), '')
      ) IS NOT NULL
      AND (health_effect_jsonb IS NULL OR health_effect_jsonb = '{}'::jsonb);

    IF missing > 0 THEN
      RAISE EXCEPTION 'Refusing to convert health_effect TEXT -> JSONB: % row(s) still have non-empty text but empty health_effect_jsonb.', missing;
    END IF;

    IF health_effect_udt = 'text' THEN
      EXECUTE 'ALTER TABLE public.food_items DROP COLUMN health_effect';
    END IF;
    -- health_effect_text will be dropped later (after additional verification below).
    EXECUTE 'ALTER TABLE public.food_items RENAME COLUMN health_effect_jsonb TO health_effect';
  END IF;
END $$;

-- Enforce not-null + default for consistency with the rest of the schema (now JSONB).
UPDATE public.food_items
SET health_effect = '{}'::jsonb
WHERE health_effect IS NULL;

ALTER TABLE public.food_items
  ALTER COLUMN health_effect SET DEFAULT '{}'::jsonb;

ALTER TABLE public.food_items
  ALTER COLUMN health_effect SET NOT NULL;

-- Verification: refuse to drop the legacy column if there are rows that would lose data.
DO $$
DECLARE
  losing int;
  empty_json int;
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'food_items'
      AND column_name = 'health_effect_text'
  ) THEN
    SELECT COUNT(*)::int INTO losing
    FROM public.food_items
    WHERE (health_effect IS NULL OR health_effect = '{}'::jsonb)
      AND health_effect_text IS NOT NULL
      AND btrim(health_effect_text) <> '';

    IF losing > 0 THEN
      RAISE EXCEPTION 'Refusing to drop health_effect_text: % row(s) still have non-empty health_effect_text but empty health_effect.', losing;
    END IF;

    ALTER TABLE public.food_items
      DROP COLUMN health_effect_text;
  END IF;

  SELECT COUNT(*)::int INTO empty_json
  FROM public.food_items
  WHERE health_effect = '{}'::jsonb;
  RAISE NOTICE 'health_effect migration complete. Rows with empty health_effect ({}): %', empty_json;
END $$;
