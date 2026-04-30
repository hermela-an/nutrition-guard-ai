-- Drop legacy/experimental column is_recommended_base from local schema.
-- This is safe to re-run.

-- Drop partial index created in 027 if it exists.
DROP INDEX IF EXISTS public.idx_food_items_recommended_base;

ALTER TABLE public.food_items
  DROP COLUMN IF EXISTS is_recommended_base;

