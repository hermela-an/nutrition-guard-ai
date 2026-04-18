# Initializes the NutritionGuard schema in your local Postgres database.
# Reads connection settings from .env.local and runs migrations into DB_SCHEMA (default: public).

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

    $displayValue = $envVarValue
    if ($envVarName -in @("DB_PASSWORD", "JWT_SECRET")) { $displayValue = "****" }
    Write-Host "Set environment variable: $envVarName = $displayValue"
  }
}

$dbHost = $env:DB_HOST
$dbPort = $env:DB_PORT
$dbName = $env:DB_NAME
$dbUser = $env:DB_USER
$dbPassword = $env:DB_PASSWORD
$dbSchema = $env:DB_SCHEMA
if ([string]::IsNullOrWhiteSpace($dbSchema)) { $dbSchema = "public" }
if ($dbSchema.Trim().ToLower() -ne "public") {
  Write-Error "Local DB initializer currently supports DB_SCHEMA=public (migrations reference public.*). Update DB_SCHEMA in .env.local."
  exit 1
}

$migrationsDir = Join-Path $PSScriptRoot "..\\database\\migrations"
if (!(Test-Path $migrationsDir)) {
  Write-Error "Missing migrations directory: $migrationsDir"
  exit 1
}
$migrations = Get-ChildItem -Path $migrationsDir -Filter "*.sql" | Sort-Object Name
if ($migrations.Count -eq 0) {
  Write-Error "No migration files found in: $migrationsDir"
  exit 1
}

$seed1 = Join-Path $PSScriptRoot "..\\database\\seed\\001_food_items.sql"
$seed2 = Join-Path $PSScriptRoot "..\\database\\seed\\002_import_food_items_from_json.sql"

# We use psql ON_ERROR_STOP so failures are obvious.
$env:PGPASSWORD = $dbPassword

Write-Host ""
Write-Host "Initializing schema '$dbSchema' in database '$dbName' on $dbHost`:$dbPort ..."

foreach ($m in $migrations) {
  Write-Host "Applying migration: $($m.Name)"
  psql -v ON_ERROR_STOP=1 -h $dbHost -p $dbPort -U $dbUser -d $dbName -f $m.FullName
}

if (Test-Path $seed1) {
  Write-Host "Applying seed: 001_food_items.sql (idempotent)..."
  psql -v ON_ERROR_STOP=1 -h $dbHost -p $dbPort -U $dbUser -d $dbName -f $seed1
} else {
  Write-Host "Seed file not found (skipping): $seed1"
}

if (Test-Path $seed2) {
  Write-Host "Applying seed: 002_import_food_items_from_json.sql (idempotent)..."
  psql -v ON_ERROR_STOP=1 -h $dbHost -p $dbPort -U $dbUser -d $dbName -f $seed2
} else {
  Write-Host "Seed file not found (skipping): $seed2"
}

Write-Host ""
Write-Host "Local DB initialized."
