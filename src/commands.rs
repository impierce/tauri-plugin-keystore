use tauri::{command, AppHandle, Runtime};

use crate::models::*;
use crate::KeystoreExt;
use crate::Result;

#[command]
pub(crate) async fn store<R: Runtime>(
    app: AppHandle<R>,
    payload: StoreRequest,
) -> Result<StoreResponse> {
    app.keystore().store(payload)
}

#[command]
pub(crate) async fn retrieve<R: Runtime>(
    app: AppHandle<R>,
    payload: RetrieveRequest,
) -> Result<RetrieveResponse> {
    app.keystore().retrieve(payload)
}

#[command]
pub(crate) async fn remove<R: Runtime>(
    app: AppHandle<R>,
    payload: RemoveRequest,
) -> Result<RemoveResponse> {
    app.keystore().remove(payload)
}
