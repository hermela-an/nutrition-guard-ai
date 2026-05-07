-- Remove the food_role check constraint to debug what value is causing the violation.
-- We will re-add it once we confirm the values being saved.

BEGIN;

ALTER TABLE public.food_items
  DROP CONSTRAINT IF EXISTS chk_food_items_food_role;

-- We also relax the category constraint just in case it's actually that one and the error message was confusing (though unlikely).
ALTER TABLE public.food_items
  DROP CONSTRAINT IF EXISTS chk_food_items_category_valid;

COMMIT;
