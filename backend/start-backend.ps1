param(
  [ValidateSet("auto", "local", "docker")]
  [string]$Db = "auto"
)

Set-Location $PSScriptRoot

$envLocal = Join-Path $PSScriptRoot ".env.local"
$envDocker = Join-Path $PSScriptRoot ".env"

if ($Db -eq "auto") {
  $Db = (Test-Path $envLocal) ? "local" : "docker"
}

if ($Db -eq "docker") {
  # Check if Docker Desktop is running
  $dockerRunning = $false
  try {
    $dockerInfo = docker info 2>$null
    if ($LASTEXITCODE -eq 0) {
      $dockerRunning = $true
      Write-Host "Docker Desktop is running."
    }
  } catch {
    # Docker not running
  }

  if (-not $dockerRunning) {
    Write-Warning "Docker Desktop is not running. Starting Docker containers..."
    try {
      # Try to start Docker Desktop
      Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe" -ArgumentList "--start" -ErrorAction SilentlyContinue
      Start-Sleep -Seconds 10

      # Check again
      $dockerInfo = docker info 2>$null
      if ($LASTEXITCODE -eq 0) {
        $dockerRunning = $true
        Write-Host "Docker Desktop started successfully."
      } else {
        Write-Warning "Could not start Docker Desktop automatically."
        Write-Host "Please start Docker Desktop manually and try again."
        Write-Host "You can start it from the Start Menu or by running: C:\Program Files\Docker\Docker\Docker Desktop.exe"
        Write-Host ""
        Write-Host "Once Docker Desktop is running, execute this script again."
        exit 1
      }
    } catch {
      Write-Warning "Could not start Docker Desktop automatically."
      Write-Host "Please start Docker Desktop manually and try again."
      Write-Host "You can start it from the Start Menu or by running: C:\Program Files\Docker\Docker\Docker Desktop.exe"
      Write-Host ""
      Write-Host "Once Docker Desktop is running, execute this script again."
      exit 1
    }
  }

  # Start the database containers
  Write-Host "Starting database containers..."
  docker-compose up -d postgres

  # Wait for database to be ready
  Write-Host "Waiting for database to be ready..."
  $timeout = 30
  $ready = $false

  for ($i = 0; $i -lt $timeout; $i++) {
    try {
      $result = docker exec nutritionguard-postgres pg_isready -U nutritionguard -d nutritionguard 2>$null
      if ($LASTEXITCODE -eq 0) {
        $ready = $true
        break
      }
    } catch {
      # Database not ready yet
    }
    Start-Sleep -Seconds 1
    Write-Host "." -NoNewline
  }

  if (-not $ready) {
    Write-Warning "Database is not ready after $timeout seconds."
    Write-Host "You can check the database status with: docker-compose logs postgres"
    Write-Host "Or try running this script again in a few moments."
    exit 1
  }

  Write-Host ""
  Write-Host "Database is ready!"
} else {
  Write-Host "Using local Postgres (no Docker)."
  Write-Host "If you haven't initialized the schema yet, run: .\\init-local-db.ps1"
}

$envFile = if ($Db -eq "local") { $envLocal } else { $envDocker }

if (!(Test-Path $envFile)) {
  Write-Error "Missing $envFile. Create it from .env.example first."
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
  $pid = $listener.OwningProcess
  $proc = $null
  try { $proc = Get-Process -Id $pid -ErrorAction Stop } catch {}
  $name = if ($proc) { $proc.ProcessName } else { "unknown" }
  Write-Error "Port $port is already in use by PID $pid ($name). Stop that backend first (or change APP_PORT in .env)."
  exit 1
}

Write-Host ""
Write-Host "Starting backend application..."
Set-Location $PSScriptRoot
mvn -DskipTests compile exec:java
