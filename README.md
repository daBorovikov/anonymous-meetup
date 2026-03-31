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

