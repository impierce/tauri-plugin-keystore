# Tauri Plugin Keystore

[![semantic-release: angular](https://img.shields.io/badge/semantic--release-angular-e10079?logo=semantic-release)](https://github.com/semantic-release/semantic-release)
[![Crates.io Version](https://img.shields.io/crates/v/tauri-plugin-keystore)](https://crates.io/crates/tauri-plugin-keystore)
[![NPM Version](https://img.shields.io/npm/v/%40impierce%2Ftauri-plugin-keystore)](https://www.npmjs.com/package/@impierce/tauri-plugin-keystore)

---

Interact with the device-native key storage (Android Keystore, iOS Keychain).

> [!NOTE]  
> This plugin is scoped to interact with the device-specific keystore. It assumes that biometrics are set up on the user's device and performs no preflight check before trying to interact with the keystore. You should use the official [biometric plugin](https://github.com/tauri-apps/plugins-workspace/tree/v2/plugins/biometric) to `checkStatus` before, if you want to make sure (see [Usage](#usage) below).

| Platform | Supported |
| -------- | :-------: |
| Linux    |    ❌     |
| Windows  |    ❌     |
| macOS    |    ❌     |
| Android  |    ✅     |
| iOS      |    ✅     |

## Installation

Add the following to your `src-tauri/Cargo.toml`:

```toml
[dependencies]
tauri-plugin-keystore = "2"
```

Then install the JS guest bindings:

```shell
pnpm add @impierce/tauri-plugin-keystore
```

_This also works for `npm` and `yarn`._

## Requirements

- This plugin requires a **Rust version of 1.77.2 or higher**.
- The minimum supported Tauri version is **2.0.0**.
- Android 9 (**API level 28**) and higher are supported.

## Usage

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

The provided functions will fail if the device has no biometrics set up, so you should check the biometric status with the official `tauri-plugin-biometric` before using them:

```typescript
import { checkStatus, type Status } from "@tauri-apps/plugin-biometric";

const biometricsStatus: Status = await checkStatus();
assert(biometricsStatus.biometryType !== BiometryType.None);
```

## Release strategy

This plugin is semantically versioned, but there are some caveats:

- Versioning starts at `v2.0.0` to match the Tauri version it supports _(common practice for Tauri plugins)_.
- Breaking changes should be avoided as they would make the plugin go out-of-sync with the Tauri major version.

#### Conflicting release approaches

The next version is determined by `semantic-release` which does [not recommend making commits during the release process](https://semantic-release.gitbook.io/semantic-release/support/faq#making-commits-during-the-release-process-adds-significant-complexity).
When publishing a new npm package to **npmjs.com** this is circumvented by `semantic-release` by pushing the version in the release metadata, so the `version` field in `package.json` is ignored.
However, **crates.io** uses the `version` field from `Cargo.toml` to determine the version. There seems to be no easy way to replace the version number during the publish to **crates.io**.

#### Solution

This repository provides a workaround for this issue by using a **semi-automated** release process which decouples building a new release from pushing it to the package registries:

1. Run the **release --dry-run** Action to let `semantic-release` determine the next version. Take note of the version it produces.

2. Manually update the `version` fields in the `Cargo.toml` and `package.json` files (omit the `"v"`, so just `2.1.0` instead of `v2.1.0`).

3. Commit the changes using the following commit message _(replace the version)_:

```
build: release version v1.1.0-alpha.1
```

4. Open a pull request with the same title as the commit message.

5. After the PR is merged, the actual **release** Action will run which creates a new release on GitHub and adds a git tag.

6. The **publish** Action then publishes the packages to npm and crates.io.

<!-- TODO: possibly `semantic-release-cargo` can solve this problem: https://crates.io/crates/semantic-release-cargo -->
