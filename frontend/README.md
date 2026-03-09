# Frontend (Angular)

The frontend is now a real Angular application.

## Structure

```text
frontend/
  ng-app/
    src/
      app/
        core/
          config/
          guards/
          services/
        shared/
          components/
        features/
          landing/
          auth/login/
          auth/signup/
          profile/
          dashboard/
          bmi/
          placeholder/
```

## Implemented Pages
- Landing
- Login (real API call)
- Signup (real API call)
- Profile Setup (real load/save with JWT)
- Dashboard
- BMI Calculator

## Placeholder Pages
- Nutrition Plan
- Daily Meal Plan
- Progress Tracking
- AI Chat Assistant
- Food Search
- Notifications

## Run Locally

```powershell
cd frontend/ng-app
npm.cmd install
npm.cmd start
```

Open: `http://localhost:4200`
