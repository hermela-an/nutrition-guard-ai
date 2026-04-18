param(
  [string]$BackendDir = $PSScriptRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Read-DotEnv([string]$Path) {
  $map = @{}
  if (!(Test-Path -LiteralPath $Path)) { return $map }
  Get-Content -LiteralPath $Path | ForEach-Object {
    $line = $_
    if ($null -eq $line) { return }
    $trimmed = $line.Trim()
    if ($trimmed.Length -eq 0) { return }
    if ($trimmed.StartsWith("#")) { return }
    $idx = $trimmed.IndexOf("=")
    if ($idx -le 0) { return }
    $k = $trimmed.Substring(0, $idx).Trim()
    $v = $trimmed.Substring($idx + 1).Trim()
    if ($v.Length -ge 2 -and $v.StartsWith('"') -and $v.EndsWith('"')) {
      $v = $v.Substring(1, $v.Length - 2)
    }
    if ($k) { $map[$k] = $v }
  }
  return $map
}

function Try-QueryFoodSummary([hashtable]$envMap, [string]$label) {
  $hostName = $envMap["DB_HOST"]
  $port = $envMap["DB_PORT"]
  $db = $envMap["DB_NAME"]
  $user = $envMap["DB_USER"]
  $pw = $envMap["DB_PASSWORD"]

  if (!$hostName -or !$port -or !$db -or !$user) {
    return [pscustomobject]@{ label = $label; ok = $false; error = "Missing DB_* settings in env file." }
  }

  $oldPw = $env:PGPASSWORD
  $tmpFile = $null
  try {
    if ($pw) { $env:PGPASSWORD = $pw } else { Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue }

    $sql = @"
SELECT
  current_database() AS db,
  inet_server_addr()::text AS server_ip,
  inet_server_port() AS server_port,
  (SELECT COUNT(*)::int FROM public.food_items) AS foods,
  (SELECT COALESCE(MAX(id), 0)::int FROM public.food_items) AS max_id;
"@

    # Using cmd.exe avoids PowerShell treating native stderr as a terminating error.
    $tmpFile = New-TemporaryFile
    Set-Content -LiteralPath $tmpFile -Value $sql -Encoding UTF8
    $cmd = "psql -h `"$hostName`" -p $port -U `"$user`" -d `"$db`" -At -F `"|`" -f `"$tmpFile`" 2>&1"
    $out = cmd /c $cmd
    if ($LASTEXITCODE -ne 0) {
      return [pscustomobject]@{ label = $label; ok = $false; error = ($out | Out-String).Trim() }
    }

    $line = ($out | Out-String).Trim()
    $parts = $line.Split("|")
    if ($parts.Length -lt 5) {
      return [pscustomobject]@{ label = $label; ok = $false; error = "Unexpected output: $line" }
    }

    $foodsWithImages = 0
    $hasImageSql = @"
SELECT COUNT(*)::int
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'food_items'
  AND column_name = 'image_url';
"@
    $tmpFile2 = New-TemporaryFile
    try {
      Set-Content -LiteralPath $tmpFile2 -Value $hasImageSql -Encoding UTF8
      $cmd2 = "psql -h `"$hostName`" -p $port -U `"$user`" -d `"$db`" -At -f `"$tmpFile2`" 2>&1"
      $out2 = cmd /c $cmd2
      if ($LASTEXITCODE -eq 0 -and ($out2 | Out-String).Trim() -eq "1") {
        $imgSql = "SELECT COUNT(*)::int FROM public.food_items WHERE image_url IS NOT NULL AND BTRIM(image_url) <> '';"
        $tmpFile3 = New-TemporaryFile
        try {
          Set-Content -LiteralPath $tmpFile3 -Value $imgSql -Encoding UTF8
          $cmd3 = "psql -h `"$hostName`" -p $port -U `"$user`" -d `"$db`" -At -f `"$tmpFile3`" 2>&1"
          $out3 = cmd /c $cmd3
          if ($LASTEXITCODE -eq 0) {
            $foodsWithImages = [int](($out3 | Out-String).Trim())
          }
        } finally {
          if ($tmpFile3) { Remove-Item -LiteralPath $tmpFile3 -Force -ErrorAction SilentlyContinue }
        }
      }
    } finally {
      if ($tmpFile2) { Remove-Item -LiteralPath $tmpFile2 -Force -ErrorAction SilentlyContinue }
    }

    return [pscustomobject]@{
      label = $label
      ok = $true
      db = $parts[0]
      server_ip = $parts[1]
      server_port = [int]$parts[2]
      foods = [int]$parts[3]
      foods_with_images = $foodsWithImages
      max_id = [int]$parts[4]
    }
  } finally {
    if ($tmpFile) { Remove-Item -LiteralPath $tmpFile -Force -ErrorAction SilentlyContinue }
    if ($null -ne $oldPw) { $env:PGPASSWORD = $oldPw } else { Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue }
  }
}

$backendPath = (Resolve-Path -LiteralPath $BackendDir).Path
$envLocalPath = Join-Path $backendPath ".env.local"
$envDockerPath = Join-Path $backendPath ".env"

$envLocal = Read-DotEnv $envLocalPath
$envDocker = Read-DotEnv $envDockerPath

Write-Host ""
Write-Host "Checking which Postgres has your latest foods (images/admin edits are stored in the DB you were connected to)."
Write-Host ""

$localResult = Try-QueryFoodSummary $envLocal "local (.env.local)"
$dockerResult = Try-QueryFoodSummary $envDocker "docker (.env)"

@($localResult, $dockerResult) | ForEach-Object {
  if ($_.ok) {
    Write-Host ("{0}: db={1} server={2}:{3} foods={4} foods_with_images={5} max_id={6}" -f $_.label, $_.db, $_.server_ip, $_.server_port, $_.foods, $_.foods_with_images, $_.max_id)
  } else {
    Write-Host ("{0}: not reachable ({1})" -f $_.label, $_.error)
  }
}

Write-Host ""

$candidates = @(@($localResult, $dockerResult) | Where-Object { $_.ok })
if (@($candidates).Count -eq 0) {
  Write-Host "No database was reachable. Start Postgres (local service or Docker) and rerun this script."
  exit 1
}

$best = @($candidates) | Sort-Object -Property foods_with_images, foods, max_id -Descending | Select-Object -First 1
Write-Host ("Recommended DB to point the backend at: {0}" -f $best.label)
Write-Host ""
Write-Host "To use local:  .\\start-backend.ps1 -Db local"
Write-Host "To use docker: .\\start-backend.ps1 -Db docker"
