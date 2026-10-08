# Pixel1 Keeper

Android-приложение для старого Google Pixel 1 (Android 10): каждую ночь забирает новые фото и видео из безлимита Яндекс Диска и кладёт их в `DCIM/Yandex/`, чтобы Google Photos на Pixel 1 загрузил их в исходном качестве. После загрузки освобождает память и присылает отчёт в Telegram.

Яндекс только читается: приложение умеет вызывать шесть методов веб-клиента Диска (список фото, информация о файле, ссылка на скачивание) и ничего не меняет в Яндексе.

Полное описание — [SPEC.md](SPEC.md).

## Сборка локально

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :app:testDebugUnitTest :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Сборка и раздача тестерам (GitHub Actions)

Каждый push в `main` собирает подписанный release APK и раздаёт его через Firebase App Distribution. PR только собираются и тестируются.

Один раз настроить секреты (на маке, нужны `gh` под `kissedcode` и Homebrew):

```bash
bash scripts/setup-secrets.sh
```

Скрипт создаёт release-keystore, сервисный аккаунт Firebase и загружает всё в GitHub Secrets. Ручной запуск сборки:

```bash
gh workflow run build.yml -R kissedcode/pixel1-keeper
```

Версия: `versionCode` — номер запуска workflow, `versionName` — `appVersionName` из `gradle.properties` + номер.

## Лицензия

Личный проект. Код открыт для чтения; используйте на свой риск — приложение работает с внутренним API веб-версии Яндекс Диска, который может измениться без предупреждения.
