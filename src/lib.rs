#![cfg(mobile)]

use tauri::{
    plugin::{Builder, TauriPlugin},
    Manager, Runtime,
};

pub use models::*;

mod mobile;

mod commands;
mod error;
mod models;

pub use error::{Error, Result};

use mobile::Keystore;

/// Extensions to [`tauri::App`], [`tauri::AppHandle`] and [`tauri::Window`] to access the keystore APIs.
pub trait KeystoreExt<R: Runtime> {
    fn keystore(&self) -> &Keystore<R>;
}

impl<R: Runtime, T: Manager<R>> crate::KeystoreExt<R> for T {
    fn keystore(&self) -> &Keystore<R> {
        self.state::<Keystore<R>>().inner()
    }
}

/// Initializes the plugin.
pub fn init<R: Runtime>() -> TauriPlugin<R> {
    Builder::new("keystore")
        .invoke_handler(tauri::generate_handler![
            commands::remove,
            commands::retrieve,
            commands::store
        ])
        .setup(|app, api| {
            let keystore = mobile::init(app, api)?;
            app.manage(keystore);
            Ok(())
        })
        .build()
}
