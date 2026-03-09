# Frontend Plan (Angular 18+)

## Recommended App Structure

```text
frontend/
  src/app/
    core/
      services/
        api.service.ts
        auth.service.ts
        health-state.service.ts    # Angular Signals source of truth
    features/
      landing/
      auth/
      profile-setup/
      bmi-calculator/
      nutrition-plan/
      daily-meal-plan/
      food-search/
      progress-tracking/
      vitamin-chat/
    shared/
      components/
      models/
      guards/
```

## Signals State Core
- `currentWeight` signal
- `conditions` signal
- `calorieGoal` signal
- derived/computed signals for sodium/sugar/cholesterol limits

## Page to Backend API Mapping
- Login/Register -> `/api/auth/*`
- Profile Setup -> `PUT /api/profile`
- BMI Calculator -> `POST /api/calculate/targets`
- Nutrition Plan/Daily Meal -> `GET /api/meal-plan/daily`
- Food Search (traffic light) -> `GET /api/foods/search`
- Progress charts -> `/api/progress/*`
- Vitamin bubble chat -> `POST /api/chat/ask` or `WS /api/chat/ws`

## PWA
- Use `ng add @angular/pwa`
- Cache food search + meal plan views for offline-first behavior
- Queue daily logs offline and sync when connectivity returns
