-- Drop legacy food_items.safety_status if it exists.
-- Safe to run multiple times.

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'food_items'
      AND column_name = 'safety_status'
  ) THEN
    ALTER TABLE public.food_items
      DROP COLUMN safety_status;
  END IF;
END
$$;

