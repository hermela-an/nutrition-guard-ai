param(
  [string]$HostName = "localhost",
  [int]$Port = 5432,
  [string]$User = "postgres",
  [string]$Password = "",
  [int]$Top = 30
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Invoke-Psql([string]$db, [string]$sql) {
  $tmp = New-TemporaryFile
  try {
    Set-Content -LiteralPath $tmp -Value $sql -Encoding UTF8
    $cmd = "psql -h `"$HostName`" -p $Port -U `"$User`" -d `"$db`" -At -F `"|`" -f `"$tmp`" 2>&1"
    $out = cmd /c $cmd
    return [pscustomobject]@{ exit = $LASTEXITCODE; out = ($out | Out-String).Trim() }
  } finally {
    Remove-Item -LiteralPath $tmp -Force -ErrorAction SilentlyContinue
  }
}

$oldPw = $env:PGPASSWORD
try {
  if ($Password) { $env:PGPASSWORD = $Password } else { Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue }

  $list = Invoke-Psql "postgres" "SELECT datname FROM pg_database WHERE datistemplate = false ORDER BY datname;"
  if ($list.exit -ne 0) {
    Write-Host "Cannot connect/list databases on ${HostName}:$Port as $User."
    Write-Host $list.out
    exit 1
  }

  $dbs = $list.out -split "`r?`n" | Where-Object { $_.Trim() } | Select-Object -First $Top
  Write-Host ""
  Write-Host "Scanning databases on ${HostName}:$Port ..."
  Write-Host ""

  $results = @()
  foreach ($db in $dbs) {
    $dbName = $db.Trim()
    if (!$dbName) { continue }

    $has = Invoke-Psql $dbName @"
SELECT
  (SELECT COUNT(*)::int FROM information_schema.tables WHERE table_schema='public' AND table_name='users') AS has_public_users,
  (SELECT COUNT(*)::int FROM information_schema.tables WHERE table_schema='public' AND table_name='food_items') AS has_public_foods,
  (SELECT COUNT(*)::int FROM information_schema.tables WHERE table_schema='ng' AND table_name='users') AS has_ng_users,
  (SELECT COUNT(*)::int FROM information_schema.tables WHERE table_schema='ng' AND table_name='food_items') AS has_ng_foods;
"@
    if ($has.exit -ne 0) {
      $results += [pscustomobject]@{ db = $dbName; error = $has.out }
      continue
    }

    $parts = $has.out.Split("|")
    $hasPubUsers = [int]$parts[0]
    $hasPubFoods = [int]$parts[1]
    $hasNgUsers = [int]$parts[2]
    $hasNgFoods = [int]$parts[3]

    $pubUsers = $null
    $pubFoods = $null
    $ngUsers = $null
    $ngFoods = $null

    if ($hasPubUsers -gt 0) {
      $q = Invoke-Psql $dbName "SELECT COUNT(*)::int FROM public.users;"
      if ($q.exit -eq 0) { $pubUsers = [int]$q.out }
    }
    if ($hasPubFoods -gt 0) {
      $q = Invoke-Psql $dbName "SELECT COUNT(*)::int FROM public.food_items;"
      if ($q.exit -eq 0) { $pubFoods = [int]$q.out }
    }
    if ($hasNgUsers -gt 0) {
      $q = Invoke-Psql $dbName "SELECT COUNT(*)::int FROM ng.users;"
      if ($q.exit -eq 0) { $ngUsers = [int]$q.out }
    }
    if ($hasNgFoods -gt 0) {
      $q = Invoke-Psql $dbName "SELECT COUNT(*)::int FROM ng.food_items;"
      if ($q.exit -eq 0) { $ngFoods = [int]$q.out }
    }

    $results += [pscustomobject]@{
      db = $dbName
      public_users = $pubUsers
      public_foods = $pubFoods
      ng_users = $ngUsers
      ng_foods = $ngFoods
      error = $null
    }
  }

  $results |
    Sort-Object -Property @{ Expression = { $_.public_users }; Descending = $true }, @{ Expression = { $_.ng_users }; Descending = $true } |
    Format-Table -AutoSize

  Write-Host ""
  Write-Host "Tip: the DB with the biggest `public_users`/`ng_users` is usually the one you used before."
  Write-Host "To inspect one DB: psql -h $HostName -p $Port -U $User -d <db_name>"
} finally {
  if ($null -ne $oldPw) { $env:PGPASSWORD = $oldPw } else { Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue }
}
