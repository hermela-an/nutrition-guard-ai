param(
  [switch]$WhatIf
)

Set-Location $PSScriptRoot

$envFile = Join-Path $PSScriptRoot ".env.local"
if (!(Test-Path $envFile)) {
  Write-Error "Missing $envFile. Create it from .env.local.example first."
  exit 1
}

Get-Content $envFile | ForEach-Object {
  if ($_ -match '^\s*#' -or $_ -match '^\s*$') { return }
  $name, $value = $_ -split '=', 2
  if ($null -ne $name -and $null -ne $value) {
    $envVarName = $name.Trim()
    $envVarValue = $value.Trim().Trim('"')
    [System.Environment]::SetEnvironmentVariable($envVarName, $envVarValue, "Process")
  }
}

$dbHost = $env:DB_HOST
$dbPort = $env:DB_PORT
$dbName = $env:DB_NAME
$dbUser = $env:DB_USER
$dbPassword = $env:DB_PASSWORD

if ([string]::IsNullOrWhiteSpace($dbHost) -or [string]::IsNullOrWhiteSpace($dbPort) -or [string]::IsNullOrWhiteSpace($dbName) -or [string]::IsNullOrWhiteSpace($dbUser)) {
  Write-Error "Missing DB_* variables in $envFile."
  exit 1
}

if ($WhatIf) {
  Write-Host "WHATIF: will renumber public.food_items.id to be consecutive starting at 1 (and update all foreign keys)."
  Write-Host "Target DB: $dbHost`:$dbPort / $dbName (user=$dbUser)"
  exit 0
}

$env:PGPASSWORD = $dbPassword

Write-Warning "This will rewrite primary keys for public.food_items and update all referencing foreign keys in the database."
Write-Warning "Make sure the backend is stopped and you have a DB backup/snapshot if you care about preserving IDs."
Write-Host ""

$sql = @'
BEGIN;

-- Lock tables to avoid concurrent writes while renumbering.
LOCK TABLE public.food_items IN ACCESS EXCLUSIVE MODE;

-- Capture and drop all FKs that reference public.food_items(id), then recreate them later.
CREATE TEMP TABLE _ng_fk_defs AS
SELECT
  conrelid::regclass::text AS table_name,
  conname AS constraint_name,
  pg_get_constraintdef(oid) AS constraint_def
FROM pg_constraint
WHERE contype = 'f'
  AND confrelid = 'public.food_items'::regclass;

-- Capture FK columns (only supports single-column references).
CREATE TEMP TABLE _ng_fk_cols AS
SELECT
  conrelid::regclass::text AS table_name,
  conname AS constraint_name,
  (SELECT attname FROM pg_attribute WHERE attrelid = conrelid AND attnum = conkey[1]) AS column_name
FROM pg_constraint
WHERE contype = 'f'
  AND confrelid = 'public.food_items'::regclass;

DO $$
DECLARE bad int;
BEGIN
  SELECT COUNT(*) INTO bad
  FROM pg_constraint
  WHERE contype = 'f'
    AND confrelid = 'public.food_items'::regclass
    AND array_length(conkey, 1) <> 1;
  IF bad > 0 THEN
    RAISE EXCEPTION 'Renumbering only supports single-column foreign keys to food_items(id). Found % multi-column constraints.', bad;
  END IF;
END $$;

DO $$
DECLARE r record;
BEGIN
  FOR r IN SELECT * FROM _ng_fk_defs LOOP
    EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', r.table_name, r.constraint_name);
  END LOOP;
END $$;

-- Map old ids -> new consecutive ids.
CREATE TEMP TABLE _ng_id_map AS
SELECT id AS old_id, row_number() OVER (ORDER BY id)::int AS new_id
FROM public.food_items
ORDER BY id;

-- Phase 1: move food_items ids into a collision-free negative space.
UPDATE public.food_items SET id = -id;

-- Phase 2: update referencing FKs to negative ids (so joins remain easy).
DO $$
DECLARE r record;
BEGIN
  FOR r IN SELECT * FROM _ng_fk_cols LOOP
    EXECUTE format('LOCK TABLE %s IN ACCESS EXCLUSIVE MODE', r.table_name);
    EXECUTE format('UPDATE %s SET %I = -%I', r.table_name, r.column_name, r.column_name);
  END LOOP;
END $$;

-- Phase 3: assign new consecutive ids back onto food_items.
UPDATE public.food_items f
SET id = m.new_id
FROM _ng_id_map m
WHERE f.id = -m.old_id;

-- Phase 4: rewrite FKs to the new ids.
DO $$
DECLARE r record;
BEGIN
  FOR r IN SELECT * FROM _ng_fk_cols LOOP
    EXECUTE format(
      'UPDATE %s t SET %I = m.new_id FROM _ng_id_map m WHERE t.%I = -m.old_id',
      r.table_name,
      r.column_name,
      r.column_name
    );
  END LOOP;
END $$;

-- Reset sequence to max(id).
SELECT setval('public.food_items_id_seq', (SELECT COALESCE(MAX(id), 0) FROM public.food_items), true);

-- Recreate original FK constraints.
DO $$
DECLARE r record;
BEGIN
  FOR r IN SELECT * FROM _ng_fk_defs LOOP
    EXECUTE format('ALTER TABLE %s ADD CONSTRAINT %I %s', r.table_name, r.constraint_name, r.constraint_def);
  END LOOP;
END $$;

COMMIT;

-- Quick check: show first/last few ids
SELECT MIN(id) AS min_id, MAX(id) AS max_id, COUNT(*) AS total FROM public.food_items;
'@

psql -v ON_ERROR_STOP=1 -h $dbHost -p $dbPort -U $dbUser -d $dbName -c $sql
