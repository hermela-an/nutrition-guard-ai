-- Add only new columns needed for single-table food + meal system

ALTER TABLE public.food_items
  ADD COLUMN IF NOT EXISTS item_type TEXT,
  ADD COLUMN IF NOT EXISTS is_recommended_base BOOLEAN,
  ADD COLUMN IF NOT EXISTS components_json JSONB;

-- Backfill defaults for existing rows
UPDATE public.food_items
SET item_type = 'food'
WHERE item_type IS NULL;

UPDATE public.food_items
SET is_recommended_base = FALSE
WHERE is_recommended_base IS NULL;

-- Set defaults and constraints safely
DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'food_items'
      AND column_name = 'item_type'
  ) THEN
    ALTER TABLE public.food_items
      ALTER COLUMN item_type SET DEFAULT 'food';

    ALTER TABLE public.food_items
      ALTER COLUMN item_type SET NOT NULL;
  END IF;

  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'food_items'
      AND column_name = 'is_recommended_base'
  ) THEN
    ALTER TABLE public.food_items
      ALTER COLUMN is_recommended_base SET DEFAULT FALSE;

    ALTER TABLE public.food_items
      ALTER COLUMN is_recommended_base SET NOT NULL;
  END IF;
END
$$;

-- Enforce valid types only
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'chk_food_items_item_type'
      AND conrelid = 'public.food_items'::regclass
  ) THEN
    ALTER TABLE public.food_items
      ADD CONSTRAINT chk_food_items_item_type
      CHECK (item_type IN ('food', 'meal'));
  END IF;
END
$$;

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_food_items_item_type
ON public.food_items (item_type);

CREATE INDEX IF NOT EXISTS idx_food_items_recommended_base
ON public.food_items (is_recommended_base)
WHERE item_type = 'food';