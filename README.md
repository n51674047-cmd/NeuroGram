# NeuroGram

NeuroGram — Android-мессенджер с тёмным интерфейсом, красными неоновыми акцентами и минималистичным дизайном.

## Что уже есть

- Экран входа и регистрации (пока демонстрационный, без сервера).
- Список демонстрационных чатов.
- Экран просмотра чата и профиль-заглушка.
- Тёмная тема на Jetpack Compose.
- GitHub Actions workflow для автоматической сборки debug APK.

## Что ещё предстоит подключить

- Firebase Authentication для реальных аккаунтов.
- Firestore для синхронизации сообщений и профилей.
- Загрузку изображений и групповые чаты.
- Статусы присутствия и push-уведомления.

**Важно:** текущая версия — интерфейсный прототип. Данные чатов демонстрационные; регистрация и отправка сообщений между устройствами пока не работают.

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
3. In **Project Settings → Script properties**, add:
   - `FIREBASE_API_KEY` — the Web API key from Firebase Console → Project settings → General.
   - `CODE_PEPPER` — a private random string of at least 32 characters. Do not put it in GitHub or the app.
4. In Firebase Console, make sure **Authentication → Sign-in method → Email/Password** is enabled.
5. In Apps Script, choose **Deploy → New deployment → Web app**:
   - Execute as: **Me**
   - Who has access: **Anyone**
6. Authorize Gmail and external requests when Google asks. Copy the deployed Web app URL ending in `/exec`.
7. Send that URL to the project maintainer so it can be inserted into `VERIFICATION_API_URL` in `MainActivity.kt`, then rebuild the APK.

The script sends mail from the Google account that owns the Apps Script project. Google Apps Script has daily email quotas, so this is suitable for an early test build, not a high-volume production service. The endpoint is public; it enforces a one-minute resend cooldown per email, a ten-minute code lifetime and five verification attempts.
