# NutritionGuard AI

Production-style foundation for a medically-aware nutrition planner with:
- Angular (signal-based frontend plan)
- Vert.x micro-verticles backend
- PostgreSQL (real connection, JSONB health metadata)
- JWT auth
- Meal filtering + traffic-light food risk system
- Realtime vitamin chat endpoint

## Project Structure

```text
NutritionGuard AI/
  backend/
    src/main/java/com/nutritionguard/app/
      config/        # Env/app configuration
      controller/    # HTTP controllers (View/API layer)
      db/            # PostgreSQL pool setup
      repository/    # Data access layer
      routes/        # Middleware (JWT auth)
      service/       # Business logic
      verticle/      # Micro-verticles (Auth, Calculation, Filter, Socket, App)
      util/          # Shared helpers
  database/
    migrations/      # Schema
    seed/            # Starter food data
  frontend/
    README.md        # Angular architecture + page mapping
  docs/
    ARCHITECTURE.md
  docker-compose.yml # Local PostgreSQL
```

## Backend Run

1. Start PostgreSQL:
```bash
docker compose up -d
```

2. Configure backend env (PowerShell):
```powershell
$env:APP_PORT="8080"
$env:DB_HOST="localhost"
$env:DB_PORT="5432"
$env:DB_NAME="nutritionguard"
$env:DB_USER="nutritionguard"
$env:DB_PASSWORD="nutritionguard"
$env:JWT_SECRET="replace-with-strong-secret"
```

3. Start Vert.x backend:
```bash
cd backend
mvn exec:java
```

4. Health check:
`GET http://localhost:8080/health`

## Core API Endpoints

- `POST /api/auth/register`
- `POST /api/auth/login`
- `PUT /api/profile`
- `GET /api/profile`
- `POST /api/calculate/targets`
- `GET /api/meal-plan/daily`
- `GET /api/foods/search?q=spinach`
- `POST /api/progress/logs`
- `GET /api/progress/history`
- `POST /api/chat/ask`
- `WS /api/chat/ws`

All `/api/profile*`, `/api/meal-plan*`, `/api/foods*`, `/api/progress*`, `/api/chat*` routes require `Authorization: Bearer <token>`.
