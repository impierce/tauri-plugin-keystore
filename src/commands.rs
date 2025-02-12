use tauri::{command, AppHandle, Runtime};

use crate::models::*;
use crate::KeystoreExt;

#[command]
pub(crate) async fn store<R: Runtime>(
    app: AppHandle<R>,
    payload: StoreRequest,
) -> crate::Result<()> {
    app.keystore().store(payload)
}

#[command]
pub(crate) async fn retrieve<R: Runtime>(
    app: AppHandle<R>,
    payload: RetrieveRequest,
) -> crate::Result<RetrieveResponse> {
    app.keystore().retrieve(payload)
}

#[command]
pub(crate) async fn remove<R: Runtime>(
    app: AppHandle<R>,
    payload: RemoveRequest,
) -> crate::Result<()> {
    app.keystore().remove(payload)
}
