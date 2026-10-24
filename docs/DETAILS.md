# PyTogether — Code Editor နဲ့ Python သင်ကြားရေး

Standalone Android app. Package `com.zynelabs.pytogether`, version `0.4.0-codegen` (versionCode 4). Separate from FlowMint and other apps; no shared app data.

## Phone-first navigation

Home has two main choices: **Code Editor** opens the saved free-practice draft; **သင်ကြားရေး / Learn Python** opens the six-lesson chooser. Resume reopens the selected lesson/draft. Settings is a secondary Home entry. Back from practice returns Home.

The free editor focuses on code above output. The Helper is opened only when asked, not shown as a large permanent explanation panel. Learning mode keeps the existing lesson guidance and optional data-flow snapshots. Portrait and keyboard resizing are retained. Drafts and input values are saved separately for each lesson and free practice.

## Settings

- Offline / Online, default **Offline**. Core Python and the Burmese rule-based Helper work offline. Online permits explicit package installation and AI requests; selecting Online alone does not send code.
- Auto-run on/off. Manual mode exposes Run. Delay options: 350, 650, 1000, 1500 ms.
- Code/output font: 12, 14, 16, 18, 20 sp.
- Local Helper and learning data-flow preferences.
- Separate Python libraries and AI provider/key dialogs.
- Reset settings requires confirmation, restores Offline/default behavior, and does not delete drafts, input, provider/key configuration, or installed libraries.

Offline gates the app's package installer and AI feature. It is **not an OS firewall**: trusted Python code and imported libraries run in the app process and can use its Internet permission. A request already started cannot be unsent by changing modes.

## Ask Helper → review → accept

Ask Helper uses the selected connection mode. Offline gives Burmese explanations and conservative syntax-checked fixes using local rules. Online asks the configured OpenAI-compatible HTTPS chat-completions endpoint only after showing the exact code and a Send confirmation. Provider retention and charges may apply.

AI advice never silently changes code. Structured AI fixes are offered for review; the app locally checks syntax, shows original and proposed code, and applies only on acceptance. Revision, lesson and exact-original checks prevent stale suggestions from overwriting later edits. A guarded Undo is available through Helper while the accepted result remains unedited. AI suggestions can be wrong; syntax checks do not prove correctness.

Provider endpoint, model and optional owner-supplied API key are configured inside the app. Keys are encrypted using AES-GCM with AndroidKeyStore, never bundled in the APK, never shown again, and not placed in logs. Switching endpoint origin/path clears key reuse. The key input is masked and excluded from saved view state/autofill. There is no supplied provider subscription or embedded paid credential.

This is **not a hardened code sandbox**: Python and installed packages run with this app's privileges. Do not run unknown code or libraries, especially in an app configured with a provider key. Encryption at rest does not make credentials immune to malicious same-app code.

## Python libraries

The app bundles pip 24.3.1 and requests name-only installs from HTTPS PyPI into app-private `files/site-packages`, serialized with Python execution. Only universal pure-Python binary wheels are requested; source builds and native/platform-specific wheels are not supported. This is not an arbitrary Android pip environment. Incompatible packages report failure rather than pretend to install. Already installed compatible libraries can be imported offline. Android runtime compatibility evidence is recorded under `tests/settings/`; consult the final validation summary for actual tested packages.

## Files

Files opens a separate Android document-picker flow to import or export Python source. Saving exports a snapshot captured before the picker opens; draft autosave is separate. Import is limited to 64 KiB valid UTF-8, shows a trust warning and exact preview, and replaces only the free draft after confirmation. Import turns Auto-run off before loading the new code. The previous free code/input is retained for restoration from Files. No storage permission is requested.

## Runtime limits

Bundled CPython 3.11 through Chaquopy 17.1.0. Android 7.0+; ARM64 phones and x86_64 emulators. No 32-bit ARM build. The educational engine limits ordinary traced code to 1.5 seconds / 18,000 events, output to 12,000 characters and data-flow snapshots to 100. These are teaching safeguards, not malicious-code containment. Blocking native calls and memory exhaustion are not fully controlled. Background practice execution is unsupported.

## Build and validation

JDK 17, Android SDK/build-tools 34, Gradle 8.7 and Python 3.11 are required. `app/build.gradle` retains the exact original debug signing certificate so this review build updates version 0.2.0 without uninstalling. Preserve that private keystore; do not include it in source distributions. This is a debug-signed review APK, not a production release.

```sh
./gradlew --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest
python3 -m unittest discover -s tests -v
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.zynelabs.pytogether.test/com.zynelabs.pytogether.SettingsInstrumentation
adb shell am instrument -w com.zynelabs.pytogether.test/com.zynelabs.pytogether.SmokeInstrumentation
adb shell am instrument -w com.zynelabs.pytogether.test/com.zynelabs.pytogether.NetworkToolsInstrumentation
```

Do not clear app data to test an update. The update test compares preference data before/after an in-place installation. Emulator coverage does not equal physical ARM64 phone coverage. No actual owner's AI provider/key was supplied, so a paid live AI round trip must not be claimed as verified.


## AI Code Generator (0.4.0)

- Home မှာ **AI Code Generator** ကဒ် အသစ်။ ရေးချင်တာကို မြန်မာလို ရိုက်ပါ။
- **Offline** (default): အသင့်ပါတဲ့ recipe တွေ (`code_generator.py`) က အဆင့်လိုက် ရှင်းပြချက်နဲ့ code ပေးတယ်။ မကိုက်ရင် မကိုက်ဘူးလို့ ရိုးရိုးပြောတယ်။ Code Editor ထဲ မထည့်ခင် Review dialog မှာ အဟောင်းနဲ့ အသစ် နှိုင်းယှဉ်ပြပြီး ဟောင်းကို ပြန်ယူလို့ရတယ်။ Auto-run မလုပ်ဘူး။
- **Online**: Settings မှာ Online ဖွင့်ပြီး OpenCode Zen / OpenAI / OpenRouter / Gemini / DeepSeek / Groq / Custom ထဲက ရွေးပြီး ကိုယ်ပိုင် key ထည့်ရင် AI က ရေးပေးတယ်၊ မှားရင် ပြန်ထောက်ပြပြီး ပြင်ပေးတယ် (ask-helper → review → fix flow)။ Key တွေကို provider တစ်ခုချင်း သီးခြား encrypt လုပ်ပြီး သိမ်းတယ်။
- Tests: `python3 -m unittest discover -s tests`, emulator `CodeGeneratorInstrumentation` (`tests/codegen-android-results.txt`).
