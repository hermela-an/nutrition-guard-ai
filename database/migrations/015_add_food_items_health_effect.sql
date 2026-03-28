-- Adds per-condition health effects for foods (admin-managed overrides).
-- Example: {"diabetes":"good","high_bp":"bad","weight_loss":"neutral"}
-- Safe to run multiple times.

ALTER TABLE public.food_items
  ADD COLUMN IF NOT EXISTS health_effect JSONB NOT NULL DEFAULT '{}'::jsonb;

UPDATE public.food_items
SET health_effect = '{}'::jsonb
WHERE health_effect IS NULL;

CREATE INDEX IF NOT EXISTS idx_food_items_health_effect ON public.food_items USING GIN (health_effect);

