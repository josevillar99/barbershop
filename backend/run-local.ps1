# Arranca el backend en local (sin Docker) contra el Oracle que corre en el
# contenedor oracle-db (debe estar levantado: docker compose up -d oracle-db).

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$envFile = Join-Path $PSScriptRoot "..\.env"
if (-not (Test-Path $envFile)) {
    Write-Error "No se encuentra $envFile. Copia .env.example a .env y rellena tus credenciales."
    exit 1
}

Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { return }
    $idx = $line.IndexOf("=")
    if ($idx -lt 0) { return }
    $key = $line.Substring(0, $idx).Trim()
    $value = $line.Substring($idx + 1).Trim()
    Set-Item -Path "Env:$key" -Value $value
}

$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

$env:DB_URL = "jdbc:oracle:thin:@localhost:1521/XEPDB1"
$env:DB_USER = $env:DB_APP_USER
$env:DB_PASSWORD = $env:DB_APP_PASSWORD

& java -version
mvn spring-boot:run
