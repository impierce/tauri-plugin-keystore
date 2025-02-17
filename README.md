# Tauri Plugin Keystore

Interact with the device-native key storage (Android Keystore, iOS Keychain).

> [!NOTE]  
> This plugin is scoped to interact with the device-specific keystore. It assumes that biometrics are set up on the user's device and performs no preflight check before trying to interact with the secrets. You should use the [official biometric plugin](https://github.com/tauri-apps/plugins-workspace/tree/v2/plugins/biometric) to `checkStatus` before, if you want to make sure.

| Platform | Supported |
| -------- | :-------: |
| Linux    |    ❌     |
| Windows  |    ❌     |
| macOS    |    ❌     |
| Android  |    ✅     |
| iOS      |    ✅     |

## Installation

`src-tauri/Cargo.toml`

```toml
[dependencies]
tauri-plugin-keystore = "0.1.0"
```

Install the JS guest bindings:

```shell
pnpm add @impierce/tauri-plugin-keystore
```

## Requirements

- This plugin requires a **Rust version of 1.77.2 or higher**.
- The minimum supported Tauri version is **2.0.0**.
- For Android, only Android 9 (**API level 28**) and higher are supported.

## Example

First you need to register the plugin with your Tauri app:

`src-tauri/src/lib.rs`

```rust
fn main() {
    tauri::Builder::default()
        .plugin(tauri_plugin_keystore::init())
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
```

Afterwards all the plugin's APIs are available through the JavaScript guest bindings:

```typescript
import { remove, retrieve, store } from "@impierce/tauri-plugin-keystore";

await store("secr3tPa$$w0rd");
const password = await retrieve();
await remove();
```

The provided functions will fail if the device has no biometrics set up, so you should check the biometric status with `plugin-biometric` _(official)_ before using them:

```typescript
import { checkStatus, type Status } from "@tauri-apps/plugin-biometric";

const biometricsStatus: Status = await checkStatus();
assert(biometricsStatus.biometryType !== BiometryType.None);
```
