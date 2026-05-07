-- Update food_role labels to the new standard: main_food, side_food, standalone food
-- And update the check constraint to allow these new values.

BEGIN;

-- 1) Drop the old constraint
ALTER TABLE public.food_items
  DROP CONSTRAINT IF EXISTS chk_food_items_food_role;

-- 2) Update existing data to new labels
UPDATE public.food_items
SET food_role = 'main_food'
WHERE food_role = 'main';

UPDATE public.food_items
SET food_role = 'side_food'
WHERE food_role = 'side';

-- 3) Update the default value
ALTER TABLE public.food_items
  ALTER COLUMN food_role SET DEFAULT 'main_food';

-- 4) Add the new constraint
ALTER TABLE public.food_items
  ADD CONSTRAINT chk_food_items_food_role
  CHECK (food_role IN ('main_food', 'side_food', 'standalone food'));

COMMIT;
