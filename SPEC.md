# Pixel1 Keeper — спецификация

Версия документа: 2026-10-08
Текущая инсталляция: package `com.kissedcode.pixel1keeper`, Firebase project `pixel1keeper-kissed`, публичный репозиторий [kissedcode/pixel1-keeper](https://github.com/kissedcode/pixel1-keeper), сборка и раздача — GitHub Actions, minSdk 29, targetSdk 36, compileSdk 36, единственное устройство — Google Pixel 1 (Android 10).
Назначение: приложение на старом Pixel 1, которое каждую ночь забирает новые фото и видео (снятые начиная с 08.10.2026) из безлимита Яндекс Диска и кладёт их в память телефона, чтобы Google Photos загрузил их в оригинальном качестве в рамках пожизненного безлимита Pixel 1. После загрузки чистит память и присылает отчёты в Telegram.

## Оглавление

- [Часть 1 — Пользовательская](#часть-1--пользовательская)
  - [Что это за приложение](#что-это-за-приложение)
  - [На каких устройствах работает](#на-каких-устройствах-работает)
  - [Что видит пользователь при первом запуске](#что-видит-пользователь-при-первом-запуске)
  - [Пользовательские сценарии](#пользовательские-сценарии)
  - [Экраны](#экраны)
  - [Требования к настройке](#требования-к-настройке)
  - [Границы функциональности](#границы-функциональности)
- [Часть 2 — Техническая спецификация для реализации](#техническая-спецификация-для-реализации)

## Часть 1 — Пользовательская

### Что это за приложение

Pixel1 Keeper превращает Pixel 1 в «мост» между Яндекс Диском и Google Photos. Телефоны семьи продолжают грузить фото в безлимит Яндекса как обычно; Pixel 1 лежит дома на Wi-Fi и зарядке, ночью забирает оттуда новое, а Google Photos на Pixel 1 загружает это в Google в оригинальном качестве без расхода квоты.

Главные возможности:

- **Ночной забор новых фото и видео** из безлимита Яндекс Диска — только то, что появилось с прошлого раза.
- **Точка отсчёта — 08.10.2026**: переносятся только фото и видео, снятые 8 октября 2026 года и позже. Архив до этой даты не трогается (возможное расширение — отдельной версией).
- **Оригиналы байт-в-байт**: Motion Photo, Ultra HDR, видео — без пересжатия.
- **Автоочистка памяти Pixel 1** после того, как Google Photos загрузил файлы.
- **Отчёты и тревоги в Telegram**: утренняя сводка, слетевший вход в Яндекс, нехватка места, перегрев.
- **Только чтение в Яндексе**: приложение никогда ничего не удаляет, не перемещает и не меняет на Диске.

### На каких устройствах работает

- Google Pixel 1 / Pixel 1 XL на штатной прошивке Android 10. Кастомные прошивки не поддерживаются (могут сломать безлимит Google Photos).
- Формально установится на любой Android 10+, но смысл есть только на устройстве с безлимитом Google Photos.

### Что видит пользователь при первом запуске

Экран «Подготовка» со списком шагов; каждый шаг — строка со статусом и кнопкой.

```
Заголовок: "Подготовка Pixel1 Keeper"
Подзаголовок: "Выполните шаги по порядку. Готовые шаги отмечаются галочкой."

1. "Войти в Яндекс"                         [Войти]
2. "Разрешить статистику использования"    [Открыть настройки]
3. "Отключить оптимизацию батареи"         [Открыть настройки]
4. "Подключить Telegram"                    [Настроить]
5. "Включить папку Yandex в Google Photos"  [Как это сделать]

Кнопка внизу: "Начать работу"
```

Кнопка «Начать работу» активна, когда выполнены шаги 1, 3 и 5 (шаги 2 и 4 желательны, но не обязательны). Шаг 5 приложение проверить не может — пользователь отмечает его вручную галочкой «Сделано».

### Пользовательские сценарии

#### Первый вход в Яндекс

1. На экране «Подготовка» нажать «Войти».
2. Откроется встроенный браузер со страницей входа Яндекса. Войти обычным способом (пароль, код, подтверждение в приложении Яндекс).
3. После успешного входа экран закрывается сам, шаг отмечается галочкой.

```
Заголовок экрана: "Вход в Яндекс"
Подсказка сверху: "Войдите в аккаунт, где лежат ваши фото. Приложение только читает список фото и скачивает их."
Snackbar после входа: "Вход выполнен"
Snackbar при ошибке: "Не удалось войти. Попробуйте ещё раз"
```

#### Обычная ночь

Пользователь ничего не делает. В ночное окно (по умолчанию 00:00–07:00), когда телефон на зарядке и в Wi-Fi, приложение:

1. проверяет вход в Яндекс;
2. забирает список новых фото с прошлого раза;
3. скачивает новые фото — сколько помещается в свободное место с запасом;
4. ждёт, пока Google Photos загрузит, и удаляет загруженное из памяти;
5. утром присылает сводку в Telegram.

Во время работы в шторке висит постоянное уведомление:

```
Заголовок: "Pixel1 Keeper работает"
Текст: "Скачано 34 из 120 · свободно 6,2 ГБ"
```

#### Утренний отчёт в Telegram

Приходит в 07:30 (настраивается):

```
Pixel1 Keeper — отчёт за ночь 08.10
Новые: скачано 47 (1,3 ГБ), удалено после загрузки 52
Ждут загрузки в Google: 18 (0,6 ГБ)
Свободно: 7,4 ГБ · батарея 31°C
```

Если ночь прошла без новых фото и всё уже загружено, отчёт короткий:

```
Pixel1 Keeper — отчёт за ночь 08.10
Новых фото нет. Всё загружено. Свободно: 9,1 ГБ
```

#### Тревоги в Telegram

Приходят сразу, не чаще одного раза в 6 часов на один тип:

```
Pixel1 Keeper: вход в Яндекс слетел. Откройте приложение на Pixel 1 и войдите заново. Фото в Яндексе в безопасности, просто приедут позже.
```

```
Pixel1 Keeper: Яндекс ограничил запросы. Пауза до 09.10 03:00, потом продолжу сам.
```

```
Pixel1 Keeper: Google Photos не загружает уже 24 часа (ждут 230 файлов, 7,9 ГБ). Проверьте Google Photos на Pixel 1.
```

```
Pixel1 Keeper: мало места — свободно 0,8 ГБ. Скачивание остановлено до очистки.
```

```
Pixel1 Keeper: батарея нагрелась до 44°C. Скачивание на паузе до остывания.
```

```
Pixel1 Keeper: ошибка — <краткое описание>. Подробности в приложении.
```

#### Повторный вход, если слетела сессия

1. Прийти тревога «вход в Яндекс слетел».
2. Открыть Pixel1 Keeper — на главном экране красная плашка `"Нужно войти в Яндекс заново"` и кнопка `"Войти"`.
3. Войти, как при первом входе. Работа продолжится в следующую ночь или сразу по кнопке «Запустить сейчас».

#### Точка отсчёта

Переносятся только фото и видео с датой съёмки **08.10.2026 00:00 (время Люксембурга) и позже**. Дата зашита в приложение и показана в настройках только для чтения: `"Переносятся фото начиная с 08.10.2026"`. Старое фото, загруженное в Яндекс позже (например, с другого устройства), но снятое до этой даты, не переносится.

#### Запуск вручную

На главном экране кнопка `"Запустить сейчас"` — запускает цикл немедленно, игнорируя ночное окно (но требуя Wi-Fi). Кнопка `"Пауза"` останавливает всю работу до нажатия `"Продолжить"`.

### Экраны

- **Подготовка** — чек-лист первого запуска (см. выше). Доступен потом из меню: `"Проверить настройку"`.
- **Главный** — заголовок `"Pixel1 Keeper"`, карточки:
  - `"Статус"` — `"Ждёт ночного окна"` / `"Работает"` / `"Пауза"` / `"Нужно войти в Яндекс заново"` / `"Яндекс ограничил запросы до …"`
  - `"Последний запуск"` — время, сколько скачано и удалено
  - `"Очередь"` — ждут скачивания: N, ждут загрузки в Google: N, перенесено всего: N
  - `"Память"` — свободно X ГБ, резерв Y ГБ
  - кнопки `"Запустить сейчас"`, `"Пауза"` / `"Продолжить"`, `"Настройки"`, `"Журнал"`
- **Вход в Яндекс** — встроенный браузер.
- **Настройки**:
  - `"Ночное окно"` — с … до … (по умолчанию 00:00–07:00)
  - `"Только на зарядке"` — вкл (по умолчанию)
  - `"Резерв свободного места"` — 2 ГБ
  - `"Переносятся фото начиная с 08.10.2026"` — только для чтения
  - `"Пропускать скриншоты"` — вкл
  - `"Режим очистки"` — `"Осторожный (по факту загрузки)"` (по умолчанию) / `"По таймеру"`
  - `"Удалять через"` — 48 часов (для режима по таймеру и как страховка)
  - `"Telegram"` — токен бота, chat id, кнопка `"Отправить тестовое сообщение"`
  - `"Время утреннего отчёта"` — 07:30
- **Журнал** — последние 500 событий (время, тип, текст), кнопка `"Скопировать журнал"`.

### Требования к настройке

Один раз на Pixel 1:

1. **Google Photos**: вход в аккаунт с безлимитом, «Автозагрузка» включена, качество — «Исходное». После первой ночи работы Keeper: Google Photos → «Библиотека» → «Фото на устройстве» → папка `Yandex` → включить «Автозагрузка».
2. **Статистика использования** (для осторожной очистки): Настройки → Приложения → Специальный доступ → «Доступ к истории использования» → Pixel1 Keeper → разрешить. Приложение открывает этот экран само.
3. **Оптимизация батареи**: отключить для Pixel1 Keeper (приложение покажет системный диалог).
4. **Telegram**: создать бота через @BotFather, написать ему любое сообщение, вставить токен в настройки; chat id приложение определит само по кнопке `"Найти chat id"`.
5. **Не ставить кастомную прошивку и не обновлять Google Photos вручную из сторонних источников.**

ADB не требуется. Установка — через Firebase App Distribution.

### Границы функциональности

Приложение НЕ:

- удаляет, перемещает или меняет что-либо в Яндекс Диске;
- загружает что-либо в Google само — это делает Google Photos;
- проверяет напрямую, что файл есть в Google Photos (у Google нет такого API для чужих файлов) — только косвенно;
- переносит фото, снятые до 08.10.2026 (архив);
- работает с несколькими аккаунтами Яндекса (аккаунт жены — следующий этап, v2);
- управляет умной розеткой;
- работает на других телефонах как отправитель — это приложение только для Pixel 1.

---

## Техническая спецификация для реализации

### Детали: Цель и границы системы

- Источник: внутренний API веб-версии Яндекс Диска (`POST https://disk.yandex.ru/models-v2?m=<method>`), авторизация cookies сессии браузера + CSRF-токен `sk` из HTML страницы. Официальный REST API не используется для безлимита (не видит `photounlim`).
- Приёмник: MediaStore, папка `DCIM/Yandex/` во внешнем хранилище Pixel 1. Загрузку в облако делает Google Photos.
- Работоспособность методов подтверждена ручным тестом 2026-10-08 в браузере (только чтение): `intapi/photo-init-snapshot`, `intapi/photo-get-deltas`, `intapi/photo-get-snapshot`, `intapi/photo-get-clusters-with-resources`, `mpfs/info`, `mpfs/url`. Ссылка из `mpfs/url` скачивается без cookies, поддерживает Range, размер совпадает с `meta.size`.
- Жёсткий запрет: никаких вызовов методов, меняющих данные (`mpfs/bulk-*`, `*delete*`, `*move*`, `*copy*`, `*trash*` и т.п.). Реализуется allow-list'ом методов в `YandexClient`.
- Вне scope v1: перенос архива до 08.10.2026, второй аккаунт, внешний watchdog (DO-дроплет), умная розетка, NotificationListener.

### Детали: Архитектура

- Kotlin 2.1.x, AGP 8.11+, Gradle 8.14.3 (wrapper), JVM 17.
- minSdk 29 (Android 10 — Pixel 1 и есть целевое устройство), targetSdk 36, compileSdk 36.
- UI: Jetpack Compose Material 3, single-activity + Navigation Compose.
- Асинхронность: coroutines + Flow.
- Фоновая работа: WorkManager.
  - `NightCycleWorker` — long-running worker с `setForeground()` (foreground service type `dataSync`), constraints: `NetworkType.UNMETERED`, `requiresCharging` (из настроек), `requiresStorageNotLow=false` (место считаем сами).
  - Планировщик: `PeriodicWorkRequest` раз в 15 минут (`SchedulerWorker`), который проверяет окно/паузу/бэкофф и ставит `NightCycleWorker` как unique work (`ExistingWorkPolicy.KEEP`).
  - `CleanupWorker` — периодический, раз в 30 минут круглосуточно (очистка не зависит от окна).
  - `ReportWorker` — раз в сутки в заданное время.
- Сеть: OkHttp 4.12 + kotlinx.serialization JSON. HTTP/2.
- Persistence:
  - Room — очередь файлов и журнал событий.
  - DataStore Preferences — настройки и курсоры (`photosliceId`, `revision`, флаг первичной догрузки).
  - Секреты (Telegram token) — DataStore, значение зашифровано AES/GCM ключом из Android Keystore. Значения по умолчанию можно передать при сборке из GitHub Secrets (`TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID` → `BuildConfig.TG_TOKEN/TG_CHAT_ID`); введённое в UI их перекрывает.
- Cookies Яндекса: `android.webkit.CookieManager` — единое хранилище и для WebView, и для OkHttp (`CookieJar`-адаптер поверх `CookieManager`).
- Firebase: только App Distribution (SDK не подключается, `google-services.json` в репозитории нет).
- DI: ручной `AppContainer` в `Application` (без Hilt — приложение маленькое).

### Детали: Брендинг

- **Источник иконки:** `branding/icon-source.png` (1024×1024 PNG).
- **Стиль:** flat minimal — старый телефон-силуэт с двумя стрелками-мостом между жёлтой (Яндекс) и многоцветной (Google) точками; без текста.
- **Primary color:** `#1F3A5F` (тёмно-синий).
- **Accent color:** `#FFCC00`.
- **Launcher icons:** legacy `mipmap-{mdpi..xxxhdpi}/ic_launcher.png` + `ic_launcher_round.png`; adaptive `mipmap-anydpi-v26/ic_launcher.xml` / `ic_launcher_round.xml`, foreground `mipmap-*/ic_launcher_foreground.png`, background `@color/ic_launcher_background`.
- **Monochrome:** нет (Android 10 не поддерживает themed icons).
- **Сборка иконок:** скрипт из `references/icon-generation.md`.

### Детали: Package name / applicationId

- `com.kissedcode.pixel1keeper`.

### Детали: AndroidManifest

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<!-- Проверка Wi-Fi/unmetered. -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<!-- Long-running WorkManager worker со статусным уведомлением. -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<!-- Для targetSdk 33+; на Android 10 выдаётся автоматически. -->
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<!-- WorkManager восстанавливает расписание после перезагрузки. -->
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
<!-- Системный диалог отключения оптимизации батареи. Приложение не в Play Store — политика допустима. -->
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS"
    tools:ignore="ProtectedPermissions" />
<!-- NetworkStatsManager по UID Google Photos. Выдаётся пользователем в «Доступ к истории использования». -->
<queries>
    <package android:name="com.google.android.apps.photos" />
</queries>
<!-- Получение UID Google Photos. -->
```

Запись в `DCIM/Yandex/` идёт через MediaStore с `RELATIVE_PATH` — на Android 10 storage-permission для собственных файлов не нужны. `requestLegacyExternalStorage` не используется.

Компоненты:

- `MainActivity` — единственная activity, `LAUNCHER`.
- `androidx.work.impl.foreground.SystemForegroundService` — переопределить в манифесте с `android:foregroundServiceType="dataSync"`.
- `BatteryTempReceiver` не регистрируется в манифесте — температура читается sticky-intent'ом `ACTION_BATTERY_CHANGED` по требованию.

`android:allowBackup="false"` (cookies и токены не должны уходить в бэкап).

### Детали: Разрешения и как они получаются

| Permission / доступ | Как выдаётся | Обязательно? |
|---|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE*` | normal, при установке | Да |
| `POST_NOTIFICATIONS` | на Android 10 автоматически | Да |
| Игнор оптимизации батареи | `Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:…")` | Да |
| `PACKAGE_USAGE_STATS` | пользователь в `Settings.ACTION_USAGE_ACCESS_SETTINGS`; проверка через `AppOpsManager.unsafeCheckOpNoThrow(OPSTR_GET_USAGE_STATS, …)` | Нет; без него доступен только режим очистки «По таймеру» |
| Storage | не нужно (MediaStore, собственные файлы) | — |

### Детали: Компоненты приложения

| Компонент | Тип | Ответственность | Зависимости |
|---|---|---|---|
| `KeeperApp` | Application | `AppContainer`, создание каналов уведомлений, регистрация `SchedulerWorker` | WorkManager |
| `MainActivity` | Activity | Compose-навигация: Setup, Home, Login, Settings, Log | ViewModels |
| `LoginScreen` | Compose + `AndroidView(WebView)` | Вход в Яндекс, определение успеха | `CookieManager` |
| `YandexSession` | class | Получение `sk` из HTML, проверка валидности сессии, `connection_id` | OkHttp, `CookieManager` |
| `YandexClient` | class | Вызовы `models-v2` из allow-list, rate limit, разбор ошибок | `YandexSession`, OkHttp |
| `PhotosliceSync` | class | Курсор ревизии, дельты, первичная догрузка с точки отсчёта, наполнение очереди | `YandexClient`, Room, DataStore |
| `Downloader` | class | Скачивание файла в MediaStore с докачкой и проверкой размера | OkHttp, `MediaStoreWriter` |
| `MediaStoreWriter` | class | Pending-вставка в `DCIM/Yandex/`, append, публикация, удаление | `ContentResolver` |
| `SpaceBudget` | class | Расчёт бюджета скачивания | `StatFs`, Room |
| `UploadDetector` | class | Оценка «Google Photos загрузил» по трафику UID | `NetworkStatsManager`, `PackageManager` |
| `Cleaner` | class | Решение об удалении и удаление | `UploadDetector`, `MediaStoreWriter`, Room |
| `DeviceHealth` | class | Температура/заряд батареи, свободное место | `BatteryManager`, `StatFs` |
| `TelegramNotifier` | class | `sendMessage`, дедупликация тревог, `getUpdates` для chat id | OkHttp, DataStore |
| `SchedulerWorker` | CoroutineWorker | Раз в 15 мин решает, запускать ли ночной цикл | WorkManager, DataStore |
| `NightCycleWorker` | CoroutineWorker (foreground) | Полный ночной цикл | все выше |
| `CleanupWorker` | CoroutineWorker | Очистка раз в 30 мин | `Cleaner` |
| `ReportWorker` | CoroutineWorker | Утренний отчёт | Room, `TelegramNotifier` |
| `KeeperDb` | Room DB | Таблицы `items`, `events`, `traffic_marks` | — |
| `SettingsStore` | DataStore | Настройки, курсоры, бэкоффы | — |

### Детали: Модель данных

`items` (Room):

| Поле | Тип | Смысл |
|---|---|---|
| `itemId` | TEXT PK | `itemId` из Photoslice (`2_<ts>_<hash>_<ver>`) — ключ дедупликации |
| `path` | TEXT | `/photounlim/2026-10-07 18-58-54.JPG` или `disk:/...` для файлов из обычных папок |
| `name` | TEXT | имя файла |
| `source` | TEXT | `DELTA` (из дельт) / `BACKFILL` (первичная догрузка с точки отсчёта) |
| `etime` | INTEGER | дата съёмки (сек), из `mpfs/info` или из `itemId` |
| `size` | INTEGER? | байт, из `mpfs/info` (заполняется перед скачиванием) |
| `mime` | TEXT? | |
| `status` | TEXT | `QUEUED` / `DOWNLOADING` / `ON_DEVICE` / `UPLOADED_ASSUMED` / `DELETED` / `SKIPPED` / `FAILED` |
| `mediaUri` | TEXT? | `content://media/...` |
| `downloadedBytes` | INTEGER | для докачки |
| `onDeviceAt` | INTEGER? | момент публикации в MediaStore (мс) |
| `attempts` | INTEGER | |
| `lastError` | TEXT? | |

Индексы: `(status, source, etime DESC)`.

`traffic_marks`: `(ts, photosTxBytes, onDeviceBytesTotal)` — снимки для `UploadDetector`.

`events`: `(ts, level, type, message)`, ротация до 500 записей.

DataStore: `photosliceId`, `revision`, `backfillDone` (bool), `pausedUntil`, `rateLimitBackoffLevel`, `lastAlertAt.<type>`, все настройки из Части 1.

### Детали: Основные пользовательские потоки

#### Вход в Яндекс (`LoginScreen`)

1. WebView с `javaScriptEnabled=true`, `domStorageEnabled=true`, `CookieManager.setAcceptThirdPartyCookies(webView, true)`, user-agent — стандартный WebView.
2. Загрузить `https://disk.yandex.ru/client/photo`. Яндекс сам редиректит на `passport.yandex.ru`.
3. В `onPageFinished`: если URL начинается с `https://disk.yandex.ru/client/` — вызвать `YandexSession.validate()`. Успех → `CookieManager.flush()`, событие `LOGIN_OK`, закрыть экран.

#### Сессия и `sk` (`YandexSession`)

1. `GET https://disk.yandex.ru/client/photo` через OkHttp с cookies из `CookieManager`, заголовки браузерные (`User-Agent` как у WebView, `Accept-Language: ru`).
2. Если ответ — редирект на `passport.yandex.ru` или в HTML нет `"sk":"…"` → `SessionExpired`.
3. `sk` = regex `"sk":"([^"]+)"`. Кэш в памяти на время цикла.
4. `Set-Cookie` из ответа записываются обратно в `CookieManager` (продление сессии).
5. `connection_id` — `System.currentTimeMillis().toString() + 3 случайные цифры`, один на цикл.
6. Если `models-v2` вернул ошибку про `sk` (в теле — новый `sk`) — взять новый и повторить один раз.

#### Вызов `models-v2` (`YandexClient`)

- `POST https://disk.yandex.ru/models-v2?m=<method>`, `Content-Type: application/json`, тело:
  ```json
  {"sk":"…","connection_id":"…","apiMethod":"<method>","requestParams":{…}}
  ```
- Allow-list: `intapi/photo-init-snapshot`, `intapi/photo-get-deltas`, `intapi/photo-get-snapshot`, `intapi/photo-get-clusters-with-resources`, `mpfs/info`, `mpfs/url`. Любой другой метод → `IllegalArgumentException` (защита от случайной мутации).
- Rate limit: не чаще 1 запроса/сек; `photo-get-clusters-with-resources` — не более 5 кластеров за запрос и пауза 3 сек между такими запросами.
- HTTP 429 / капча (HTML вместо JSON, `captcha` в URL редиректа) → `RateLimited`: `pausedUntil = now + 24ч × 2^level` (макс 4 суток), тревога, цикл завершается.
- HTTP 401/403 или редирект на паспорт → `SessionExpired`, тревога, цикл завершается.

#### Синхронизация новых (`PhotosliceSync.syncDeltas`)

1. Если нет `photosliceId`/`revision` → `photo-init-snapshot` → сохранить оба, затем выполнить первичную догрузку (см. ниже) до первого вызова дельт.
2. `photo-get-deltas {id, base_revision: revision, locale:"ru"}`. Обработать `items[].items_changes[]`:
   - `change_type = insert` → если `etimeMs ≥ CUTOFF_MS` — upsert в `items` (`source=DELTA`, `status=QUEUED`), если `itemId` ещё нет; иначе игнорировать;
   - `update` → обновить `path`, если элемент ещё `QUEUED`;
   - `delete` → если `QUEUED`, пометить `SKIPPED` (удалён в Яндексе до скачивания); если уже на устройстве — ничего не делать.
   - Альбомы: пропускать элементы с `screenshots` в `albums`, если включено `"Пропускать скриншоты"`.
3. Если `total > limit` — повторять с новым `base_revision` до текущей `revision`.
4. Сохранить новую `revision` только после успешной записи в Room (транзакция).
5. Если `photo-get-deltas` вернул ошибку «ревизия слишком старая» → пере-инициализировать снимок и сделать дифф через `photo-get-snapshot` + `clusters-with-resources` по кластерам новее последнего известного `etime`.

#### Точка отсчёта (константа)

- `CUTOFF_MS = 1791410400000` — 2026-10-08T00:00:00+02:00 (Europe/Luxembourg). Константа в `BuildConfig`, не настраивается из UI.
- Дата съёмки элемента `etimeMs` берётся из второго сегмента `itemId` (`2_<etimeMs 19 цифр>_<hash>_<ver>`, например `2_0000001791392334000_…` → `1791392334000`); при сомнении уточняется по `mpfs/info.etime × 1000`.

#### Первичная догрузка (`PhotosliceSync.backfill`)

Нужна один раз, чтобы забрать фото, снятые с 08.10.2026 до первого запуска приложения.

1. Выполняется, если `backfillDone=false`, сразу после `photo-init-snapshot`.
2. `photo-get-snapshot {id}` → кластеры; взять только кластеры с `to ≥ CUTOFF_MS`.
3. Порциями по 5 кластеров: `photo-get-clusters-with-resources {photosliceId, clusters:{<id>:{range:[0,size-1]}}, hideScreenshots:<настройка>}`; брать `clusters.fetched[].items[]`, `missing` игнорировать.
4. Элементы с `etimeMs ≥ CUTOFF_MS` → upsert `source=BACKFILL`, `status=QUEUED`.
5. `backfillDone=true`. Дальше работают только дельты.

#### Скачивание (`NightCycleWorker` → `Downloader`)

1. Проверки: не пауза, нет `pausedUntil`, Wi-Fi, зарядка (если включено), температура батареи < 42°C.
2. `budget = StatFs(free) − reserve − bytesInFlight`. Если `budget ≤ 0` → тревога «мало места» (если свободно < reserve/2), конец скачивания.
3. Выборка: `QUEUED` и `DOWNLOADING` (докачка) по `etime ASC` — в порядке съёмки.
4. Для каждого элемента:
   1. `mpfs/info {path, meta:""}` → `size`, `mimetype`, `etime`. Если `size > budget` → пропустить в этом цикле.
   2. `mpfs/url {path}` → `file` (протокол-относительный URL) → `https:` + `file`. Ссылку не хранить — брать заново перед каждой попыткой.
   3. `MediaStoreWriter.beginPending(name, mime, etime)`: вставка в `MediaStore.Images`/`Video` с `RELATIVE_PATH="DCIM/Yandex/"`, `DISPLAY_NAME=name`, `DATE_TAKEN=etime*1000`, `IS_PENDING=1`. Если имя занято файлом с другим `itemId` → добавить суффикс ` (2)`.
   4. OkHttp GET без cookies, `Range: bytes=<downloadedBytes>-` при докачке; запись через `openOutputStream(uri, "wa")`, сохранение `downloadedBytes` каждые 4 МБ.
   5. По завершении: размер записанного == `size`, иначе `FAILED` и удалить pending-запись. Успех → `IS_PENDING=0`, `status=ON_DEVICE`, `onDeviceAt=now`.
   6. `budget -= size`.
5. Параллелизм — 2 загрузки одновременно (`Semaphore(2)`), пауза 1 сек между вызовами `models-v2`.
6. Ошибки сети — до 5 попыток на элемент с экспоненциальной задержкой; затем `FAILED` и запись в журнал. `FAILED` повторяются раз в сутки, после 3 суток — отчёт в утренней сводке.
7. Цикл заканчивается при выходе из окна, потере Wi-Fi/зарядки, исчерпании бюджета или очереди.

#### Определение загрузки в Google (`UploadDetector`)

1. UID Google Photos: `packageManager.getApplicationInfo("com.google.android.apps.photos", 0).uid`.
2. Раз в 30 минут (`CleanupWorker`) — `NetworkStatsManager.queryDetailsForUid(TYPE_WIFI, null, from, now, uid)` → суммарный `txBytes` с момента первой `traffic_mark`; сохранить снимок в `traffic_marks`.
3. Модель «очередь FIFO»: Google Photos загружает файлы примерно в порядке появления. `uploadedBytesEstimate = photosTx × 0.97` (поправка на протокольные накладные). Элементы `ON_DEVICE` в порядке `onDeviceAt` считаются загруженными, пока их накопленная сумма размеров ≤ `uploadedBytesEstimate − bytesЗагруженныхРанее`.
4. Дополнительное условие: прирост `txBytes` за последние 30 минут < 1 МБ (Google Photos «затих») — тогда все `ON_DEVICE`, покрытые оценкой, получают `UPLOADED_ASSUMED`.
5. Без доступа к статистике `UploadDetector` выключен.

#### Очистка (`Cleaner`)

- Режим «Осторожный»: удалять `UPLOADED_ASSUMED`, если с `onDeviceAt` прошло ≥ 6 часов (запас на дозагрузку видео).
- Режим «По таймеру»: удалять `ON_DEVICE` старше «Удалять через» (по умолчанию 48ч).
- Страховка в осторожном режиме: элемент `ON_DEVICE` старше 7 суток не удаляется, а попадает в тревогу «Google Photos не загружает».
- Удаление: `contentResolver.delete(mediaUri)` (собственный файл, без диалога на Android 10) → `status=DELETED`.
- Тревога «Google Photos не загружает 24 часа»: есть `ON_DEVICE` ≥ 24ч и прирост `txBytes` за 24ч < 50 МБ.

#### Утренний отчёт (`ReportWorker`)

- Считает события за последние 24 часа из `items`/`events`, формирует текст из шаблонов Части 1, отправляет `TelegramNotifier.send`.
- Если Telegram не настроен — пишет отчёт только в журнал.

#### Telegram (`TelegramNotifier`)

- `POST https://api.telegram.org/bot<token>/sendMessage` с `chat_id`, `text`, `disable_web_page_preview=true`.
- «Найти chat id»: `getUpdates` → `chat.id` последнего личного сообщения боту.
- Дедупликация тревог: одна и та же тревога не чаще раза в 6 часов (`lastAlertAt.<type>`).
- Ошибки отправки не роняют цикл; 3 попытки с задержкой.

### Детали: Взаимодействие с системой

- `Settings.ACTION_USAGE_ACCESS_SETTINGS` — экран доступа к статистике.
- `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — диалог батареи; проверка `PowerManager.isIgnoringBatteryOptimizations`.
- `Intent.ACTION_BATTERY_CHANGED` (sticky) — `EXTRA_TEMPERATURE` (десятые °C), `EXTRA_PLUGGED`, `EXTRA_LEVEL`.
- `StatFs(Environment.getExternalStorageDirectory())` — свободное место.
- Глубокая ссылка на Google Photos для шага 5 подготовки: `packageManager.getLaunchIntentForPackage("com.google.android.apps.photos")`.

### Детали: Внешние интеграции

| Интеграция | Endpoint | Авторизация | Где хранится |
|---|---|---|---|
| Яндекс Диск (внутренний API) | `https://disk.yandex.ru/models-v2?m=…`, `https://disk.yandex.ru/client/photo` | cookies сессии + `sk` | `CookieManager` (app data) |
| Яндекс downloader | `https://*.downloader.disk.yandex.ru/disk/…` (из `mpfs/url`) | подписанный URL | не хранится |
| Telegram Bot API | `https://api.telegram.org/bot<token>/…` | токен бота | DataStore, зашифровано Keystore |
| Firebase App Distribution | — (только дистрибуция) | — | — |

Ключей в коде и в репозитории нет — всё вводится в UI или приходит из GitHub Secrets при сборке.

Формы ответов (по тесту 2026-10-08):

- `photo-init-snapshot` → `{"photoslice_id":"…","revision":20918}`
- `photo-get-deltas` → `{"revision":…,"total":…,"limit":…,"items":[{"base_revision":…,"revision":…,"index_changes":[…],"items_changes":[{"change_type":"insert","cluster_id":"…","item_id":"…","data":{"path":"photounlim:/….JPG","albums":[…]}}]}]}`
- `photo-get-snapshot` → `{"id":"…","revision":…,"items":[{"id":"<cluster>","albums":{…},"from":…,"to":…,"size":N}]}`
- `photo-get-clusters-with-resources` → `{"clusters":{"fetched":[{"id":"<cluster>","size":N,"items":[{"id":"/photounlim/….JPG","itemId":"…","albums":[…]}]}],"missing":[…]}}`
- `mpfs/info` → `{"path":"…","name":"…","etime":…,"meta":{"size":…,"mimetype":"…","mediatype":"image|video",…}}`
- `mpfs/url` → `{"file":"//downloader.disk.yandex.ru/disk/…","digest":"…"}`

Пути: в дельтах `photounlim:/X`, в `clusters-with-resources` — `/photounlim/X`; в `mpfs/*` передавать в форме `/photounlim/X`. Элементы из обычных папок (`nonphotounlim`) приходят с путями Диска — передавать как есть.

### Детали: Локализация

- По умолчанию русский (`values/strings.xml`).
- Английский `values-en/strings.xml` — обязательный по шаблону.
- Тексты Telegram — только русский (шаблоны в `strings.xml` под префиксом `tg_`).

### Детали: Нефункциональные требования

- **Безопасность:** не логировать cookies, `sk`, токен Telegram, подписанные URL; в журнале — только имена файлов и коды ошибок. `allowBackup=false`. WebView только для домена Яндекса (`shouldOverrideUrlLoading` пропускает `*.yandex.ru`, `*.yandex.com`, `*.ya.ru`, остальное — блок).
- **Бережность к Яндексу:** строго только чтение (allow-list), не более 1 запроса/сек, архив не листается (только кластеры после точки отсчёта), экспоненциальный бэкофф при 429/капче.
- **Надёжность:** все шаги идемпотентны; курсор ревизии сдвигается только после коммита в Room; докачка по Range; переживает перезагрузку (WorkManager + pending-записи MediaStore, незавершённые pending старше 24ч удаляются при старте).
- **Батарея и нагрев:** пауза скачивания при температуре ≥ 42°C, продолжение при ≤ 38°C; foreground-уведомление только во время цикла.
- **Производительность:** потоковая запись без промежуточных копий (нет двойного расхода места).
- **UX:** тёмная тема, content description на всех кнопках, все тексты из `strings.xml`.

### Детали: Acceptance checklist

- [ ] Установка через Firebase App Distribution на Pixel 1 (Android 10) проходит.
- [ ] Чек-лист «Подготовка» корректно отражает состояние каждого шага.
- [ ] Вход в Яндекс в WebView проходит, после перезапуска приложения сессия сохраняется.
- [ ] `YandexClient` бросает исключение на любой метод вне allow-list (unit-тест).
- [ ] Новое фото, снятое на Pixel 9 Pro и загруженное в безлимит Яндекса, оказывается в `DCIM/Yandex/` Pixel 1 в следующем цикле.
- [ ] Размер и SHA-256 скачанного файла совпадают с оригиналом с Pixel 9 Pro (ручная проверка на 3 фото и 1 видео).
- [ ] Google Photos на Pixel 1 загружает файл из `DCIM/Yandex/`, место в Google-аккаунте не растёт.
- [ ] Обрыв Wi-Fi посреди скачивания видео → докачка с того же байта.
- [ ] Свободное место не опускается ниже резерва.
- [ ] Осторожная очистка удаляет файлы только после роста трафика Google Photos на их объём.
- [ ] Очистка по таймеру удаляет файлы старше заданного срока.
- [ ] Утренний отчёт и все шесть видов тревог приходят в Telegram с текстами из Части 1.
- [ ] При протухшей сессии — тревога и красная плашка на главном экране; ничего не падает.
- [ ] При 429 — пауза и бэкофф, повторная тревога не чаще раза в 6 часов.
- [ ] Фото, снятое до 08.10.2026 и попавшее в дельты, не скачивается.
- [ ] Фото, снятые 08.10.2026 до первого запуска, забираются первичной догрузкой.
- [ ] Повторно скачанных файлов нет (дедупликация по `itemId`).
- [ ] В журнале нет cookies, `sk`, токенов и URL.
- [ ] `branding/icon-source.png` существует, все `mipmap-*` разложены, adaptive icon отображается корректно.

### Детали: Сборка и дистрибуция (CI)

- Репозиторий **публичный**: [kissedcode/pixel1-keeper](https://github.com/kissedcode/pixel1-keeper). В нём нет ни одного секрета; всё приватное — в GitHub Secrets.
- Workflow `.github/workflows/build.yml`:
  - `pull_request` — unit-тесты + release-сборка без секретов (подпись debug-ключом), без раздачи;
  - `push` в `main` и ручной `workflow_dispatch` — тесты, подписанный release APK, раздача в Firebase App Distribution.
  - Если секретов нет — шаг раздачи пропускается с предупреждением, сборка не падает.
- Версия: `versionCode = github.run_number` (растёт с каждым запуском — бамп вручную не нужен), `versionName = <appVersionName из gradle.properties>.<versionCode>`. Смысловой бамп — правка `appVersionName`.
- Release notes — сообщение последнего коммита (или поле `release_notes` при ручном запуске) + номер сборки и SHA.
- Секреты (заливаются скриптом `scripts/setup-secrets.sh` с мака):

| Секрет | Что это |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | release-keystore `~/keystores/pixel1keeper-release.jks` в base64 |
| `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_PASSWORD`, `ANDROID_KEY_ALIAS` | пароли и alias (пароль также в связке ключей macOS, сервис `pixel1keeper-keystore`) |
| `FIREBASE_SERVICE_ACCOUNT` | JSON-ключ сервисного аккаунта `appdist-ci@pixel1keeper-kissed.iam.gserviceaccount.com`, роль `roles/firebaseappdistro.admin` |
| `FIREBASE_APP_ID` | `1:784451679346:android:1cc0270f80df61bbd6beeb` |
| `FIREBASE_TESTERS` | список тестеров через запятую (в секрете, чтобы почты не светились в публичном репо) |
| `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID` | необязательно; значения Telegram по умолчанию в сборке |

### Детали: Known current deployment state

- Версия: `0.1.0.<run_number>` — первая сборка после настройки секретов.
- Firebase project: `pixel1keeper-kissed`; Android App ID: `1:784451679346:android:1cc0270f80df61bbd6beeb`.
- Тестеры в Firebase: два дефолтных адреса владельца (добавлены 2026-10-08); список — только в секрете `FIREBASE_TESTERS`, в публичном репозитории адресов нет.
- Репозиторий: публичный `kissedcode/pixel1-keeper`, ветка `main`.
- Проверено в песочнице 2026-10-08: unit-тесты, debug и подписанный release APK (R8), lint без ошибок. На устройстве ещё не запускалось.

### Детали: История версий

- 2026-10-08 — первая версия спецификации.
- 2026-10-08 — архив исключён из v1; точка отсчёта — фото, снятые с 08.10.2026.
- 2026-10-08 — v0.1.0: первая реализация; публичный репозиторий, сборка и раздача через GitHub Actions, секреты в GitHub Secrets.
