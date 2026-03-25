-- Allow storing uploaded images as data URLs in food_items.image_url.
-- - Expands image_url from VARCHAR(2048) to TEXT
-- - Replaces strict http/https constraint with http/https OR data:image/*
-- Safe to run multiple times.

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'food_items'
      AND column_name = 'image_url'
      AND data_type = 'character varying'
  ) THEN
    ALTER TABLE public.food_items
      ALTER COLUMN image_url TYPE TEXT;
  END IF;
END
$$;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'chk_food_items_image_url_http'
      AND conrelid = 'public.food_items'::regclass
  ) THEN
    ALTER TABLE public.food_items
      DROP CONSTRAINT chk_food_items_image_url_http;
  END IF;
END
$$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'chk_food_items_image_url_valid'
      AND conrelid = 'public.food_items'::regclass
  ) THEN
    ALTER TABLE public.food_items
      ADD CONSTRAINT chk_food_items_image_url_valid
      CHECK (image_url IS NULL OR image_url ~* '^(https?://|data:image/)');
  END IF;
END
$$;

