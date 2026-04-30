-- Add `food_role` to distinguish main vs side foods without changing the existing
-- `item_type` ('food' | 'meal') behavior used across the app.

ALTER TABLE public.food_items
  ADD COLUMN IF NOT EXISTS food_role TEXT;

-- Backfill existing rows:
-- - If the row is explicitly a side (meal_type='side'), mark as side.
-- - Otherwise default to main.
UPDATE public.food_items
SET food_role = CASE
  WHEN LOWER(COALESCE(meal_type, '')) = 'side' THEN 'side'
  ELSE 'main'
END
WHERE food_role IS NULL;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'food_items'
      AND column_name = 'food_role'
  ) THEN
    ALTER TABLE public.food_items
      ALTER COLUMN food_role SET DEFAULT 'main';

    ALTER TABLE public.food_items
      ALTER COLUMN food_role SET NOT NULL;
  END IF;
END
$$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'chk_food_items_food_role'
      AND conrelid = 'public.food_items'::regclass
  ) THEN
    ALTER TABLE public.food_items
      ADD CONSTRAINT chk_food_items_food_role
      CHECK (food_role IN ('main', 'side'));
  END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_food_items_food_role
ON public.food_items (food_role);

