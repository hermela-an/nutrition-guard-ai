# Nutrition Tracking (Single-Table `food_items`)

This project uses **one table** (`public.food_items`) to store:

- **Base foods** (`item_type = 'food'`): individual ingredients with nutrition data.
- **Meals** (`item_type = 'meal'`): user/system-created combinations that store **only** their structure in JSON; nutrition is computed at runtime from base foods.

## Table columns (core for this model)

The app already has `public.food_items` with many compatibility columns (e.g., `food_name`, `meal_type`, `portion_amount`, `calories_kcal`, `protein_g`, ...).  
This model adds the following columns (migration `database/migrations/027_add_food_items_meals_single_table.sql`):

- `item_type` (`'food' | 'meal'`)
- `calories_per_100g` (nullable)
- `protein` (nullable, g per 100g)
- `carbs` (nullable, g per 100g)
- `fat` (nullable, g per 100g)
- `is_recommended_base` (boolean flag for system “recommended base foods”)
- `components_json` (nullable; used only when `item_type='meal'`)

## Base foods (`item_type='food'`)

Example base foods: **Kitfo**, **Injera**, **Bread**, **Misir Wat**.

- Store nutrition values (preferably per 100g in `calories_per_100g`, `protein`, `carbs`, `fat`).
- `components_json` must be `NULL`.

## Meals (`item_type='meal'`)

Meals **do not store** nutrition totals. They store a JSON list of components:

```json
[
  { "food_id": 1, "quantity_g": 150 },
  { "food_id": 2, "quantity_g": 120 }
]
```

At runtime, the backend:

1. Loads each referenced base food
2. Converts nutrition to “per 100g” (uses `calories_per_100g` if present, otherwise derives it from `portion_amount` + `calories_kcal` when `portion_unit='g'`)
3. Multiplies by `quantity_g / 100`
4. Sums totals

## API endpoints (added for meal-building)

- `GET /api/foods/recommended` — returns only base foods where `is_recommended_base=true`
- `POST /api/meals` — creates a meal with `components_json` and returns computed nutrition (passwords never involved)
- `GET /api/meals/:id` — fetches a meal and returns computed nutrition
- `POST /api/meals/calculate` — compute nutrition for a component list without saving

All of these are **additive** and do not change existing food search, meal plan, or admin endpoints.

