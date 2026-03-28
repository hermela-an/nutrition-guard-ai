# Starts the backend using local Postgres (no Docker).
# Reads environment variables from .env.local in this folder.

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

$port = 8080
if ($env:APP_PORT) { $port = [int]$env:APP_PORT }
$listener = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($listener) {
  $procId = $listener.OwningProcess
  $proc = $null
  try { $proc = Get-Process -Id $procId -ErrorAction Stop } catch {}
  $name = if ($proc) { $proc.ProcessName } else { "unknown" }
  Write-Error "Port $port is already in use by PID $procId ($name). Stop that backend first (or change APP_PORT in .env.local)."
  exit 1
}

Write-Host ""
Write-Host "Starting backend application (local Postgres)..."
Set-Location $PSScriptRoot
mvn -DskipTests compile exec:java
