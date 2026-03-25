param(
  [Parameter(Mandatory = $true)]
  [string]$Email
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

$env:PGPASSWORD = $dbPassword
$safeEmail = $Email.Trim().ToLower().Replace("'", "''")

Write-Host "Promoting user to ADMIN: $safeEmail"

$sql = @"
ALTER TABLE public.users
  ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT true;

UPDATE public.users
SET role = 'ADMIN'
WHERE LOWER(email) = '$safeEmail';

SELECT id, email, role, is_active, created_at
FROM public.users
WHERE LOWER(email) = '$safeEmail';
"@

psql -v ON_ERROR_STOP=1 -h $dbHost -p $dbPort -U $dbUser -d $dbName -c $sql
