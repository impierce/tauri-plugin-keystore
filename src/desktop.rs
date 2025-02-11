use serde::de::DeserializeOwned;
use tauri::{plugin::PluginApi, AppHandle, Runtime};

use crate::models::*;

pub fn init<R: Runtime, C: DeserializeOwned>(
    app: &AppHandle<R>,
    _api: PluginApi<R, C>,
) -> crate::Result<Keystore<R>> {
    Ok(Keystore(app.clone()))
}

/// Access to the keystore APIs.
pub struct Keystore<R: Runtime>(AppHandle<R>);

impl<R: Runtime> Keystore<R> {
    pub fn ping(&self, payload: PingRequest) -> crate::Result<PingResponse> {
        Ok(PingResponse {
            value: Some(format!("{:?}-desktop", payload.value)),
        })
    }

    pub fn store(&self, payload: StoreRequest) -> crate::Result<StoreResponse> {
        let entry = keyring::Entry::new("unime-dev", "tester").unwrap();
        entry.set_password(&payload.value).unwrap();
        Ok(StoreResponse {})
    }

    pub fn retrieve(&self, payload: RetrieveRequest) -> crate::Result<RetrieveResponse> {
        let entry = keyring::Entry::new(&payload.service, &payload.user).unwrap();
        let password = entry.get_password().unwrap();
        Ok(RetrieveResponse {
            value: Some(password),
        })
    }
}
