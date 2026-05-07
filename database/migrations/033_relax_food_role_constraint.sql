-- Relax the food_role constraint to allow both underscore and space variations, 
-- and ensure it doesn't block updates during the transition.

BEGIN;

ALTER TABLE public.food_items
  DROP CONSTRAINT IF EXISTS chk_food_items_food_role;

-- Standardize everything to underscore version for consistency with main_food/side_food
UPDATE public.food_items
SET food_role = 'standalone_food'
WHERE food_role IN ('standalone', 'standalone food');

UPDATE public.food_items
SET food_role = 'main_food'
WHERE food_role = 'main';

UPDATE public.food_items
SET food_role = 'side_food'
WHERE food_role = 'side';

ALTER TABLE public.food_items
  ADD CONSTRAINT chk_food_items_food_role
  CHECK (food_role IN ('main_food', 'side_food', 'standalone_food', 'standalone food'));

-- Also update the default to be consistent
ALTER TABLE public.food_items
  ALTER COLUMN food_role SET DEFAULT 'main_food';

COMMIT;
