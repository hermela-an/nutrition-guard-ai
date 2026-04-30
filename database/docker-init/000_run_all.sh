#!/bin/sh
set -eu

echo "NutritionGuard: applying migrations + seeds..."

if [ ! -d "/ng-init/migrations" ]; then
  echo "Missing /ng-init/migrations mount" >&2
  exit 1
fi

if [ ! -d "/ng-init/seed" ]; then
  echo "Missing /ng-init/seed mount" >&2
  exit 1
fi

echo "Applying migrations..."
if ! ls /ng-init/migrations/*.sql >/dev/null 2>&1; then
  echo "No migration .sql files found under /ng-init/migrations" >&2
  exit 1
fi
for f in /ng-init/migrations/*.sql; do
  echo " - $f"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -f "$f"
done

if [ "${NUTRITIONGUARD_RUN_SEEDS:-}" != "1" ]; then
  echo "Skipping seeds (set NUTRITIONGUARD_RUN_SEEDS=1 to enable)."
  echo "NutritionGuard: init complete."
  exit 0
fi

echo "Applying seeds..."
if ! ls /ng-init/seed/*.sql >/dev/null 2>&1; then
  echo "No seed .sql files found under /ng-init/seed" >&2
  exit 1
fi
for f in /ng-init/seed/*.sql; do
  echo " - $f"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -f "$f"
done

echo "NutritionGuard: init complete."
