# NutritionGuard AI - Startup Guide

## The Problem You Were Experiencing

The error you encountered was caused by **Docker Desktop not running**, which meant the PostgreSQL database container was not available. This prevented the backend from starting and you from accessing the system.

## The Fix

I've updated the `backend/start-backend.ps1` script to support both:
- **Local Postgres** (no Docker), using `backend/.env.local`
- **Docker Postgres**, using `backend/.env`

1. **Check if Docker Desktop is running**
2. **Start Docker Desktop if needed**
3. **Start the database containers**
4. **Wait for the database to be ready**
5. **Start the backend application**

## How to Start the System

### Option 1: Local Postgres (No Docker) (Recommended)
```powershell
cd backend

# 1) Configure backend/.env.local (copy from backend/.env.local.example)
# 2) Initialize schema + seed in your local Postgres
.\init-local-db.ps1

# 3) Start backend (auto-detects .env.local and skips Docker)
.\start-backend.ps1
```

### Option 2: Docker Postgres
```powershell
cd backend

# Forces Docker mode (uses backend/.env and starts containers)
.\start-backend.ps1 -Db docker
```

### Option 3: Manual Docker Startup
If you prefer to start Docker manually:

1. **Start Docker Desktop** (from Start Menu or run: `C:\Program Files\Docker\Docker\Docker Desktop.exe`)

2. **Start the database**
```powershell
cd backend
docker-compose up -d postgres
```

3. **Wait for database to be ready** (check with: `docker-compose logs postgres`)

4. **Start the backend (Docker mode)**
```powershell
cd backend
.\start-backend.ps1 -Db docker
```

## What the Script Does

1. **Checks Docker Desktop**: Verifies Docker is running
2. **Starts Database**: Runs `docker-compose up -d postgres`
3. **Waits for Database**: Waits up to 30 seconds for PostgreSQL to be ready
4. **Loads Environment Variables**: Reads from `.env.local` (local) or `.env` (docker)
5. **Starts Backend**: Runs `mvn exec:java`

## Expected Output

When successful, you should see:
```
Docker Desktop is running.
Starting database containers...
Waiting for database to be ready...
Database is ready!
Set environment variable: APP_PORT = 8080
Set environment variable: DB_HOST = localhost
Set environment variable: DB_PORT = 5433
Set environment variable: DB_NAME = nutritionguard
Set environment variable: DB_USER = nutritionguard
Set environment variable: DB_PASSWORD = nutritionguard
Set environment variable: JWT_SECRET = I52HBv3XVz8lgQkN/CQXoR6UhVnFzMPOnQeUPuZ031S66/dccyAoMxGQ2bcGig7q9ToWLwFvsLSVe3ML1+j+ew==
Starting backend application...
[INFO] Scanning for projects...
[INFO] 
[INFO] -------------< com.nutritionguard:nutritionguard-backend >--------------
[INFO] Building nutritionguard-backend 1.0.0
[INFO]   from pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- exec:3.5.0:java (default-cli) @ nutritionguard-backend ---
[INFO] Started Server on port 8080
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  4.567 s
[INFO] Finished at: 2026-03-10T09:53:00+03:00
[INFO] ------------------------------------------------------------------------
```

## Troubleshooting

### If Docker Desktop Won't Start
- Make sure Docker Desktop is installed
- Check if it's running in the system tray
- Try restarting your computer

### If Database Won't Start
```powershell
# Check Docker status
docker info

# Check container status
docker-compose ps

# Check logs
docker-compose logs postgres
```

### If Backend Won't Start
```powershell
# Check if Maven is installed
mvn --version

# Try compiling first
mvn clean compile

# Then run
mvn exec:java
```

## Next Steps

Once the backend is running on port 8080, you can:
- Access the frontend at `http://localhost:4200` (if using Angular dev server)
- Test the API endpoints
- Start developing new features

### Admin Dashboard
If you log in with an **ADMIN** account, you’ll be redirected to the admin console:
- Frontend routes: `/admin/overview`, `/admin/users`, `/admin/foods`, `/admin/alerts`
- Backend endpoints: `GET /api/admin/stats`, `GET /api/admin/analytics/food-searches`, `GET/PUT/DELETE /api/admin/users`, `GET/POST/PUT/DELETE /api/admin/foods`

To make a specific email an admin, set `ADMIN_EMAILS` in `backend/.env` (Docker) or `backend/.env.local` (local), restart the backend, then log in again.

If you still see **"Admin access required"**, promote the user directly in your local database:
```powershell
cd backend
.\make-admin.ps1 -Email "youradmin@email.com"
```
Then log out/in again so a new token is issued.

## Contact

If you continue to experience issues, please provide:
- The exact error message you're seeing
- Output from `docker info`
- Output from `mvn --version`
