param(
  # Env file for the source DB (the DB that has the latest Admin Dashboard data).
  [string]$SourceEnv = (Join-Path $PSScriptRoot ".env"),

  # Env file for the target DB (the DB you want to recover/sync).
  [string]$TargetEnv = (Join-Path $PSScriptRoot ".env.local"),

  # If set, automatically choose the richer DB as the SOURCE by comparing summaries of both env files.
  [switch]$AutoSource,

  # Optional list of env files to consider as SOURCE candidates when using -AutoSource.
  [string[]]$CandidateSourceEnvs = @(),

  # Only print summaries; do not change the target DB.
  [switch]$DryRun,

  # Optional file path to write a step-by-step debug log (useful if something hangs).
  [string]$DebugLogPath = ""
)

Set-Location $PSScriptRoot
$ErrorActionPreference = "Stop"

function Log([string]$msg) {
  if ([string]::IsNullOrWhiteSpace($DebugLogPath)) { return }
  $line = "[{0}] {1}" -f (Get-Date).ToString("s"), $msg
  try { Add-Content -Path $DebugLogPath -Value $line -Encoding utf8 } catch {}
}

function Read-EnvFile([string]$path) {
  $map = @{}
  if (!(Test-Path $path)) { return $map }
  Get-Content $path | ForEach-Object {
    if ($_ -match '^\s*#' -or $_ -match '^\s*$') { return }
    $kv = $_ -split '=', 2
    if ($kv.Count -ne 2) { return }
    $key = $kv[0].Trim()
    $val = $kv[1].Trim().Trim('"')
    if ($key) { $map[$key] = $val }
  }
  return $map
}

function Resolve-EnvPath([string]$path) {
  if ([string]::IsNullOrWhiteSpace($path)) { return $path }
  try {
    return (Resolve-Path -LiteralPath $path -ErrorAction Stop).Path
  } catch {
    return $path
  }
}

function Score-Summary([pscustomobject]$s) {
  if (-not $s.ok) { return -1 }
  # Weight image/definition/health_effect highest (admin dashboard completeness).
  return
    ($s.foods_with_images * 1000000) +
    ($s.foods_with_definitions * 10000) +
    ($s.foods_with_health_effect * 100) +
    ($s.users_count * 10) +
    ($s.profiles_count * 1) +
    ($s.foods_count * 1)
}

function Require-Command([string]$name) {
  $cmd = Get-Command $name -ErrorAction SilentlyContinue
  if (-not $cmd) {
    throw "Missing required command '$name'. Install PostgreSQL client tools (psql/pg_dump) and ensure they are on PATH."
  }
}

function To-DbCfg([hashtable]$envMap) {
  foreach ($k in @("DB_HOST", "DB_PORT", "DB_NAME", "DB_USER", "DB_PASSWORD")) {
    if (-not $envMap.ContainsKey($k) -or [string]::IsNullOrWhiteSpace($envMap[$k])) {
      throw "Missing $k in env file."
    }
  }
  return [pscustomobject]@{
    DB_HOST = $envMap["DB_HOST"]
    DB_PORT = [int]$envMap["DB_PORT"]
    DB_NAME = $envMap["DB_NAME"]
    DB_USER = $envMap["DB_USER"]
    DB_PASSWORD = $envMap["DB_PASSWORD"]
  }
}

function Invoke-Psql([pscustomobject]$cfg, [string]$sql) {
  $env:PGPASSWORD = $cfg.DB_PASSWORD
  $env:PGCONNECT_TIMEOUT = "5"
  try {
    Log ("psql start: {0}:{1}/{2} user={3}" -f $cfg.DB_HOST, $cfg.DB_PORT, $cfg.DB_NAME, $cfg.DB_USER)
    $out = psql -w -v ON_ERROR_STOP=1 -h $cfg.DB_HOST -p $cfg.DB_PORT -U $cfg.DB_USER -d $cfg.DB_NAME -qtAX -F '|' -c $sql 2>$null
    if ($LASTEXITCODE -ne 0) { throw "psql exit $LASTEXITCODE" }
    Log "psql ok"
    return $out
  } finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:PGCONNECT_TIMEOUT -ErrorAction SilentlyContinue
  }
}

