# NovixGram

NovixGram — Android-мессенджер с тёмным интерфейсом, красными неоновыми акцентами и минималистичным дизайном.

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
2. Выбери workflow **Build NovixGram APK**.
3. После завершения сборки открой запуск workflow.
4. Скачай artifact **NovixGram-debug** — внутри находится `app-debug.apk`.

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
8. Copy the deployed Web app URL ending in `/exec`. It must be inserted into `VERIFICATION_API_URL` in `MainActivity.kt`, then a new APK must be built.

The script sends mail from the Google account that owns the Apps Script project. Google Apps Script has daily email quotas, so this is suitable for an early test build, not a high-volume production service. The endpoint is public; it enforces a one-minute resend cooldown per email, a ten-minute code lifetime and five verification attempts.
