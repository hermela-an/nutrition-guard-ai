# Nutrition Plan Implementation

High-level notes on the Nutrition Plan feature implementation and intended behavior.

## Key Features

- Summary cards: BMI, BMR, TDEE, target calories
- Macro breakdown: protein/carbs/fats with progress bars
- Medical constraints: shown based on `profiles.health_metadata`
- Goal-specific guidelines: weight loss/gain/maintenance hints
- Backend integration through:
  - `GET /api/profile`
  - `POST /api/calculate/targets`

## Files

- Angular feature: `frontend/ng-app/src/app/features/nutrition-plan/`
- Backend logic:
  - Calculation: `backend/src/main/java/com/nutritionguard/app/controller/CalculationController.java`
  - Profile: `backend/src/main/java/com/nutritionguard/app/controller/ProfileController.java`

## Notes

- This document is intentionally short; keep implementation details close to the code and tests.