function Invoke-PsqlFile([pscustomobject]$cfg, [string]$filePath) {
  $env:PGPASSWORD = $cfg.DB_PASSWORD
  $env:PGCONNECT_TIMEOUT = "5"
  try {
    psql -w -v ON_ERROR_STOP=1 -h $cfg.DB_HOST -p $cfg.DB_PORT -U $cfg.DB_USER -d $cfg.DB_NAME -f $filePath | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "psql exit $LASTEXITCODE" }
  } finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:PGCONNECT_TIMEOUT -ErrorAction SilentlyContinue
  }
}

function Get-DbSummary([string]$label, [pscustomobject]$cfg) {
  try {
    $sql = @"
SELECT
  (SELECT COUNT(*) FROM public.users) AS users_count,
  (SELECT COUNT(*) FROM public.profiles) AS profiles_count,
  (SELECT COUNT(*) FROM public.food_items) AS foods_count,
  (SELECT COUNT(*) FROM public.food_items WHERE image_url IS NOT NULL AND BTRIM(image_url) <> '') AS foods_with_images,
  (SELECT COUNT(*) FROM public.food_items WHERE definition IS NOT NULL AND BTRIM(definition) <> '') AS foods_with_definitions,
  (SELECT COUNT(*) FROM public.food_items WHERE health_effect IS NOT NULL AND BTRIM(health_effect) <> '') AS foods_with_health_effect,
  (SELECT COALESCE(MAX(created_at)::text,'') FROM public.users) AS latest_user_created_at,
  (SELECT COALESCE(MAX(updated_at)::text,'') FROM public.profiles) AS latest_profile_updated_at
;
"@
    $out = Invoke-Psql -cfg $cfg -sql $sql
    $parts = $out -split '\|'
    return [pscustomobject]@{
      label = $label
      host = $cfg.DB_HOST
      port = $cfg.DB_PORT
      db = $cfg.DB_NAME
      user = $cfg.DB_USER
      users_count = [int]$parts[0]
      profiles_count = [int]$parts[1]
      foods_count = [int]$parts[2]
      foods_with_images = [int]$parts[3]
      foods_with_definitions = [int]$parts[4]
      foods_with_health_effect = [int]$parts[5]
      latest_user_created_at = $parts[6]
      latest_profile_updated_at = $parts[7]
      ok = $true
    }
  } catch {
    return [pscustomobject]@{
      label = $label
      host = $cfg.DB_HOST
      port = $cfg.DB_PORT
      db = $cfg.DB_NAME
      user = $cfg.DB_USER
      error = $_.Exception.Message
      ok = $false
    }
  }
}

function Get-SourceTableRef([pscustomobject]$sourceCfg, [string]$tableName) {
  if ($tableName -notin @("users", "profiles", "food_items")) {
    throw "Unsupported table name: $tableName"
  }

  $sql = @"
SELECT table_schema
FROM information_schema.tables
WHERE table_name = '$tableName'
  AND table_type = 'BASE TABLE'
  AND table_schema NOT IN ('pg_catalog','information_schema')
ORDER BY
  CASE WHEN table_schema = 'public' THEN 0 WHEN table_schema = 'ng' THEN 1 ELSE 2 END,
  table_schema
LIMIT 1;
"@
  $schema = (Invoke-Psql -cfg $sourceCfg -sql $sql).Trim()
  if ([string]::IsNullOrWhiteSpace($schema)) {
    throw "Could not locate table '$tableName' in source DB."
  }
  return "$schema.$tableName"
}

function Dump-And-Load([pscustomobject]$sourceCfg, [pscustomobject]$targetCfg, [string]$stagingSchema, [string]$tmpDir, [string]$tableName) {
  $dumpPath = Join-Path $tmpDir ("dump_{0}.sql" -f $tableName)
  $tableRef = Get-SourceTableRef -sourceCfg $sourceCfg -tableName $tableName
  $fromSchema = ($tableRef -split '\.')[0]

  $env:PGPASSWORD = $sourceCfg.DB_PASSWORD
  $env:PGCONNECT_TIMEOUT = "5"
  try {
    pg_dump -w -h $sourceCfg.DB_HOST -p $sourceCfg.DB_PORT -U $sourceCfg.DB_USER -d $sourceCfg.DB_NAME --data-only --column-inserts --rows-per-insert=500 --no-owner --no-privileges --table=$tableRef |
      Out-File -FilePath $dumpPath -Encoding utf8
    if ($LASTEXITCODE -ne 0) { throw "pg_dump exit $LASTEXITCODE" }
  } finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:PGCONNECT_TIMEOUT -ErrorAction SilentlyContinue
  }

  $from = "INSERT INTO $fromSchema."
  $to = "INSERT INTO $stagingSchema."
  (Get-Content $dumpPath -Raw) -replace [regex]::Escape($from), $to |
    Set-Content -Path $dumpPath -Encoding utf8

  Write-Host "Loading $tableName into $stagingSchema..."
  Invoke-PsqlFile -cfg $targetCfg -filePath $dumpPath
}

