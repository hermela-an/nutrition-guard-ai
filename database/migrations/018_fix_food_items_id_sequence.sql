-- Ensures the food_items id sequence matches MAX(id) so new inserts keep increasing.
-- This does not change any existing ids.
-- Safe to run multiple times.

DO $$
DECLARE
  seq text;
  max_id bigint;
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = 'public'
      AND table_name = 'food_items'
  ) THEN
    seq := pg_get_serial_sequence('public.food_items', 'id');
    IF seq IS NOT NULL THEN
      SELECT COALESCE(MAX(id), 0) INTO max_id FROM public.food_items;
      PERFORM setval(seq, max_id, true);
    END IF;
  END IF;
END
$$;

