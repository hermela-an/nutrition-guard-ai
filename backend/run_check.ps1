$envFile = ".env.local"
Get-Content $envFile | ForEach-Object {
    if ($_ -match '^\s*([^#]+)=(.+)$') {
        $name = $Matches[1].Trim()
        $value = $Matches[2].Trim().Trim('"')
        [System.Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
}
$env:PGPASSWORD = $env:DB_PASSWORD
psql -h $env:DB_HOST -p $env:DB_PORT -U $env:DB_USER -d $env:DB_NAME -f check_triggers.sql
