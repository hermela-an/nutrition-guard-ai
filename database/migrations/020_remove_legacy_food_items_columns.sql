-- Removes legacy columns that can confuse DB inspection (the app uses canonical columns).
-- Safe to run multiple times.
-- - calories_kcal -> canonical calories (017 backfills calories already)
-- - portion_value -> canonical portion_amount

DO $$
BEGIN
  -- Backfill portion_amount from legacy portion_value when both columns exist.
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'food_items'
      AND column_name = 'portion_amount'
  ) AND EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'food_items'
      AND column_name = 'portion_value'
  ) THEN
    EXECUTE $sql$
      UPDATE public.food_items
      SET portion_amount = COALESCE(NULLIF(portion_amount, 0), (portion_value)::double precision)
      WHERE portion_value IS NOT NULL
    $sql$;
  END IF;
END
$$;

ALTER TABLE public.food_items
  DROP COLUMN IF EXISTS calories_kcal,
  DROP COLUMN IF EXISTS portion_value;

