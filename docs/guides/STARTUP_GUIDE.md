# NutritionGuard AI - Startup Guide

## Option 1: Local Postgres (no Docker) (recommended)

```powershell
cd backend

# 1) Configure backend/.env.local (copy from backend/.env.local.example)
# 2) Initialize schema + seed in your local Postgres
.\init-local-db.ps1

# 3) Start backend (uses backend/.env.local)
.\start-backend-local.ps1
```

## Option 2: Docker Postgres

```powershell
cd backend

# Uses backend/.env and starts containers (docker compose)
.\start-backend.ps1 -Db docker
```

## Verify

- Health check: `GET http://localhost:8080/health`

## Troubleshooting

- If the port is taken, change `APP_PORT` in your env file or stop the process using the port.
- If DB connection fails, confirm `DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD` match your running Postgres.
