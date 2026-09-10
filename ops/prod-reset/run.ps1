# Reset the prod database to a single ADMIN account (Kaushik) and nothing else.
#
#   .\run.ps1            # read-only: prints what is in prod now
#   .\run.ps1 apply      # performs the reset
#
# Connection string comes from $env:MONGODB_URI, or (if unset) is read from
# ..\..\config\dev.properties  (same Atlas cluster; only the DB name differs).
# Database name: $env:MONGO_DB, or "prod" by default.

param([string]$Mode = "inspect")

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$m2 = Join-Path $env:USERPROFILE ".m2\repository"
$jars = @(
  "$m2\org\mongodb\mongodb-driver-sync\5.0.1\mongodb-driver-sync-5.0.1.jar"
  "$m2\org\mongodb\mongodb-driver-core\5.0.1\mongodb-driver-core-5.0.1.jar"
  "$m2\org\mongodb\bson\5.0.1\bson-5.0.1.jar"
  "$m2\org\slf4j\slf4j-api\2.0.13\slf4j-api-2.0.13.jar"
)
foreach ($j in $jars) {
  if (-not (Test-Path $j)) { throw "Missing jar: $j  (run '..\..\mvnw -q dependency:resolve' first)" }
}
$cp = ($jars + $PSScriptRoot) -join ";"

if (-not (Test-Path ProdReset.class) -or
    (Get-Item ProdReset.java).LastWriteTime -gt (Get-Item ProdReset.class).LastWriteTime) {
  Write-Host "Compiling ProdReset.java ..." -ForegroundColor Cyan
  & javac -cp $cp ProdReset.java
}

Write-Host "Running (mode: $Mode) ..." -ForegroundColor Cyan
& java -cp $cp ProdReset $Mode
