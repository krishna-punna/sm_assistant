# 00 — Foundation (P0) and release (P7)

Cross-cutting work that the feature tasks depend on. Do these first: today there are no tests, screen logic lives inside composables, and keys are stored unencrypted.

## Foundation

| ID | Type | Task | Depends on | Status |
|----|------|------|-----------|--------|
| FND-1 | Impl | **Test setup.** Add `kotlinx-coroutines-test`, `mockwebserver` (OkHttp), `turbine`, Robolectric to the version catalog. Create `app/src/test` and `app/src/androidTest` source sets with one sample test each. | — | done — JUnit, coroutines-test, Robolectric 4.16 (`sdk=35`); MockWebServer/Turbine added with the first client that needs them; Compose UI tests run on the JVM under Robolectric (`app/src/test`), so no emulator is needed for them |
| FND-2 | Impl | **CI runs tests.** Extend `.github/workflows/android.yml`: `./gradlew lint testDebugUnitTest assembleDebug`; upload test reports. Add a second job running `connectedDebugAndroidTest` on an emulator (`reactivecircus/android-emulator-runner`), on PRs only. | FND-1 | partial — CI runs `lintDebug testDebugUnitTest assembleDebug` and uploads reports; emulator job added with the first UI test |
| FND-3 | Impl | **ViewModel per screen.** Move state and side effects out of composables into `ViewModel`s exposing `StateFlow<UiState>`; composables become stateless (`Screen(state, onEvent)`), so they can be UI-tested with fake state. Start with settings, API Status, hashtags. | FND-7 | todo |
| FND-4 | Impl | **Secure key storage.** Store API keys encrypted with an Android Keystore key (Jetpack DataStore + Tink `Aead`, since `EncryptedSharedPreferences` is deprecated). Migrate existing values from the `api_settings` prefs on first run. Non-secret settings (URLs, hashtags, toggles) go in a plain DataStore. | FND-1 | done — `KeystoreCipher` (AES-256-GCM, Keystore key) encrypts keys in `ApiSettingsStore`; old plain-text keys migrate on first read. URLs/hashtags stay in plain SharedPreferences (not secret), so no DataStore/Tink dependency |
| FND-5 | Impl | **HTTP layer.** One `OkHttpClient` (10 s timeouts, no body logging in release) + `kotlinx.serialization` JSON. Base URLs injectable so tests can point at MockWebServer. Port `ApiConnectionCheck` to it. | FND-1 | partial — `HttpURLConnection` in `ApiConnectionCheck` only |
| FND-6 | Impl | **Error model and key redaction.** A shared `ApiResult`/`ApiError` (NotConfigured, InvalidKey, NoAccess, RateLimited, Unreachable, Server(code)) used by every client. An OkHttp interceptor/log wrapper that strips `Authorization`, `x-api-key`, `x-goog-api-key` from any log. | FND-5 | todo |
| FND-7 | Impl | **Dependency wiring.** A small manual `AppContainer` (settings store, HTTP client, agent clients, LinkedIn client) created in `Application`, passed to ViewModel factories. Lets tests swap fakes. | — | todo |
| FND-8 | Impl | **Release-safe config.** Fail the build if `app.yml` contains a non-empty `key:` (Gradle check task), so real keys are never committed. | — | done — unit test `AppConfigTest` fails CI if `app.yml` has a key |

### Validation

| ID | Check | Status |
|----|-------|--------|
| FND-V1 | CI on a PR shows lint, unit test, and APK steps; a deliberately failing test turns the PR red. | todo |
| FND-V2 | After upgrading from a PR #4 build, saved keys still load (migration), and `adb shell run-as com.smassistant.app cat shared_prefs/*.xml` shows no plaintext key. | todo |
| FND-V3 | With logging on, a full API Status run and a generation call print no key or token in logcat. | todo |

### Tests

| ID | Kind | Test | Status |
|----|------|------|--------|
| FND-T1 | unit | Secure store round-trip: save → load returns the key; stored bytes are not the plaintext. (Robolectric, fake cipher: Robolectric has no AndroidKeyStore) | done |
| FND-T2 | unit | Migration from old prefs copies values once and deletes the plaintext entries. | done |
| FND-T3 | unit | Redaction: a request with each auth header logs `███` instead of the value. | todo |
| FND-T4 | unit | `ApiError` mapping from HTTP codes / IOExceptions (shared by all clients). | todo |

## Release polish (P7)

| ID | Type | Task | Depends on | Status |
|----|------|------|-----------|--------|
| REL-1 | Impl | Release build: R8 on, keep rules for serialization/SnakeYAML, signing config from CI secrets, `assembleRelease` job. | FND-2 | todo |
| REL-2 | Impl | Accessibility: content descriptions on icon buttons (copy, expand, images), 48 dp touch targets, TalkBack order on chat screens. | GEN-* | todo |
| REL-3 | Impl | App icon, name, version from tag. | — | todo |
| REL-V1 | Val | Install the release APK on a clean device; walk the full flow topic → prompt → content → post. | P4 | todo |
| REL-T1 | UI | Accessibility checks enabled in Compose UI tests (`enableAccessibilityChecks()`). | FND-1 | todo |
