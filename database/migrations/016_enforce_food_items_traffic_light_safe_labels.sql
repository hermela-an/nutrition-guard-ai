-- Ensure food_items.traffic_light uses SAFE/CAUTION/AVOID everywhere.
-- Fixes legacy values (GREEN/YELLOW/RED) and re-applies the check constraint.
-- Safe to run multiple times.

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'food_items'
      AND column_name = 'traffic_light'
  ) THEN
    -- Drop existing constraint first so we can normalize legacy values safely.
    IF EXISTS (
      SELECT 1
      FROM pg_constraint
      WHERE conname = 'chk_food_items_traffic_light_valid'
        AND conrelid = 'public.food_items'::regclass
    ) THEN
      ALTER TABLE public.food_items
        DROP CONSTRAINT chk_food_items_traffic_light_valid;
    END IF;

    -- Normalize existing rows.
    UPDATE public.food_items
    SET traffic_light = CASE UPPER(COALESCE(traffic_light, ''))
      WHEN 'GREEN' THEN 'SAFE'
      WHEN 'YELLOW' THEN 'CAUTION'
      WHEN 'RED' THEN 'AVOID'
      WHEN 'SAFE' THEN 'SAFE'
      WHEN 'CAUTION' THEN 'CAUTION'
      WHEN 'AVOID' THEN 'AVOID'
      ELSE 'SAFE'
    END;

    -- Default for new rows.
    ALTER TABLE public.food_items
      ALTER COLUMN traffic_light SET DEFAULT 'SAFE';

    IF NOT EXISTS (
      SELECT 1
      FROM pg_constraint
      WHERE conname = 'chk_food_items_traffic_light_valid'
        AND conrelid = 'public.food_items'::regclass
    ) THEN
      ALTER TABLE public.food_items
        ADD CONSTRAINT chk_food_items_traffic_light_valid
        CHECK (traffic_light IN ('SAFE', 'CAUTION', 'AVOID'));
    END IF;
  END IF;
END
$$;
