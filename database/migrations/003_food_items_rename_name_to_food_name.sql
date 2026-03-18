-- Upgrade migration for existing databases created before food_name/portion columns existed.
-- Safe to run multiple times.

DO $$
BEGIN
  -- Rename food_items.name -> food_items.food_name if needed.
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'food_items'
      AND column_name = 'name'
  ) AND NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'food_items'
      AND column_name = 'food_name'
  ) THEN
    ALTER TABLE public.food_items RENAME COLUMN name TO food_name;
  END IF;

  -- Backfill any NULL food_name (defensive).
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'food_items'
      AND column_name = 'food_name'
  ) THEN
    UPDATE public.food_items
    SET food_name = COALESCE(food_name, 'Unknown')
    WHERE food_name IS NULL;
  END IF;

  -- Ensure portion columns exist (for very early databases).
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'food_items'
      AND column_name = 'portion_amount'
  ) THEN
    ALTER TABLE public.food_items
      ADD COLUMN portion_amount DOUBLE PRECISION NOT NULL DEFAULT 0;
    ALTER TABLE public.food_items
      ALTER COLUMN portion_amount DROP DEFAULT;
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'food_items'
      AND column_name = 'portion_unit'
  ) THEN
    ALTER TABLE public.food_items
      ADD COLUMN portion_unit TEXT NOT NULL DEFAULT '';
    ALTER TABLE public.food_items
      ALTER COLUMN portion_unit DROP DEFAULT;
  END IF;

  -- Index maintenance: replace idx_food_items_name with idx_food_items_food_name.
  IF EXISTS (
    SELECT 1 FROM pg_indexes
    WHERE schemaname = 'public'
      AND tablename = 'food_items'
      AND indexname = 'idx_food_items_name'
  ) THEN
    EXECUTE 'DROP INDEX IF EXISTS public.idx_food_items_name';
  END IF;

  EXECUTE 'CREATE INDEX IF NOT EXISTS idx_food_items_food_name ON public.food_items (LOWER(food_name))';
END
$$;

