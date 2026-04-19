BEGIN;

-- 1. Modify foreign keys referencing food_items.id to include ON UPDATE CASCADE
-- This allows us to update the primary keys in food_items, and all dependent rows
-- in other tables will be automatically updated for data integrity.
DO $$
DECLARE
  r record;
BEGIN
  FOR r IN (
    SELECT
      tc.table_schema,
      tc.table_name,
      tc.constraint_name,
      kcu.column_name AS fk_column,
      ccu.table_schema AS ref_schema,
      ccu.table_name AS ref_table,
      ccu.column_name AS ref_column
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu 
      ON tc.constraint_name = kcu.constraint_name 
      AND tc.table_schema = kcu.table_schema
    JOIN information_schema.constraint_column_usage ccu 
      ON ccu.constraint_name = tc.constraint_name 
      AND ccu.table_schema = tc.table_schema
    WHERE tc.constraint_type = 'FOREIGN KEY'
      AND ccu.table_name = 'food_items' 
      AND ccu.column_name = 'id'
  ) LOOP
    -- Drop the existing constraint
    EXECUTE format('ALTER TABLE %I.%I DROP CONSTRAINT %I', 
      r.table_schema, r.table_name, r.constraint_name);
    
    -- Recreate it with ON UPDATE CASCADE (preserving ON DELETE CASCADE assuming that's the standard for this DB)
    EXECUTE format('ALTER TABLE %I.%I ADD CONSTRAINT %I FOREIGN KEY (%I) REFERENCES %I.%I(%I) ON UPDATE CASCADE ON DELETE CASCADE',
      r.table_schema, r.table_name, r.constraint_name, r.fk_column, r.ref_schema, r.ref_table, r.ref_column);
      
    RAISE NOTICE 'Updated foreign key % on table %', r.constraint_name, r.table_name;
  END LOOP;
END $$;

-- 2. Renumber the IDs sequentially into a temporary negative range to avoid unique constraint violations
-- For example, IDs (5, 7, 8, 16) become (-1, -2, -3, -4)
WITH renumbered AS (
    SELECT 
      id AS old_id, 
      -(ROW_NUMBER() OVER (ORDER BY id ASC))::int AS temp_id
    FROM public.food_items
)
UPDATE public.food_items
SET id = r.temp_id
FROM renumbered r
WHERE public.food_items.id = r.old_id;

-- 3. Invert the temporary negative IDs back to positive sequential IDs
-- (-1, -2, -3, -4) become (1, 2, 3, 4)
UPDATE public.food_items 
SET id = abs(id) 
WHERE id < 0;

-- 4. Reset the auto-increment sequence for the food_items table
-- so that the next inserted ID follows the new highest sequential ID.
DO $$
DECLARE
  seq_name text;
  max_id int;
BEGIN
  -- Get the sequence associated with the id column
  seq_name := pg_get_serial_sequence('public.food_items', 'id');
  
  -- Get the current highest ID
  SELECT COALESCE(MAX(id), 0) INTO max_id FROM public.food_items;
  
  IF seq_name IS NOT NULL THEN
    -- Reset sequence to the highest ID, so nextval will be max_id + 1
    PERFORM setval(seq_name, GREATEST(max_id, 1));
    RAISE NOTICE 'Sequence % reset to %', seq_name, GREATEST(max_id, 1);
  ELSE
    RAISE WARNING 'No sequence found for public.food_items.id';
  END IF;
END $$;

-- 5. Restore the foreign keys back to their original state (without ON UPDATE CASCADE)
-- to ensure side-effects don't happen later in production, maintaining strict structural integrity.
DO $$
DECLARE
  r record;
BEGIN
  FOR r IN (
    SELECT
      tc.table_schema,
      tc.table_name,
      tc.constraint_name,
      kcu.column_name AS fk_column,
      ccu.table_schema AS ref_schema,
      ccu.table_name AS ref_table,
      ccu.column_name AS ref_column
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu 
      ON tc.constraint_name = kcu.constraint_name 
      AND tc.table_schema = kcu.table_schema
    JOIN information_schema.constraint_column_usage ccu 
      ON ccu.constraint_name = tc.constraint_name 
      AND ccu.table_schema = tc.table_schema
    WHERE tc.constraint_type = 'FOREIGN KEY'
      AND ccu.table_name = 'food_items' 
      AND ccu.column_name = 'id'
  ) LOOP
    EXECUTE format('ALTER TABLE %I.%I DROP CONSTRAINT %I', 
      r.table_schema, r.table_name, r.constraint_name);
    
    EXECUTE format('ALTER TABLE %I.%I ADD CONSTRAINT %I FOREIGN KEY (%I) REFERENCES %I.%I(%I) ON DELETE CASCADE',
      r.table_schema, r.table_name, r.constraint_name, r.fk_column, r.ref_schema, r.ref_table, r.ref_column);
      
    RAISE NOTICE 'Restored foreign key % on table %', r.constraint_name, r.table_name;
  END LOOP;
END $$;

COMMIT;
