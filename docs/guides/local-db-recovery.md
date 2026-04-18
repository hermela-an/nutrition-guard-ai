# Local DB Recovery (Food + Users)

If the Admin Dashboard shows different data than your `psql` session, the backend is almost always connected to a **different database** (or a different Postgres instance).

## 1) Confirm which DB the backend uses

When the backend starts, it prints a `Config: ...` line showing:
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_SCHEMA`, and `ENV_FILE`

Make sure:
- `ENV_FILE` is `backend\.env.local` when using local Postgres
- `DB_SCHEMA=public`

Start explicitly:
- Local: `backend\start-backend.ps1 -Db local`
- Docker: `backend\start-backend.ps1 -Db docker`

## 2) Verify counts in the current local DB

Connect:

```powershell
psql -h localhost -p 5432 -U postgres -d nutrition_guard
```

Run:

```sql
SELECT COUNT(*) AS foods FROM public.food_items;
SELECT COUNT(*) AS users FROM public.users;
SELECT role, COUNT(*) FROM public.users GROUP BY role ORDER BY role;
SELECT COUNT(*) AS foods_with_images FROM public.food_items WHERE image_url IS NOT NULL AND BTRIM(image_url) <> '';
```

## 3) Restore/backfill the local food list (seed data)

Run the local initializer (idempotent; safe to re-run):

```powershell
cd backend
.\init-local-db.ps1
```

This applies all migrations and then runs both:
- `database/seed/001_food_items.sql`
- `database/seed/002_import_food_items_from_json.sql`

## 4) If foods/users exist in another local database

List DBs:

```powershell
psql -h localhost -p 5432 -U postgres -l
```

Then connect to a candidate DB and compare counts:

```powershell
psql -h localhost -p 5432 -U postgres -d <db_name>
```

```sql
SELECT current_database();
SELECT COUNT(*) AS foods FROM public.food_items;
SELECT COUNT(*) AS users FROM public.users;
```

If you find the “old” DB with the missing rows, the safest sync approach is:
1) Export those tables from the old DB (via `pg_dump -t public.food_items -t public.users`)
2) Import into the current DB (via `psql`)

## 4b) If the “latest” data is in Docker Postgres (recommended sync)

If your Admin Dashboard previously showed multiple users and foods-with-images, but your current local DB has only seed/default data, the “latest” data is often sitting in the Docker volume (Postgres on port `5433` for this repo).

1) Start Docker Postgres (repo root):

```powershell
docker compose up -d postgres
```

2) Run a safe merge into your local DB (creates a backup schema first):

```powershell
cd backend
.\sync-db.ps1 -SourceEnv .\.env -TargetEnv .\.env.local
```

If you’re not sure which env file points to the latest DB, let the script choose automatically:

```powershell
cd backend
.\sync-db.ps1 -AutoSource
```

## 5) If tables exist in another schema (e.g., `ng`)

List schemas that contain a `users` or `food_items` table:

```sql
SELECT table_schema, table_name
FROM information_schema.tables
WHERE table_name IN ('users', 'food_items')
ORDER BY table_schema, table_name;
```

If you see `ng.food_items` or `ng.users`, apply the repo migrations (they move/standardize schemas):

```powershell
cd backend
.\init-local-db.ps1
```

