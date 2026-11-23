# GlyphForge Android debug APK builder for Windows 10/11.
# Downloads only official Gradle and official Nothing SDK when missing.
# Requires Android SDK Platform 36 and Build Tools installed by Android Studio.
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Root
if(-not $env:JAVA_HOME) {
  $jbr = Join-Path ${env:ProgramFiles} 'Android\Android Studio\jbr'
  if(Test-Path $jbr) { $env:JAVA_HOME = $jbr; $env:Path = "$jbr\bin;" + $env:Path }
}
if(-not $env:ANDROID_HOME) {
  $candidate = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
  if(Test-Path $candidate) { $env:ANDROID_HOME = $candidate }
}
if(-not $env:ANDROID_HOME -or -not (Test-Path (Join-Path $env:ANDROID_HOME 'platforms\android-36\android.jar'))) {
  throw 'Android SDK API 36 не найден. Открой Android Studio -> Settings -> SDK Manager -> установи Android API 36 и Build Tools.'
}
$aar = Join-Path $Root 'app\libs\glyph-matrix-sdk-2.0.aar'
if(-not (Test-Path $aar)) {
  New-Item -ItemType Directory -Force -Path (Split-Path -Parent $aar) | Out-Null
  Write-Host 'Downloading official Nothing Glyph Matrix SDK 2.0...'
  Invoke-WebRequest -Uri 'https://raw.githubusercontent.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit/main/glyph-matrix-sdk-2.0.aar' -OutFile $aar
}
$folder=Join-Path $Root '.buildtools'
$gradleDir=Join-Path $folder 'gradle-8.13'
$gradleBat=Join-Path $gradleDir 'bin\gradle.bat'
if(-not(Test-Path $gradleBat)){
  New-Item -ItemType Directory -Force -Path $folder|Out-Null
  $zip=Join-Path $folder 'gradle-8.13-bin.zip'
  Write-Host 'Downloading official Gradle 8.13...'
  Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-8.13-bin.zip' -OutFile $zip
  Expand-Archive -Path $zip -DestinationPath $folder -Force
}
Write-Host 'Building app-debug.apk...'
& $gradleBat --no-daemon assembleDebug
if($LASTEXITCODE -ne 0){throw 'Сборка завершилась с ошибкой. Посмотри журнал Gradle.'}
$apk=Join-Path $Root 'app\build\outputs\apk\debug\app-debug.apk'
Write-Host "ГОТОВО: $apk" -ForegroundColor Green
