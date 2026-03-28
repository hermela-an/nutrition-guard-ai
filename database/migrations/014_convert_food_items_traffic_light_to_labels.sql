-- Convert food_items.traffic_light values from GREEN/YELLOW/RED to SAFE/CAUTION/AVOID.
-- Keeps system behavior the same; only the stored labels change.
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
    -- Convert existing rows (legacy -> new).
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

    -- Replace constraint to match new values.
    IF EXISTS (
      SELECT 1
      FROM pg_constraint
      WHERE conname = 'chk_food_items_traffic_light_valid'
        AND conrelid = 'public.food_items'::regclass
    ) THEN
      ALTER TABLE public.food_items
        DROP CONSTRAINT chk_food_items_traffic_light_valid;
    END IF;

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

