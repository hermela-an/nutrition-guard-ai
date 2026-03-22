-- Add optional metadata for richer chat responses.
-- Safe to run multiple times.

ALTER TABLE public.food_items
  ADD COLUMN IF NOT EXISTS image_url VARCHAR(2048),
  ADD COLUMN IF NOT EXISTS definition TEXT;

DO $$
BEGIN
  -- Enforce a minimal URL shape (http/https) when image_url is provided.
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'chk_food_items_image_url_http'
      AND conrelid = 'public.food_items'::regclass
  ) THEN
    ALTER TABLE public.food_items
      ADD CONSTRAINT chk_food_items_image_url_http
      CHECK (image_url IS NULL OR image_url ~* '^https?://');
  END IF;
END
$$;

