# GlyphForge — подписанные обновления

Пока используется debug APK из Actions, подпись может меняться на каждом runner. Однократно экспортируйте библиотеку, удалите debug APK (только после сохранения резервной копии) и установите подписанную версию из GitHub Releases. Далее не удаляйте приложение: используйте прежний ключ и увеличивайте versionCode.

На доверенном Windows-компьютере установите GitHub CLI и JDK 17 (keytool на PATH), выполните gh auth login, затем запустите Setup-Release-Signing.ps1 в PowerShell. Скрипт генерирует закрытый PKCS12 в профиле пользователя, передаёт ключ только в GitHub Actions Secrets и запускает релиз. Пароль задаёт владелец. Не отправляйте ключ, пароль или base64 файла в чат и не публикуйте их. Храните приватную резервную копию ключа и пароль.

Workflow Publish signed GlyphForge release запускается вручную, при изменении app/build.gradle на main или теге vX.Y.Z. При отсутствии секретов автоматическая публикация пропускается, ручной запуск показывает ошибку. Если релиз уже существует, повторная публикация пропускается. GitHub Secrets: GF_KEYSTORE_B64, GF_KEYSTORE_PASSWORD, GF_KEY_ALIAS, GF_KEY_PASSWORD. Не заменяйте их другим ключом.

Установите APK из Releases, не debug APK из Actions. При запуске приложение проверяет релиз. Нажмите «Скачать и установить»; если Android требует разрешения установки из этого источника, разрешите его и повторите нажатие. APK скачивается через HTTPS, проверяется имя пакета, более высокий versionCode и совпадение подписей. Затем системный установщик запрашивает подтверждение. При обновлении той же подписанной версии библиотека сохраняется.


Security update: GlyphForge opens GitHub Releases in an external browser. It does not install APKs or request installation permission. Never bypass Play Protect. The same permanent signing key remains necessary for normal updates.


Release gate: requires successful browser and Android emulator workflows for the exact same commit; when a previous release exists the workflow downloads its APK and compares SHA-256 signing certificate fingerprints and versionCode. A mismatch blocks release. Legacy .gforge library import preserves remapped rules in a disabled state.
