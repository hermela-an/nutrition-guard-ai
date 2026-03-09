# Frontend Structure (Commit Scope 1)

This commit keeps a professional frontend folder layout while focusing on the highest-priority pages.

## Structure

```text
frontend/
  src/
    index.html
    shared/
      assets/
        styles.css
    pages/
      landing.html                # Completed
      auth/
        login.html                # Completed
        signup.html               # Completed
      profile/
        profile-setup.html        # Completed
      placeholders/
        dashboard.html
        bmi-calculator.html
        nutrition-plan.html
        daily-meal-plan.html
        progress-tracking.html
        ai-chat-assistant.html
        food-search.html
        notifications.html
```

## Completed In This Phase
- Landing page
- Login page
- Signup page
- Profile Setup page

## Placeholder In This Phase
- Dashboard
- BMI Calculator
- Nutrition Plan
- Daily Meal Plan
- Progress Tracking
- AI Chat Assistant
- Food Search
- Notifications

## Run Locally
From project root:

```powershell
cd frontend/src
python -m http.server 5173
```

Open: `http://localhost:5173`
