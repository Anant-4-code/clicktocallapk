# CallBridge Android App

Android app that listens for encrypted call requests from `ntfy.sh`, decrypts on-device, and dials through the phone app.

Project path:
`c:\Users\HP\Downloads\Click To Call\CallBridgeAndroid`

Server currently configured in code:
`https://redirectorhook-production.up.railway.app`

---

## 1) Architecture Overview

### Main components

- `MainActivity` - onboarding, register/sync/re-register UI, permission flow
- `NtfyListenerService` - foreground background listener for `ntfy.sh/<topic>/json`
- `CallCrypto` - decrypts `enc:v1:...` payload using AES-GCM
- `SecretDeriver` - derives per-agent key with HMAC-SHA256
- `PhoneNumbers` - validates and normalizes Indian numbers (`10`, `91`, `+91`, `0`)
- `CallHelper` - attempts direct call and fallback dialer open
- `ServiceStarter` / `ServiceWatchdog` / `BootReceiver` - keep listener alive after kill/reboot

### Runtime flow

1. User enters agent name and taps Register.
2. App creates topic `callbridge-<agent>-<random>` and calls `POST /register`.
3. App stores:
   - `agent_id`
   - `ntfy_topic`
   - `agent_secret` (server-provided; same as derived key)
4. Foreground service subscribes to `https://ntfy.sh/<topic>/json`.
5. On each message:
   - read `message` field
   - if `enc:v1:...`, derive key and decrypt
   - normalize number and place call
6. If app is killed/rebooted, watchdog/boot receiver restarts service.

---

## 2) Encryption Details

### Method used

- Cipher: `AES/GCM/NoPadding` (AES-256-GCM)
- Prefix: `enc:v1:`
- Packed payload format: `iv(12) + authTag(16) + ciphertext`
- Encoding: URL-safe Base64 (no padding)

### Key derivation

- Derivation: `HMAC-SHA256(PEPPER, lowercase(agentId))`
- App pepper constant: `callbridge-shared-key-v1`
- Must match server `CALLBRIDGE_PEPPER` behavior.

### Important behavior

- App never trusts random digits inside encrypted strings.
- If decryption fails, it shows re-register hint.
- Legacy plain-number messages are still handled for compatibility.

---

## 3) Requirements

- Android `minSdk 26`
- Compile/target SDK `34`
- Java/Kotlin target `17`
- Internet access
- Android Studio recommended for build/install

Dependencies:
- `okhttp` for streaming ntfy messages
- `kotlinx-coroutines-android`
- AndroidX appcompat/core-ktx

---

## 4) Permissions and Device Settings

### Manifest permissions

- `INTERNET`
- `CALL_PHONE`
- `READ_PHONE_STATE`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_DATA_SYNC`
- `POST_NOTIFICATIONS`
- `RECEIVE_BOOT_COMPLETED`
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- `WAKE_LOCK`
- `SCHEDULE_EXACT_ALARM`

### Runtime permissions

App asks for:
- Phone permission (`CALL_PHONE`)
- Read phone state (`READ_PHONE_STATE`)
- Notification permission on Android 13+

### Device settings to enable (critical on Xiaomi/Realme/Oppo/Vivo)

- Allow autostart/background activity for CallBridge
- Disable battery optimization for CallBridge
- Allow persistent notification (foreground service)
- Ensure SIM/network is active for actual dialing

---

## 5) Build and Install

### Option A: Android Studio

1. Open `CallBridgeAndroid` folder in Android Studio.
2. Wait for Gradle sync.
3. Build -> Build APK(s).
4. APK path:
   `app/build/outputs/apk/debug/app-debug.apk`

### Option B: Terminal

```powershell
cd "c:\Users\HP\Downloads\Click To Call\CallBridgeAndroid"
gradlew.bat assembleDebug
```

APK output:
`app/build/outputs/apk/debug/app-debug.apk`

### Install to phone

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

or copy APK manually and install from file manager.

---

## 6) First-time Setup on Phone

1. Open app.
2. Enter exact agent ID used in sheet links (example: `rahul`).
3. Tap Register.
4. Grant requested permissions.
5. Keep app background service allowed.
6. Tap Sync if needed.
7. Use "Test call (9999999999)" button.

If the app is already paired and you changed server encryption behavior:
- tap Re-register, then register again.

---

## 7) How Calls Are Triggered

A sheet click eventually sends encrypted payload to ntfy topic.
App receives it and tries:

1. `TelecomManager.placeCall`
2. `Intent.ACTION_CALL`
3. fallback `Intent.ACTION_DIAL` (user taps green call button)

Multiple output formats are attempted to improve device compatibility:
- `+91xxxxxxxxxx`
- `91xxxxxxxxxx`
- `xxxxxxxxxx`

---

## 8) Operations Checklist

Before daily use:

- [ ] App shows correct agent name
- [ ] Topic is present on status screen
- [ ] Foreground listener notification exists
- [ ] Battery optimization disabled for app
- [ ] Autostart enabled
- [ ] Test call button works
- [ ] Sheet link for same agent is correct

After server deploy:

- [ ] Open app once (auto-sync registration)
- [ ] If needed tap Sync/Re-register

---

## 9) Troubleshooting

### "Agent not registered" from server
- Open app and tap Sync.
- Confirm agent name in app equals `agent=` in sheet link.

### Notification received but no dial
- Check phone permission.
- Check SIM/network availability.
- Check battery/autostart restrictions.

### Decrypt failure / re-register warning
- Re-register app.
- Ensure server and app use same pepper/derivation logic.

### Wrong number format
- App supports `10-digit`, `91...`, `+91...`, `0...`.
- If still failing, inspect incoming message format and logs.

### Service stops in background
- Enable OEM autostart and no battery restrictions.
- Keep foreground notification allowed.

---

## 10) Integration Contract with Server

### Register request
`POST /register` body:
```json
{
  "agentId": "rahul",
  "ntfyTopic": "callbridge-rahul-abc123"
}
```

### Register response expected
```json
{
  "success": true,
  "agentSecret": "<64 hex chars>"
}
```

### Incoming ntfy message expected

- Message body should be encrypted payload in `message` field:
  - `enc:v1:...`

Legacy plain text still supported, but encrypted payload is the standard.
#
