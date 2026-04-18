-- Enforce uniqueness for (food_name, meal_type) as-stored (case/whitespace preserved).
-- Note: if you previously created an expression-based index for this name, we drop it
-- first so re-running migrations yields the intended index definition.

DROP INDEX IF EXISTS public.ux_food_items_food_name_meal_type;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM (
      SELECT food_name, meal_type, COUNT(*) AS c
      FROM public.food_items
      GROUP BY food_name, meal_type
      HAVING COUNT(*) > 1
    ) d
  ) THEN
    RAISE EXCEPTION 'Duplicate food_items detected for the same (food_name, meal_type). Resolve duplicates before applying the uniqueness constraint.';
  END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_food_items_food_name_meal_type
  ON public.food_items (food_name, meal_type);
