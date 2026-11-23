# GlyphForge - create a PRIVATE GitHub repository and upload the project
# Requirements: Git + GitHub CLI (`gh`) installed, and `gh auth login` completed.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
foreach ($cmd in @('git','gh')) {
  if (-not (Get-Command $cmd -ErrorAction SilentlyContinue)) {
    throw "Missing command: $cmd. Install Git and GitHub CLI first."
  }
}
& gh auth status
if ($LASTEXITCODE -ne 0) { throw 'Log in first: gh auth login' }
$owner = (& gh api user --jq '.login' | Select-Object -First 1).Trim()
if ($LASTEXITCODE -ne 0 -or -not $owner) { throw 'Could not determine GitHub account' }
$target = "$owner/GlyphForge"
& gh repo view $target --json name 1>$null 2>$null
if ($LASTEXITCODE -eq 0) {
  throw "Repository $target already exists. Refusing to overwrite or mix unrelated history."
}
if (-not (Test-Path .git)) {
  & git init -b main
  if ($LASTEXITCODE -ne 0) { throw 'git init failed' }
}
$name = (& git config user.name)
$email = (& git config user.email)
if (-not $name -or -not $email) {
  $account = & gh api user --jq '.id, .login'
  if ($LASTEXITCODE -ne 0 -or $account.Count -lt 2) { throw 'Could not set git author; run git config first' }
  if (-not $name) { & git config user.name $account[1].Trim() }
  if (-not $email) { & git config user.email "$($account[0].Trim())+$($account[1].Trim())@users.noreply.github.com" }
}
& git add -A
if ($LASTEXITCODE -ne 0) { throw 'git add failed' }
& git commit -m 'Initial commit: GlyphForge Android 0.3 Music Reactive'
if ($LASTEXITCODE -ne 0) { throw 'git commit failed' }
& gh repo create $target --private --source . --remote origin --push
if ($LASTEXITCODE -ne 0) { throw 'Repository creation or push failed. Inspect GitHub before retrying.' }
Write-Host "Published: https://github.com/$target"
Write-Host 'To build APK: GitHub > Actions > Build GlyphForge Android APK'
