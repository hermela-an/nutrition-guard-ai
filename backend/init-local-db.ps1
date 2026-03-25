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
    Write-Host "Set environment variable: $envVarName = $envVarValue"
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

$seed = Join-Path $PSScriptRoot "..\\database\\seed\\001_food_items.sql"

# We use psql ON_ERROR_STOP so failures are obvious.
$env:PGPASSWORD = $dbPassword

Write-Host ""
Write-Host "Initializing schema '$dbSchema' in database '$dbName' on $dbHost`:$dbPort ..."

foreach ($m in $migrations) {
  Write-Host "Applying migration: $($m.Name)"
  psql -v ON_ERROR_STOP=1 -h $dbHost -p $dbPort -U $dbUser -d $dbName -f $m.FullName
}

if (Test-Path $seed) {
  $countRaw = psql -t -A -h $dbHost -p $dbPort -U $dbUser -d $dbName -c "SELECT COUNT(*) FROM public.food_items;" 2>$null
  $count = 0
  [int]::TryParse(($countRaw | Select-Object -First 1), [ref]$count) | Out-Null

  if ($count -gt 0) {
    Write-Host "Seed data already present (food_items count=$count). Skipping seed."
  } else {
    Write-Host "Seeding food items..."
    psql -v ON_ERROR_STOP=1 -h $dbHost -p $dbPort -U $dbUser -d $dbName -f $seed
  }
} else {
  Write-Host "Seed file not found (skipping): $seed"
}

Write-Host ""
Write-Host "Local DB initialized."