Require-Command "psql"
Require-Command "pg_dump"
Log "Commands resolved: psql, pg_dump"

$SourceEnv = Resolve-EnvPath $SourceEnv
$TargetEnv = Resolve-EnvPath $TargetEnv

$envA = Read-EnvFile $SourceEnv
$envB = Read-EnvFile $TargetEnv
if ($envA.Count -eq 0) { throw "Source env file not found or empty: $SourceEnv" }
if ($envB.Count -eq 0) { throw "Target env file not found or empty: $TargetEnv" }
Log "Env files loaded: SourceEnv=$SourceEnv TargetEnv=$TargetEnv"

$cfgA = To-DbCfg $envA
$cfgB = To-DbCfg $envB
Log "DB configs parsed"

if ($AutoSource) {
  # Keep TARGET fixed; choose the best reachable SOURCE candidate.
  $targetSummary = Get-DbSummary "Target ($TargetEnv)" $cfgB

  $candidates = @()
  if ($CandidateSourceEnvs.Count -gt 0) {
    $candidates = $CandidateSourceEnvs | ForEach-Object { Resolve-EnvPath $_ }
  } else {
    $candidates = Get-ChildItem -File -Force -Filter ".env*" |
      Where-Object {
        $_.Name -notin @(".env.local", ".env.local.example", ".env.example")
      } |
      Select-Object -ExpandProperty FullName
  }

  # Ensure SourceEnv is included first if it's a distinct path.
  $candidates = @($SourceEnv) + ($candidates | Where-Object { $_ -ne $SourceEnv })
  $candidates = $candidates | Where-Object { $_ -ne $TargetEnv } | Select-Object -Unique

  $best = $null
  foreach ($envPath in $candidates) {
    $envMap = Read-EnvFile $envPath
    if ($envMap.Count -eq 0) { continue }
    $cfg = To-DbCfg $envMap
    $sum = Get-DbSummary "Candidate ($envPath)" $cfg
    $sum | Format-List

    if ($best -eq $null -or (Score-Summary $sum) -gt (Score-Summary $best)) {
      $best = $sum
      $cfgA = $cfg
      $SourceEnv = $envPath
    }
  }

  if ($best -eq $null -or -not $best.ok) {
    Write-Host ""
    Write-Host "AutoSource: no reachable SOURCE candidate DB found (other than the target)."
    Write-Host "Target summary:"
    $targetSummary | Format-List
    exit 0
  }

  Write-Host ""
  Write-Host "AutoSource: selected SOURCE env: $SourceEnv"
}

$sourceCfg = $cfgA
$targetCfg = $cfgB

$sourceSummary = Get-DbSummary "Source ($SourceEnv)" $sourceCfg
$targetSummary = Get-DbSummary "Target ($TargetEnv)" $targetCfg

Write-Host ""
Write-Host "DB summary:"
$sourceSummary | Format-List
$targetSummary | Format-List

if (-not $targetSummary.ok) { throw "Cannot connect to target DB ($TargetEnv): $($targetSummary.error)" }

if ($DryRun) {
  Write-Host ""
  Write-Host "DryRun: no changes applied."
  exit 0
}

if (-not $sourceSummary.ok) { throw "Cannot connect to source DB ($SourceEnv): $($sourceSummary.error)" }

