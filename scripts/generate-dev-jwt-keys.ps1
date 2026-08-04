$ErrorActionPreference = "Stop"

$secretsDir = Join-Path $PSScriptRoot "..\secrets"
New-Item -ItemType Directory -Force -Path $secretsDir | Out-Null

$privateKey = Join-Path $secretsDir "jwt-private.pem"
$publicKey = Join-Path $secretsDir "jwt-public.pem"

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out $privateKey
openssl rsa -in $privateKey -pubout -out $publicKey

Write-Host "Generated development JWT keys:"
Write-Host "  $privateKey"
Write-Host "  $publicKey"
Write-Host "These keys are for local development only and must not be used in production."
