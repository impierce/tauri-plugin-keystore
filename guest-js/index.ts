import { invoke } from "@tauri-apps/api/core";

export interface StoreRequest {
  keyAlias: string;
  value: string;
  promptTitle: string;
  promptSubtitle: string;
  promptNegativeButtonText: string;
}

export async function store(args: StoreRequest): Promise<void> {
  return await invoke<void>("plugin:keystore|store", {
    payload: {
      args,
    },
  });
}

export async function retrieve(
  service: string,
  user: string,
  keyAlias: string
): Promise<string | null> {
  return await invoke<{ value?: string }>("plugin:keystore|retrieve", {
    payload: {
      service,
      user,
      keyAlias,
    },
  }).then((r) => (r.value ? r.value : null));
}

export async function remove(keyAlias: string): Promise<void> {
  return await invoke<void>("plugin:keystore|remove", {
    payload: {
      keyAlias,
    },
  });
}
