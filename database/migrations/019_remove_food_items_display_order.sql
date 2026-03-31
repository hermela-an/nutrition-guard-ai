-- Removes display_order support (ordering is based on id in queries/UI).
-- Safe to run even if display_order was never added.

DROP INDEX IF EXISTS public.idx_food_items_display_order_unique;

ALTER TABLE public.food_items
  DROP COLUMN IF EXISTS display_order;

DROP SEQUENCE IF EXISTS public.food_items_display_order_seq;
