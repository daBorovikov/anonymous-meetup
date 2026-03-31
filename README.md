# AnonymousMeetup

## Local Run
1. Установите Android Studio с JDK 17.
2. Положите `google-services.json` в `app/google-services.json`.
3. Проверьте `local.properties` и Android SDK.
4. Запустите:
```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```
5. Откройте debug build на устройстве или эмуляторе.

## Debug Verification
В debug build откройте `Профиль -> Debug tools`.

Доступные действия:
- `Reset state`: очищает локальные conversations, message history, processed envelopes, joined groups, group keys, conversation secrets, legacy private sessions и encounter cache. Identity keys не удаляются.
- `Clear legacy`: очищает legacy локальные кэши.
- `Resync groups`: перечитывает локально присоединённые группы с сервера.
- `Run scenario`: создаёт новую группу, ждёт появления в поиске, заново вступает в неё и отправляет тестовое групповое сообщение.
- `Trace log`: показывает последние debug traces для create/join/anonymous messaging flow.

## Firestore Deploy
Текущий код ожидает новый контракт `groups` и `anonymous_envelopes`.
Если live backend работает на старых rules, `createGroup` будет падать с `PERMISSION_DENIED` даже при корректном Android-коде.

Локально:
```bash
npx firebase-tools login
npx firebase-tools deploy --only firestore --project anonymousmeetup-b5fb8
```

Если `npx firebase-tools login` не открывает браузер:
```bash
npx firebase-tools login --no-localhost
```

## GitHub Actions CI/CD
Workflow: `.github/workflows/android-ci.yml`

### CI
Запускается на:
- `push` в `main` и `develop`
- `pull_request` в `main` и `develop`
- `workflow_dispatch`

Что делает:
- checkout
- Java 17
- Node.js 20
- Gradle cache
- восстановление `google-services.json` из секрета
- `assembleDebug`
- `testDebugUnitTest`
- загрузка debug APK как artifact

### CD
Job `deploy-firestore` запускается:
- на `push` в `main`
- или вручную через `workflow_dispatch`

Он деплоит:
- `firestore.rules`
- `firestore.indexes.json`

### GitHub Secrets
Нужно создать secrets в репозитории GitHub:

1. `GOOGLE_SERVICES_JSON_BASE64`
Используется для сборки Android-приложения в CI.

PowerShell-команда для получения base64:
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('D:\Users\mrbor\Desktop\Codex\app\google-services.json'))
```

2. `FIREBASE_SERVICE_ACCOUNT_JSON`
Используется для deploy Firestore в CD.

Как получить:
- Firebase Console
- `Project settings`
- `Service accounts`
- `Generate new private key`
- содержимое JSON-файла вставить в secret целиком

После этого workflow сможет не только собирать проект, но и деплоить Firestore-конфигурацию.

## Legacy Groups
В Firestore могут лежать старые документы групп с полями вроде `private`, `memberLogins`, `memberIds`, `members`, `creatorId`.

Текущее поведение:
- чтение legacy документов безопасно и без `toObject(Group::class.java)` предупреждений;
- группы без `joinToken` не ломают приложение;
- такие группы помечаются как legacy / unsupported для join;
- в debug build доступна ручная миграция legacy группы в новый формат.

Новые группы создаются только в новом формате.

## Current Firestore Contract
### groups
```json
{
  "id": "String",
  "name": "String",
  "description": "String",
  "category": "String",
  "isPrivate": false,
  "groupHash": "String",
  "poolId": "group_pool_01",
  "joinToken": "String",
  "createdAt": 1710000000000
}
```

### anonymous_envelopes
```json
{
  "poolId": "private_pool_01",
  "ciphertext": "String",
  "nonce": "String",
  "timestamp": 1710000000000,
  "ttlSeconds": 2592000,
  "version": 1
}
```
