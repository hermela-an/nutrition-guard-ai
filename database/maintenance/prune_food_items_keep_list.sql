-- Prune `public.food_items` rows while keeping a specific set of foods.
-- Safe-by-default: aborts if any keep-list item is missing (case/trim insensitive).
--
-- Keeps (by food_name):
--   - Firfir
--   - Doro Wat
--   - Chicken Salad
--   - Rice and salmon
--   - Spinach
--
-- Note: If other tables reference `food_items(id)` with ON DELETE CASCADE,
-- deleting food_items rows will also delete related rows automatically.

BEGIN;

-- Normalization used in this script:
-- - trim ends
-- - lowercase
-- - collapse any internal whitespace runs to a single space
-- - treat "&" as "and"

-- Fail fast if the table does not exist.
DO $$
BEGIN
  IF to_regclass('public.food_items') IS NULL THEN
    RAISE EXCEPTION 'Table public.food_items does not exist.';
  END IF;
END $$;


-- Refuse to run if any keep-list item is not present in the table (case/trim-insensitive),
-- to avoid accidental full-table deletes due to typos or unexpected naming.
DO $$
DECLARE
  missing text[];
  m text;
  candidates text[];
BEGIN
  SELECT array_agg(k ORDER BY k) INTO missing
  FROM (
    SELECT k
    FROM (
      VALUES
        ('firfir'),
        ('doro wat'),
        ('chicken salad'),
        ('rice and salmon'),
        ('spinach')
    ) AS keep(k)
    EXCEPT
    SELECT DISTINCT
      regexp_replace(replace(lower(btrim(food_name)), '&', 'and'), '\s+', ' ', 'g') AS k
    FROM public.food_items
  ) AS m;

  IF missing IS NOT NULL THEN
    FOREACH m IN ARRAY missing LOOP
      SELECT array_agg(food_name ORDER BY food_name) INTO candidates
      FROM (
        SELECT DISTINCT food_name
        FROM public.food_items
        WHERE regexp_replace(replace(lower(btrim(food_name)), '&', 'and'), '\s+', ' ', 'g')
          LIKE '%' || replace(m, ' ', '%') || '%'
        ORDER BY food_name
        LIMIT 20
      ) s;

      RAISE NOTICE 'Keep-list item not found: "%". Candidate existing names (up to 20): %', m, COALESCE(candidates, ARRAY[]::text[]);
    END LOOP;

    -- RAISE EXCEPTION 'Refusing to prune public.food_items; keep-list foods not found: %', missing;
  END IF;
END $$;

-- Preview: how many rows would be deleted?
SELECT COUNT(*)::int AS rows_to_delete
FROM public.food_items
WHERE regexp_replace(replace(lower(btrim(food_name)), '&', 'and'), '\s+', ' ', 'g') NOT IN (
  'firfir',
  'doro wat',
  'chicken salad',
  'rice and salmon',
  'spinach'
);

-- Optional preview: which food names would be removed (top 50 by frequency)?
SELECT
  btrim(food_name) AS food_name,
  COUNT(*)::int AS rows
FROM public.food_items
WHERE regexp_replace(replace(lower(btrim(food_name)), '&', 'and'), '\s+', ' ', 'g') NOT IN (
  'firfir',
  'doro wat',
  'chicken salad',
  'rice and salmon',
  'spinach'
)
GROUP BY btrim(food_name)
ORDER BY COUNT(*) DESC, btrim(food_name) ASC
LIMIT 50;

-- Delete all rows except the keep-list.
DELETE FROM public.food_items
WHERE regexp_replace(replace(lower(btrim(food_name)), '&', 'and'), '\s+', ' ', 'g') NOT IN (
  'firfir',
  'doro wat',
  'chicken salad',
  'rice and salmon',
  'spinach'
);

-- Verify what's left (should be only the keep-list, across any meal_type variants).
SELECT
  id,
  food_name,
  meal_type,
  portion_amount,
  portion_unit,
  calories_kcal
FROM public.food_items
ORDER BY lower(btrim(food_name)) ASC, meal_type ASC, id ASC;

COMMIT;
