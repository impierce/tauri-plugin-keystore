import { invoke } from "@tauri-apps/api/core";

/**
 * Strings shown in the platform's authentication prompt.
 *
 * Every field is optional; the native implementations fall back to their own
 * defaults for anything left unset.
 */
export interface Prompt {
  title?: string;
  /** Android only. */
  subtitle?: string;
  /** Android: the negative button label. iOS: the cancel button label. */
  cancelLabel?: string;
  /** iOS only: the reason shown by the system authentication sheet. */
  reason?: string;
}

/**
 * Stores `value` under `key`, replacing any secret already stored there.
 *
 * `key` identifies the secret: a key alias on Android, a keychain account on
 * iOS. Requires the user to authenticate.
 */
export async function store(
  key: string,
  value: string,
  prompt?: Prompt
): Promise<void> {
  await invoke<void>("plugin:keystore|store", {
    payload: { key, value, prompt },
  });
}

/**
 * Returns the secret stored under `key`, or `null` if there is none.
 *
 * Requires the user to authenticate.
 */
export async function retrieve(
  key: string,
  prompt?: Prompt
): Promise<string | null> {
  const response = await invoke<{ value?: string | null }>(
    "plugin:keystore|retrieve",
    { payload: { key, prompt } }
  );
  return response.value ?? null;
}

/** Removes the secret stored under `key`. Succeeds if there is none. */
export async function remove(key: string): Promise<void> {
  await invoke<void>("plugin:keystore|remove", { payload: { key } });
}
