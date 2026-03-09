$envFile = Join-Path $PSScriptRoot ".env"

if (!(Test-Path $envFile)) {
  Write-Error "Missing $envFile. Create it from .env.example first."
  exit 1
}

Get-Content $envFile | ForEach-Object {
  if ($_ -match '^\s*#' -or $_ -match '^\s*$') { return }
  $name, $value = $_ -split '=', 2
  if ($null -ne $name -and $null -ne $value) {
    [System.Environment]::SetEnvironmentVariable($name.Trim(), $value.Trim().Trim('"'), "Process")
  }
}

mvn exec:java
