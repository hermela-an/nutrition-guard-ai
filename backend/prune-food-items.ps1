# Prunes public.food_items to a small keep-list.
# Reads DB connection settings from backend/.env.local (same as init-local-db.ps1).

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
$dbSchema = $env:DB_SCHEMA
if ([string]::IsNullOrWhiteSpace($dbSchema)) { $dbSchema = "public" }
if ($dbSchema.Trim().ToLower() -ne "public") {
  Write-Error "This prune script targets public.food_items. Set DB_SCHEMA=public in .env.local."
  exit 1
}

$sqlFile = Join-Path $PSScriptRoot "..\\database\\maintenance\\prune_food_items_keep_list.sql"
if (!(Test-Path $sqlFile)) {
  Write-Error "Missing SQL file: $sqlFile"
  exit 1
}

$env:PGPASSWORD = $dbPassword

Write-Host ""
Write-Host "Pruning public.food_items in database '$dbName' on $dbHost`:$dbPort ..."
psql -v ON_ERROR_STOP=1 -h $dbHost -p $dbPort -U $dbUser -d $dbName -f $sqlFile

if ($LASTEXITCODE -ne 0) {
  Write-Error "Prune failed (psql exit code: $LASTEXITCODE). No changes were committed."
  exit $LASTEXITCODE
}

Write-Host ""
Write-Host "Done."