# Backup snapshot (prevents accidental data loss).
$stamp = (Get-Date).ToString("yyyyMMdd_HHmmss")
$backupSchema = "_ng_backup_$stamp"
Write-Host ""
Write-Host "Creating target backup schema: $backupSchema"
Invoke-Psql -cfg $targetCfg -sql "CREATE SCHEMA IF NOT EXISTS $backupSchema;" | Out-Null
Invoke-Psql -cfg $targetCfg -sql "CREATE TABLE IF NOT EXISTS $backupSchema.users AS TABLE public.users;" | Out-Null
Invoke-Psql -cfg $targetCfg -sql "CREATE TABLE IF NOT EXISTS $backupSchema.profiles AS TABLE public.profiles;" | Out-Null
Invoke-Psql -cfg $targetCfg -sql "CREATE TABLE IF NOT EXISTS $backupSchema.food_items AS TABLE public.food_items;" | Out-Null

# Staging schema inside the TARGET DB.
$stagingSchema = "_ng_sync"
Write-Host "Preparing staging schema: $stagingSchema"
Invoke-Psql -cfg $targetCfg -sql "DROP SCHEMA IF EXISTS $stagingSchema CASCADE; CREATE SCHEMA $stagingSchema;" | Out-Null
Invoke-Psql -cfg $targetCfg -sql "CREATE TABLE $stagingSchema.users (LIKE public.users INCLUDING ALL);" | Out-Null
Invoke-Psql -cfg $targetCfg -sql "CREATE TABLE $stagingSchema.profiles (LIKE public.profiles INCLUDING ALL);" | Out-Null
Invoke-Psql -cfg $targetCfg -sql "CREATE TABLE $stagingSchema.food_items (LIKE public.food_items INCLUDING ALL);" | Out-Null

$tmpDir = Join-Path $PSScriptRoot ".tmp_sync"
New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null

Write-Host ""
Write-Host "Dumping from source ($SourceEnv) and loading into target staging ($TargetEnv)..."
Dump-And-Load $sourceCfg $targetCfg $stagingSchema $tmpDir "users"
Dump-And-Load $sourceCfg $targetCfg $stagingSchema $tmpDir "profiles"
Dump-And-Load $sourceCfg $targetCfg $stagingSchema $tmpDir "food_items"

# Ensure admin/user columns exist (idempotent).
Invoke-Psql -cfg $targetCfg -sql "ALTER TABLE public.users ADD COLUMN IF NOT EXISTS role TEXT NOT NULL DEFAULT 'USER';" | Out-Null
Invoke-Psql -cfg $targetCfg -sql "ALTER TABLE public.users ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT true;" | Out-Null

Write-Host "Merging users..."

$mergeUsersSql = @"
WITH incoming AS (
  SELECT
    id AS source_id,
    LOWER(TRIM(email)) AS email_norm,
    password_hash,
    created_at,
    COALESCE(role, 'USER') AS role,
    COALESCE(is_active, true) AS is_active
  FROM $stagingSchema.users
  WHERE email IS NOT NULL AND BTRIM(email) <> ''
)
-- Update existing users by normalized email (prevents duplicate users differing only by id/casing).
UPDATE public.users u
SET
  password_hash = COALESCE(i.password_hash, u.password_hash),
  role = COALESCE(i.role, u.role),
  is_active = COALESCE(i.is_active, u.is_active)
FROM incoming i
WHERE LOWER(TRIM(u.email)) = i.email_norm;

-- Insert users that don't exist yet (by normalized email). Use email normalized.
INSERT INTO public.users (id, email, password_hash, created_at, role, is_active)
SELECT i.source_id, i.email_norm, i.password_hash, i.created_at, i.role, i.is_active
FROM incoming i
WHERE NOT EXISTS (
  SELECT 1 FROM public.users u WHERE LOWER(TRIM(u.email)) = i.email_norm
)
ON CONFLICT (id) DO UPDATE
SET
  email = EXCLUDED.email,
  password_hash = EXCLUDED.password_hash,
  role = EXCLUDED.role,
  is_active = EXCLUDED.is_active,
  created_at = COALESCE(public.users.created_at, EXCLUDED.created_at);
"@
Invoke-Psql -cfg $targetCfg -sql $mergeUsersSql | Out-Null

Write-Host "Merging profiles..."
$mergeProfilesSql = @"
INSERT INTO public.profiles (user_id, age, gender, height_cm, weight_kg, activity_level, goal, health_metadata, updated_at)
SELECT
  COALESCE(uByEmail.id, uById.id) AS user_id,
  p.age,
  p.gender,
  p.height_cm,
  p.weight_kg,
  p.activity_level,
  p.goal,
  p.health_metadata,
  p.updated_at
