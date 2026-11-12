# NeuroGram

NeuroGram — Android-мессенджер с тёмным интерфейсом, красными неоновыми акцентами и минималистичным дизайном.

## NovixGram 0.4.0

Глобальное обновление добавляет:
- Групповые чаты: создание по username, список участников, добавление/удаление участников администраторами и выход из группы.
- Действия с сообщениями: ответы, пересылка, реакции, редактирование и удаление собственных сообщений, закрепление.
- Отправку изображений и видео через Firebase Storage.
- Статус профиля, который отображается в публичной карточке.
- Обновлённый интерфейс переписки, превью закреплённого сообщения и групповые уведомления в коде Cloud Functions.
- Обновлённые Firestore и Storage Rules.

### Важно перед использованием новых функций
Новые правила находятся в `firestore.rules` и `storage.rules`. Их нужно опубликовать в Firebase Console (Firestore Database → Rules и Storage → Rules), иначе групповые чаты и отправка медиа могут отклоняться действующими правилами. Серверные push-уведомления для групп требуют развернутой Cloud Function; на бесплатном Spark эта функция не запускается. Остальные функции не требуют перехода на Blaze.

## Реализовано в исходниках

- Вход через Firebase Authentication (email/password).
- Регистрация с четырёхзначным кодом подтверждения по email через Google Apps Script.
- Профили и уникальные username в Firestore.
- Поиск пользователя по username и создание личного чата.
- Список чатов и обмен текстовыми сообщениями через Firestore.
- Статусы отправки/доставки/прочтения сообщений.
- Светлая и тёмная темы, настройки интерфейса.
- Загрузка аватара через Firebase Storage.
- GitHub Actions для сборки debug APK.

Часть функций зависит от корректной настройки Firebase, публикации Firestore/Storage Rules и развёртывания Apps Script. Это ранняя тестовая версия, не готовая к публичному запуску.

## Сборка APK

1. Открой вкладку **Actions** репозитория.
2. Выбери workflow **Build NeuroGram APK**.
3. После завершения сборки открой запуск workflow.
4. Скачай artifact **NeuroGram-debug** — внутри находится `app-debug.apk`.

## Технологии

- Kotlin
- Jetpack Compose
- Android Gradle Plugin
- GitHub Actions

## Four-digit email verification (registration)

The app does **not** create a Firebase account when the user first taps “Get code”. The Google Apps Script backend sends a four-digit code, checks its expiry (10 minutes) and limits wrong attempts to five. Only after a correct code does the backend call Firebase Authentication to create the account.

### One-time setup

1. Open [Google Apps Script](https://script.google.com/) and create a new project.
2. Copy the contents of `backend/google-apps-script/Code.gs` into the script editor and save.
3. In Apps Script **Project Settings**, enable **Show "appsscript.json" manifest file in editor**. Open the manifest and replace it with the contents of `backend/google-apps-script/appsscript.json`. This limits OAuth permissions to sending email and making the Firebase HTTP request.
4. In **Project Settings → Script properties**, add:
   - `FIREBASE_API_KEY` — the Web API key from Firebase Console → Project settings → General.
   - `CODE_PEPPER` — a private random string of at least 32 characters. Do not put it in GitHub or the app.
5. In Firebase Console, make sure **Authentication → Sign-in method → Email/Password** is enabled.
6. In Apps Script, choose **Deploy → New deployment → Web app**:
   - Execute as: **Me**
   - Who has access: **Anyone**
7. Authorize **sending email** and **external requests** when Google asks. The script uses `MailApp`, not `GmailApp`, so it does not need permission to read or manage the mailbox. Google may still show an "app not verified" warning because this is a private Apps Script project; only proceed if you recognize the project and its code.
8. Copy the deployed Web app URL ending in `/exec`. The current Android source already contains a deployed `/exec` URL in `VERIFICATION_API_URL`; only change it if you create a different deployment.

### Safe deployment check (no email is sent)

After copying the updated `Code.gs` into Apps Script, open the deployed `/exec` URL in your phone browser. The `doGet()` health check returns JSON similar to:

```json
{
  "ok": true,
  "service": "NeuroGram verification API",
  "apiVersion": 1,
  "ready": true
}
```

- `ready: true` means both Script Properties exist and `CODE_PEPPER` is at least 32 characters. The response never returns either property value.
- `ready: false` means to recheck the exact property names `FIREBASE_API_KEY` and `CODE_PEPPER`.
- Opening this URL only checks the backend; it does not send an email or create a Firebase account.

If you edit an already deployed script, open **Deploy → Manage deployments**, choose the web app deployment, press **Edit**, select **New version**, and deploy. Then open the same `/exec` URL again. The URL usually stays the same when updating that deployment.

### Registration checks

1. First confirm the health-check response contains `"ready": true`.
2. In the app, open **Registration**, enter an email, password and username, then tap **Get code**.
3. Check the inbox and spam folder. The code expires after 10 minutes; another code can be requested after the one-minute resend cooldown.
4. Enter the four-digit code. The Firebase account is created only after the code is accepted.

The script sends mail from the Google account that owns the Apps Script project. Google Apps Script has daily email quotas, so this is suitable for an early test build, not a high-volume production service. The endpoint is public; it enforces a one-minute resend cooldown per email, a ten-minute code lifetime and five verification attempts.
