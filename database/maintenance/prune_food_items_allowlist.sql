-- Prune `public.food_items` rows while keeping an explicit allow-list of foods.
-- Safe-by-default: aborts if any allow-list item is missing (case/trim insensitive).
--
-- Edit the allow-list in the VALUES() list below to match exactly what you want
-- to see in Admin Dashboard → Food Management.
--
-- NOTE: Deleting food_items rows can cascade into other tables if they reference
-- food_items(id) with ON DELETE CASCADE.

BEGIN;

DO $$
BEGIN
  IF to_regclass('public.food_items') IS NULL THEN
    RAISE EXCEPTION 'Table public.food_items does not exist.';
  END IF;
END $$;

DO $$
DECLARE
  missing_names text[];
BEGIN
  SELECT array_agg(k ORDER BY k) INTO missing_names
  FROM (
    SELECT k
    FROM (
      VALUES
        -- Replace these with your Admin Dashboard list (food_name values):
        ('chicken salad'),
        ('rice and salmon'),
        ('spinach'),
        ('doro wat'),
        ('normal firfir'),
        ('quanta firfir'),
        ('injera')
    ) AS allowlist(k)
    EXCEPT
    SELECT DISTINCT
      regexp_replace(replace(lower(btrim(food_name)), '&', 'and'), '\s+', ' ', 'g') AS k
    FROM public.food_items
  ) m;
  IF missing_names IS NOT NULL THEN
    RAISE EXCEPTION 'Refusing to prune public.food_items; allow-list foods not found: %', missing_names;
  END IF;
END $$;

-- Delete all rows except the allow-list.
DELETE FROM public.food_items
WHERE regexp_replace(replace(lower(btrim(food_name)), '&', 'and'), '\s+', ' ', 'g') NOT IN (
  SELECT k FROM (
    VALUES
      ('chicken salad'),
      ('rice and salmon'),
      ('spinach'),
      ('doro wat'),
      ('normal firfir'),
      ('quanta firfir'),
      ('injera')
  ) AS a(k)
);

COMMIT;
