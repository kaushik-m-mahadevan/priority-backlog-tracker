<#
  Builds (if needed) and runs the app with the `demo` profile so it starts pre-loaded
  with a realistic sample backlog. Uses the embedded MongoDB unless MONGODB_URI is set.

  Usage:
    ./scripts/run-demo.ps1              # embedded Mongo, demo data
    $env:MONGODB_URI = "mongodb+srv://..."; ./scripts/run-demo.ps1   # your Mongo
#>
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$jar = Get-ChildItem "target/*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $jar) {
  Write-Host "No jar found - building (this downloads Node + the Mongo binary on first run)..."
  & "./mvnw" -q -DskipTests package
  $jar = Get-ChildItem "target/*.jar" | Select-Object -First 1
}

Write-Host "Starting on http://localhost:8080  (profile: demo)   sign in as test123 / test123"
& java -jar $jar.FullName --spring.profiles.active=demo
