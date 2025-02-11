use tauri::{command, AppHandle, Runtime};

use crate::models::*;
use crate::KeystoreExt;
use crate::Result;

#[command]
pub(crate) async fn ping<R: Runtime>(
    app: AppHandle<R>,
    payload: PingRequest,
) -> Result<PingResponse> {
    app.keystore().ping(payload)
}

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

// #[command]
// retr, store, del
