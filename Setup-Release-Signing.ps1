# Run locally on a trusted Windows PC. Requires GitHub CLI and JDK keytool.
$ErrorActionPreference = 'Stop'
$repo = 'CRILIK/GlyphForge-0.3V'
$alias = 'glyphforge'
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { throw 'Install GitHub CLI: https://cli.github.com/' }
if (-not (Get-Command keytool -ErrorAction SilentlyContinue)) { throw 'Install JDK 17 and put keytool on PATH: https://adoptium.net/' }
gh auth status
if ($LASTEXITCODE -ne 0) { throw 'Run gh auth login first.' }
$existing = gh secret list --repo $repo --json name
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect repository secrets.' }
$names = @($existing | ConvertFrom-Json | ForEach-Object { $_.name })
$required = @('GF_KEYSTORE_B64','GF_KEYSTORE_PASSWORD','GF_KEY_ALIAS','GF_KEY_PASSWORD')
if (@($names | Where-Object { $_ -in $required }).Count -gt 0) {
    throw 'Signing secrets already exist (possibly partial). Do not rotate an existing key. Review GitHub Settings first.'
}
$dir = Join-Path $env:USERPROFILE 'GlyphForge-private-signing'
New-Item -ItemType Directory -Force -Path $dir | Out-Null
$key = Join-Path $dir 'glyphforge-release.p12'
$secure = Read-Host 'Create or enter the keystore password (at least 12 characters; keep it safe)' -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try { $env:GF_SIGN_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
try {
    if ($env:GF_SIGN_PASSWORD.Length -lt 12) { throw 'Password must have at least 12 characters.' }
    if (-not (Test-Path $key)) {
        keytool -genkeypair -storetype PKCS12 -keystore $key -alias $alias -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=GlyphForge' -storepass:env GF_SIGN_PASSWORD -keypass:env GF_SIGN_PASSWORD
        if ($LASTEXITCODE -ne 0) { throw 'keytool failed.' }
    } else {
        keytool -list -keystore $key -alias $alias -storepass:env GF_SIGN_PASSWORD
        if ($LASTEXITCODE -ne 0) { throw 'Existing keystore cannot be opened; do not replace it.' }
    }
    $values = @{
        GF_KEYSTORE_B64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($key))
        GF_KEYSTORE_PASSWORD = $env:GF_SIGN_PASSWORD
        GF_KEY_ALIAS = $alias
        GF_KEY_PASSWORD = $env:GF_SIGN_PASSWORD
    }
    foreach ($name in $required) {
        $values[$name] | gh secret set $name --repo $repo
        if ($LASTEXITCODE -ne 0) { throw "Could not configure $name. Keep the same keystore; finish missing secrets manually." }
    }
    Write-Host "Signing configured. Keep a private backup of $key and password. Never upload the key to the repository."
    gh workflow run release-apk.yml --repo $repo --ref main
    if ($LASTEXITCODE -ne 0) { Write-Host 'Start Publish signed GlyphForge release manually in Actions.' }
} finally {
    Remove-Item Env:GF_SIGN_PASSWORD -ErrorAction SilentlyContinue
    $values = $null
    $secure.Dispose()
}
