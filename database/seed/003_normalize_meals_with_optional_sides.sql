-- Normalize Ethiopian meals to the single-table "food + meal" design.
-- Goal: avoid rigid combinations like "Misir Wat with Injera" in the database.
-- Safe to run multiple times.

-- 1) Remove rigid combination rows (if present).
DELETE FROM public.food_items
WHERE LOWER(food_name) LIKE '% with injera%'
   OR LOWER(food_name) LIKE '% and injera%'
   OR LOWER(food_name) LIKE '% with bread%'
   OR LOWER(food_name) LIKE '% and bread%';

-- 2) Ensure side rows exist so side resolution can prefer meal_type='side'.
INSERT INTO public.food_items (
  food_name,
  meal_type,
  portion_amount,
  portion_unit,
  calories_kcal,
  protein_g,
  carbs_g,
  fat_g,
  vitamins,
  traffic_light,
  item_type,
  components_json
)
VALUES
  ('Injera', 'side', 100, 'g', 180, 6, 36, 2, '{}'::jsonb, 'SAFE', 'food', NULL),
  ('Bread',  'side', 60,  'g', 160, 5, 30, 2, '{}'::jsonb, 'SAFE', 'food', NULL)
ON CONFLICT (food_name, meal_type) DO NOTHING;

-- 3) Mark base Ethiopian dishes as `meal` items and attach optional side choices.
UPDATE public.food_items
SET
  item_type = 'meal',
  components_json = '{
    "side_options": [
      { "food": "Injera" },
      { "food": "Bread" }
    ]
  }'::jsonb
WHERE LOWER(food_name) IN ('kitfo', 'misir wat', 'doro wat', 'shiro');

