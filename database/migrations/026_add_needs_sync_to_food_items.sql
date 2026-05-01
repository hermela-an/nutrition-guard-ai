-- Add needs_sync and is_deleted to food_items table for admin local DB sync rule.

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'food_items'
      AND column_name = 'needs_sync'
  ) THEN
    ALTER TABLE public.food_items
    ADD COLUMN needs_sync BOOLEAN NOT NULL DEFAULT true;
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'food_items'
      AND column_name = 'is_deleted'
  ) THEN
    ALTER TABLE public.food_items
    ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT false;
  END IF;
END $$;
