-- Adds optional category for food_items.
-- Safe to run multiple times.

ALTER TABLE public.food_items
  ADD COLUMN IF NOT EXISTS category TEXT;

-- Optional backfill for existing rows.
UPDATE public.food_items
SET category = 'general'
WHERE category IS NULL;

