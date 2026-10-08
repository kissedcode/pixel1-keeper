#!/usr/bin/env bash
# Однократная настройка секретов GitHub Actions для kissedcode/pixel1-keeper.
# Запускать на маке:  bash scripts/setup-secrets.sh
# Что делает:
#   1. Создаёт release-keystore в ~/keystores (если ещё нет), пароль — в связке ключей macOS.
#   2. Создаёт сервисный аккаунт Firebase App Distribution и его ключ (через gcloud).
#   3. Загружает всё в GitHub Secrets репозитория. Локально ключ сервисного аккаунта удаляется.
# Ничего секретного в репозиторий и в вывод не попадает.
set -euo pipefail

REPO="kissedcode/pixel1-keeper"
PROJECT="pixel1keeper-kissed"
APP_ID="1:784451679346:android:1cc0270f80df61bbd6beeb"
KEYSTORE="$HOME/keystores/pixel1keeper-release.jks"
ALIAS="upload"
KC_SERVICE="pixel1keeper-keystore"
SA_NAME="appdist-ci"
SA_EMAIL="$SA_NAME@$PROJECT.iam.gserviceaccount.com"

say() { printf '\n==> %s\n' "$*"; }
need() { command -v "$1" >/dev/null 2>&1 || { echo "Нужна утилита: $1 ($2)"; exit 1; }; }

need gh "brew install gh"
need keytool "brew install openjdk@17"
need base64 "-"

# --- GitHub: нужен аккаунт-владелец репозитория
# Токен kissedcode берём только для этого скрипта — активный аккаунт gh не меняется.
say "GitHub: использую аккаунт kissedcode"
GH_TOKEN="$(gh auth token -h github.com -u kissedcode 2>/dev/null || true)"
[ -n "$GH_TOKEN" ] || { echo "Войдите в gh под kissedcode: gh auth login"; exit 1; }
export GH_TOKEN
gh repo view "$REPO" --json name >/dev/null || { echo "Нет доступа к $REPO под текущим аккаунтом gh"; exit 1; }

# --- 1. Keystore
if [ -f "$KEYSTORE" ]; then
  say "Keystore уже есть: $KEYSTORE"
  KS_PASS="$(security find-generic-password -s "$KC_SERVICE" -a "$ALIAS" -w 2>/dev/null || true)"
  if [ -z "$KS_PASS" ]; then
    read -rsp "Пароль от существующего keystore: " KS_PASS; echo
  fi
else
  say "Создаю release keystore"
  mkdir -p "$(dirname "$KEYSTORE")"
  KS_PASS="$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 32)"
  keytool -genkeypair -v -keystore "$KEYSTORE" -storetype PKCS12 \
    -storepass "$KS_PASS" -keypass "$KS_PASS" -alias "$ALIAS" \
    -keyalg RSA -keysize 4096 -validity 10000 \
    -dname "CN=Pixel1 Keeper, O=kissedcode, C=LU" >/dev/null
  security add-generic-password -U -s "$KC_SERVICE" -a "$ALIAS" -w "$KS_PASS"
  echo "Пароль сохранён в связке ключей macOS (сервис $KC_SERVICE). Сделайте резервную копию $KEYSTORE — без него обновления не установятся поверх."
fi

# --- 2. Сервисный аккаунт Firebase
if ! command -v gcloud >/dev/null 2>&1; then
  say "Ставлю Google Cloud CLI (нужен для ключа сервисного аккаунта)"
  brew install --cask gcloud-cli
  # shellcheck disable=SC1091
  source "$(brew --prefix)/share/google-cloud-sdk/path.bash.inc" 2>/dev/null || true
fi
if ! gcloud auth list --filter=status:ACTIVE --format='value(account)' | grep -q .; then
  say "Вход в Google (выберите аккаунт-владелец Firebase-проекта)"
  gcloud auth login --brief
fi
gcloud config set project "$PROJECT" >/dev/null 2>&1

say "Включаю Firebase App Distribution API"
gcloud services enable firebaseappdistribution.googleapis.com --project "$PROJECT" >/dev/null

if ! gcloud iam service-accounts describe "$SA_EMAIL" --project "$PROJECT" >/dev/null 2>&1; then
  say "Создаю сервисный аккаунт $SA_EMAIL"
  gcloud iam service-accounts create "$SA_NAME" --project "$PROJECT" --display-name "App Distribution CI" >/dev/null
fi
gcloud projects add-iam-policy-binding "$PROJECT" \
  --member "serviceAccount:$SA_EMAIL" --role roles/firebaseappdistro.admin --condition=None >/dev/null

TMP_DIR="$(mktemp -d)"
TMP_KEY="$TMP_DIR/sa.json"
trap 'rm -rf "$TMP_DIR"' EXIT
say "Создаю ключ сервисного аккаунта (локальная копия будет удалена)"
gcloud iam service-accounts keys create "$TMP_KEY" --iam-account "$SA_EMAIL" --project "$PROJECT" >/dev/null

# --- 3. GitHub Secrets
say "Загружаю секреты в $REPO"
base64 -i "$KEYSTORE" | tr -d '\n' | gh secret set ANDROID_KEYSTORE_BASE64 -R "$REPO"
printf '%s' "$KS_PASS" | gh secret set ANDROID_KEYSTORE_PASSWORD -R "$REPO"
printf '%s' "$KS_PASS" | gh secret set ANDROID_KEY_PASSWORD -R "$REPO"
printf '%s' "$ALIAS"   | gh secret set ANDROID_KEY_ALIAS -R "$REPO"
gh secret set FIREBASE_SERVICE_ACCOUNT -R "$REPO" < "$TMP_KEY"
printf '%s' "$APP_ID"  | gh secret set FIREBASE_APP_ID -R "$REPO"

TESTERS=""
while [ -z "$TESTERS" ]; do read -rp "Email тестеров через запятую: " TESTERS; done
printf '%s' "$TESTERS" | gh secret set FIREBASE_TESTERS -R "$REPO"

echo
read -rp "Задать Telegram по умолчанию (токен бота и chat id попадут в сборку)? [y/N]: " TG
if [[ "$TG" =~ ^[Yy]$ ]]; then
  read -rsp "Токен бота: " TG_TOKEN; echo
  read -rp "Chat id: " TG_CHAT
  printf '%s' "$TG_TOKEN" | gh secret set TELEGRAM_BOT_TOKEN -R "$REPO"
  printf '%s' "$TG_CHAT"  | gh secret set TELEGRAM_CHAT_ID -R "$REPO"
fi

say "Готово. Секреты в репозитории:"
gh secret list -R "$REPO"
echo
echo "Запустить сборку и раздачу:  gh workflow run build.yml -R $REPO"
