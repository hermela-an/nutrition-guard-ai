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
    guides/          # Setup + implementation notes
    README.md        # Docs index
  docker-compose.yml # Local PostgreSQL
```

## Backend Run

Tip: The repo includes helper scripts:
- Docker DB: `backend\start-backend.ps1 -Db docker` (uses `backend\.env`)
- Local Postgres: `backend\start-backend.ps1 -Db local` (uses `backend\.env.local`)

To initialize (or re-sync) your **local** database schema + seed foods, run:
- `backend\init-local-db.ps1` (applies `database/migrations/*.sql` then runs both `database/seed/001_food_items.sql` and `database/seed/002_import_food_items_from_json.sql`).

1. Start PostgreSQL:
```bash
docker compose up -d
```

2. Configure backend env (PowerShell):
```powershell
$env:APP_PORT="8080"
$env:DB_HOST="localhost"
$env:DB_PORT="5433"
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

## Frontend Run (Angular)

```powershell
cd frontend\ng-app

# Install dependencies (PowerShell execution policy may block npm.ps1; use npm.cmd)
npm.cmd ci

# Start dev server
npm.cmd start
```

## Login Debug Checklist (User/Admin)

- There is **no separate `admins` table**. Admins are rows in `public.users` with `role = 'ADMIN'`.
- If login returns **"Invalid credentials"**, first confirm the backend is connected to the expected DB: on startup it prints `DB_PORT`, `DB_NAME`, `DB_SCHEMA`, and which `ENV_FILE` it loaded.
- If your email is listed in `ADMIN_EMAILS`, **signup** (`/api/auth/register`) will create that account as `ADMIN` automatically.
- To promote an existing account to admin, run `backend\make-admin.ps1 -Email you@example.com`.

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