FROM $stagingSchema.profiles p
LEFT JOIN $stagingSchema.users su ON su.id = p.user_id
LEFT JOIN public.users uByEmail ON LOWER(TRIM(uByEmail.email)) = LOWER(TRIM(su.email))
LEFT JOIN public.users uById ON uById.id = p.user_id
WHERE COALESCE(uByEmail.id, uById.id) IS NOT NULL
ON CONFLICT (user_id) DO UPDATE
SET
  age = EXCLUDED.age,
  gender = EXCLUDED.gender,
  height_cm = EXCLUDED.height_cm,
  weight_kg = EXCLUDED.weight_kg,
  activity_level = EXCLUDED.activity_level,
  goal = EXCLUDED.goal,
  health_metadata = EXCLUDED.health_metadata,
  updated_at = EXCLUDED.updated_at;
"@
Invoke-Psql -cfg $targetCfg -sql $mergeProfilesSql | Out-Null

Write-Host "Merging food items (including images/definitions)..."
$mergeFoodsSql = @"
INSERT INTO public.food_items (
  food_name,
  meal_type,
  category,
  traffic_light,
  portion_amount,
  portion_unit,
  calories_kcal,
  protein_g,
  carbs_g,
  fat_g,
  sugar_g,
  sodium_mg,
  cholesterol_mg,
  fiber_g,
  glycemic_index,
  ingredients,
  image_url,
  definition,
  health_effect,
  vitamins
)
SELECT
  food_name,
  meal_type,
  category,
  traffic_light,
  portion_amount,
  portion_unit,
  calories_kcal,
  protein_g,
  carbs_g,
  fat_g,
  sugar_g,
  sodium_mg,
  cholesterol_mg,
  fiber_g,
  glycemic_index,
  ingredients,
  image_url,
  definition,
  health_effect,
  vitamins
FROM $stagingSchema.food_items
ON CONFLICT (food_name, meal_type) DO UPDATE
SET
  category = COALESCE(EXCLUDED.category, public.food_items.category),
  traffic_light = COALESCE(EXCLUDED.traffic_light, public.food_items.traffic_light),
  portion_amount = COALESCE(EXCLUDED.portion_amount, public.food_items.portion_amount),
  portion_unit = COALESCE(EXCLUDED.portion_unit, public.food_items.portion_unit),
  calories_kcal = COALESCE(EXCLUDED.calories_kcal, public.food_items.calories_kcal),
  protein_g = COALESCE(EXCLUDED.protein_g, public.food_items.protein_g),
  carbs_g = COALESCE(EXCLUDED.carbs_g, public.food_items.carbs_g),
  fat_g = COALESCE(EXCLUDED.fat_g, public.food_items.fat_g),
  sugar_g = COALESCE(EXCLUDED.sugar_g, public.food_items.sugar_g),
  sodium_mg = COALESCE(EXCLUDED.sodium_mg, public.food_items.sodium_mg),
  cholesterol_mg = COALESCE(EXCLUDED.cholesterol_mg, public.food_items.cholesterol_mg),
  fiber_g = COALESCE(EXCLUDED.fiber_g, public.food_items.fiber_g),
  glycemic_index = COALESCE(EXCLUDED.glycemic_index, public.food_items.glycemic_index),
  ingredients = COALESCE(EXCLUDED.ingredients, public.food_items.ingredients),
  image_url = COALESCE(EXCLUDED.image_url, public.food_items.image_url),
  definition = COALESCE(EXCLUDED.definition, public.food_items.definition),
  health_effect = COALESCE(EXCLUDED.health_effect, public.food_items.health_effect),
  vitamins = COALESCE(EXCLUDED.vitamins, public.food_items.vitamins);
"@
Invoke-Psql -cfg $targetCfg -sql $mergeFoodsSql | Out-Null

Write-Host "Fixing food_items id sequence..."
Invoke-Psql -cfg $targetCfg -sql "SELECT setval(pg_get_serial_sequence('public.food_items','id'), (SELECT COALESCE(MAX(id), 1) FROM public.food_items), true);" | Out-Null

Write-Host ""
Write-Host "Sync complete. Post-sync summary:"
(Get-DbSummary "Target ($TargetEnv)" $targetCfg) | Format-List
Write-Host "Backup snapshot saved in schema: $backupSchema"
