/**
 * NeuroGram 4-digit email verification backend.
 * Deploy as a Google Apps Script Web App: Execute as Me; access Anyone.
 * Script Properties: FIREBASE_API_KEY and CODE_PEPPER (32+ random chars).
 */

// Safe public health check. It never returns either Script Property value.
function doGet() {
  var props = PropertiesService.getScriptProperties();
  var pepper = String(props.getProperty("CODE_PEPPER") || "");
  var apiKey = String(props.getProperty("FIREBASE_API_KEY") || "").trim();
  var ready = pepper.length >= 32 && apiKey.length > 0;
  return ContentService.createTextOutput(JSON.stringify({
    ok: true,
    service: "NeuroGram verification API",
    apiVersion: 1,
    ready: ready
  })).setMimeType(ContentService.MimeType.JSON);
}

function doPost(e) {
  try {
    var data = JSON.parse((e && e.postData && e.postData.contents) || "{}");
    var action = String(data.action || "");
    var email = String(data.email || "").trim().toLowerCase();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return output(false, "Проверь адрес электронной почты.");
    if (action === "sendCode") return sendCode(email);
    if (action === "verifyAndCreate") return verifyAndCreate(email, String(data.code || ""), String(data.password || ""));
    return output(false, "Неизвестная операция.");
  } catch (err) {
    console.error(err);
    return output(false, "Внутренняя ошибка сервера. Попробуй позже.");
  }
}

function sendCode(email) {
  var props = PropertiesService.getScriptProperties();
  var pepper = props.getProperty("CODE_PEPPER");
  if (!pepper || pepper.length < 32) return output(false, "Сервер не настроен: добавь CODE_PEPPER.");
  var key = stateKey(email);
  var lock = LockService.getScriptLock();
  lock.waitLock(10000);
  var code;
  try {
    var previous = JSON.parse(props.getProperty(key) || "{}");
    var now = Date.now();
    if (previous.lastSentAt && now - previous.lastSentAt < 60000) {
      return output(false, "Новый код можно запросить через " + Math.ceil((60000 - (now - previous.lastSentAt)) / 1000) + " сек.");
    }
    code = ("0000" + (parseInt(hash(Utilities.getUuid() + Date.now() + pepper).slice(0, 8), 16) % 10000)).slice(-4);
    props.setProperty(key, JSON.stringify({
      codeHash: hash(email + "|" + code + "|" + pepper),
      expiresAt: now + 10 * 60 * 1000,
      lastSentAt: now,
      attempts: 0,
      processing: false
    }));
  } finally {
    lock.releaseLock();
  }
  try {
    MailApp.sendEmail(email, "Код подтверждения NeuroGram",
      "Твой код подтверждения NeuroGram: " + code + "\n\nКод действует 10 минут. Никому его не сообщай.",
      { htmlBody: '<div style="font-family:Arial,sans-serif;padding:24px;color:#17243a"><h2>NeuroGram</h2><p>Код подтверждения почты:</p><div style="font-size:34px;font-weight:bold;letter-spacing:10px;padding:16px 0">' + code + '</div><p>Код действует 10 минут. Никому его не сообщай.</p></div>' });
    return output(true, "Код отправлен на " + email + ".");
  } catch (err) {
    props.deleteProperty(key);
    console.error(err);
    return output(false, "Не удалось отправить письмо. Проверь разрешение на отправку почты у Apps Script.");
  }
}

function verifyAndCreate(email, code, password) {
  if (!/^\d{4}$/.test(code)) return output(false, "Введи четырёхзначный код.");
  if (password.length < 6 || password.length > 128) return output(false, "Пароль должен содержать от 6 до 128 символов.");
  var props = PropertiesService.getScriptProperties();
  var pepper = props.getProperty("CODE_PEPPER");
  var apiKey = props.getProperty("FIREBASE_API_KEY");
  if (!pepper || !apiKey) return output(false, "Сервер не настроен: добавь FIREBASE_API_KEY и CODE_PEPPER.");
  var key = stateKey(email);
  var lock = LockService.getScriptLock();
  lock.waitLock(10000);
  var state;
  try {
    state = JSON.parse(props.getProperty(key) || "{}");
    if (!state.codeHash || Date.now() > Number(state.expiresAt || 0)) {
      props.deleteProperty(key);
      return output(false, "Код истёк. Запроси новый.");
    }
    if (state.processing) return output(false, "Проверка уже выполняется. Подожди немного.");
    if (Number(state.attempts || 0) >= 5) {
      props.deleteProperty(key);
      return output(false, "Слишком много попыток. Запроси новый код.");
    }
    if (hash(email + "|" + code + "|" + pepper) !== state.codeHash) {
      state.attempts = Number(state.attempts || 0) + 1;
      props.setProperty(key, JSON.stringify(state));
      if (state.attempts >= 5) {
        props.deleteProperty(key);
        return output(false, "Лимит попыток исчерпан. Запроси новый код.");
      }
      return output(false, "Неверный код. Осталось попыток: " + (5 - state.attempts) + ".");
    }
    state.processing = true;
    props.setProperty(key, JSON.stringify(state));
  } finally {
    lock.releaseLock();
  }

  // Firebase Auth user is created server-side only after the code matches.
  var url = "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=" + encodeURIComponent(apiKey);
  var response;
  try {
    response = UrlFetchApp.fetch(url, {
      method: "post",
      contentType: "application/json",
      payload: JSON.stringify({ email: email, password: password, returnSecureToken: true }),
      muteHttpExceptions: true
    });
  } catch (err) {
    resetProcessing(key, state);
    console.error(err);
    return output(false, "Не удалось связаться с Firebase. Попробуй ещё раз.");
  }
  var result = JSON.parse(response.getContentText() || "{}");
  if (response.getResponseCode() >= 200 && response.getResponseCode() < 300 && result.localId) {
    props.deleteProperty(key);
    return output(true, "Почта подтверждена. Аккаунт создан.");
  }
  var firebaseError = result && result.error && result.error.message ? String(result.error.message) : "";
  props.deleteProperty(key);
  if (firebaseError.indexOf("EMAIL_EXISTS") >= 0) return output(false, "Аккаунт с этой почтой уже существует. Попробуй войти.");
  if (firebaseError.indexOf("WEAK_PASSWORD") >= 0) return output(false, "Пароль слишком простой. Используй не менее 6 символов.");
  if (firebaseError.indexOf("INVALID_EMAIL") >= 0) return output(false, "Firebase отклонил адрес электронной почты.");
  console.error("Firebase signUp failed: " + firebaseError);
  return output(false, "Firebase не смог создать аккаунт. Проверь настройки проекта.");
}

function resetProcessing(key, previousState) {
  var lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    var props = PropertiesService.getScriptProperties();
    var current = JSON.parse(props.getProperty(key) || "{}");
    if (current.codeHash === previousState.codeHash) {
      current.processing = false;
      props.setProperty(key, JSON.stringify(current));
    }
  } finally {
    lock.releaseLock();
  }
}

function stateKey(email) { return "VERIFY_" + hash(email); }
function hash(value) {
  var bytes = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, value, Utilities.Charset.UTF_8);
  return bytes.map(function (b) {
    var v = (b + 256) % 256;
    return ("0" + v.toString(16)).slice(-2);
  }).join("");
}
function output(ok, message) {
  return ContentService.createTextOutput(JSON.stringify({ ok: ok, message: message }))
    .setMimeType(ContentService.MimeType.JSON);
}
