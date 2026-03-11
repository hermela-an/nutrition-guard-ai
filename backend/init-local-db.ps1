# Initializes the NutritionGuard schema in your local Postgres database.
# Reads connection settings from .env.local and runs the schema migration into DB_SCHEMA (default: ng).

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
if ([string]::IsNullOrWhiteSpace($dbSchema)) { $dbSchema = "ng" }
if ($dbSchema -ne "ng") {
  Write-Error "This initializer uses the fixed schema 'ng'. Set DB_SCHEMA=ng in .env.local."
  exit 1
}

$migration = Join-Path $PSScriptRoot "..\\database\\migrations\\001_init_ng_schema.sql"
if (!(Test-Path $migration)) {
  Write-Error "Missing migration file: $migration"
  exit 1
}

# We use psql ON_ERROR_STOP so failures are obvious.
$env:PGPASSWORD = $dbPassword

Write-Host ""
Write-Host "Initializing schema '$dbSchema' in database '$dbName' on $dbHost`:$dbPort ..."

psql -v ON_ERROR_STOP=1 -h $dbHost -p $dbPort -U $dbUser -d $dbName -c "CREATE SCHEMA IF NOT EXISTS $dbSchema;"
psql -v ON_ERROR_STOP=1 -h $dbHost -p $dbPort -U $dbUser -d $dbName -c "ALTER ROLE $dbUser IN DATABASE $dbName SET search_path TO $dbSchema,public;" 2>$null
psql -v ON_ERROR_STOP=1 -h $dbHost -p $dbPort -U $dbUser -d $dbName -f $migration

Write-Host ""
Write-Host "Local DB initialized."
