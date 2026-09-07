use serde::{Deserialize, Serialize};

/// Strings shown in the platform's authentication prompt.
///
/// Every field is optional; the native implementations fall back to their own
/// defaults for anything left unset.
#[derive(Debug, Clone, Default, Deserialize, Serialize)]
#[serde(rename_all = "camelCase", default)]
pub struct Prompt {
    pub title: Option<String>,
    pub subtitle: Option<String>,
    /// Android: the negative button label. iOS: the cancel button label.
    pub cancel_label: Option<String>,
    /// iOS only: the reason shown by the system authentication sheet.
    pub reason: Option<String>,
}

#[derive(Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct StoreRequest {
    /// Identifies the secret: a key alias on Android, a keychain account on iOS.
    pub key: String,
    pub value: String,
    #[serde(default)]
    pub prompt: Option<Prompt>,
}

#[derive(Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RetrieveRequest {
    pub key: String,
    #[serde(default)]
    pub prompt: Option<Prompt>,
}

/// `value` is `None` when no secret is stored under the requested key.
#[derive(Debug, Clone, Default, Deserialize, Serialize)]
#[serde(rename_all = "camelCase", default)]
pub struct RetrieveResponse {
    pub value: Option<String>,
}

#[derive(Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RemoveRequest {
    pub key: String,
}
