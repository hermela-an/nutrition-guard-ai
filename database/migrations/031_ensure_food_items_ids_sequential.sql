-- Ensures the food_items id sequence remains strictly sequential and ordered.
-- 1. Updates foreign keys to include ON UPDATE CASCADE.
-- 2. Purges soft-deleted items to avoid hidden gaps.
-- 3. Renumbers all items sequentially starting from 1.
-- 4. Resets the sequence to match.

BEGIN;

-- 1. Purge soft-deleted items (if any exist) so they don't occupy IDs.
DELETE FROM public.food_items WHERE is_deleted = TRUE;

-- 2. Update foreign keys to use ON UPDATE CASCADE.
-- This ensures that when we change the 'id' of a food item, tracking tables update automatically.
DO $$
DECLARE
    r record;
BEGIN
    FOR r IN (
        SELECT
            con.conname AS constraint_name,
            con.conrelid::regclass::text AS table_name,
            a.attname AS column_name
        FROM
            pg_constraint con
            JOIN pg_attribute a ON a.attrelid = con.conrelid AND a.attnum = con.conkey[1]
        WHERE
            con.contype = 'f'
            AND con.confrelid = 'public.food_items'::regclass
    ) LOOP
        EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', r.table_name, r.constraint_name);
        EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY (%I) REFERENCES public.food_items(id) ON DELETE CASCADE ON UPDATE CASCADE',
            r.table_name, r.constraint_name, r.column_name);
    END LOOP;
END $$;

-- 3. Renumber IDs using a negative space trick to avoid unique constraint collisions during update.
-- Phase A: Move all IDs to negative space.
UPDATE public.food_items SET id = -id;

-- Phase B: Assign new consecutive IDs starting from 1.
-- Ordered by the original IDs (which are now negative).
UPDATE public.food_items f
SET id = m.new_id
FROM (
  SELECT id, row_number() OVER (ORDER BY -id ASC) as new_id
  FROM public.food_items
) m
WHERE f.id = m.id;

-- 4. Reset the sequence to the new max ID.
SELECT setval('public.food_items_id_seq', (SELECT COALESCE(MAX(id), 0) FROM public.food_items), true);

COMMIT;
