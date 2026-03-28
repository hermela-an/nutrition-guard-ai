-- Merge food_items.description into food_items.definition (when definition is missing),
-- then drop the description column.
-- Safe to run multiple times.

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'food_items'
      AND column_name = 'description'
  ) AND EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'food_items'
      AND column_name = 'definition'
  ) THEN
    UPDATE public.food_items
    SET definition = description
    WHERE definition IS NULL AND description IS NOT NULL;
  END IF;
END
$$;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'food_items'
      AND column_name = 'description'
  ) THEN
    ALTER TABLE public.food_items
      DROP COLUMN description;
  END IF;
END
$$;

