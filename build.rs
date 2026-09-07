const COMMANDS: &[&str] = &["remove", "retrieve", "store"];

fn main() {
    let result = tauri_plugin::Builder::new(COMMANDS)
        .android_path("android")
        .ios_path("ios")
        .try_build();

    // docs.rs builds this crate for an Android target (see `Cargo.toml`), where
    // the plugin build always fails and is irrelevant to the generated docs.
    if !(cfg!(docsrs) && std::env::var("TARGET").unwrap().contains("android")) {
        result.unwrap();
    }
}
