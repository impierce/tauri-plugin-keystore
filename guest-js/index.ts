import { invoke } from "@tauri-apps/api/core";

export async function store(value: string): Promise<void> {
  return await invoke<void>("plugin:keystore|store", {
    payload: {
      value,
    },
  });
}

export async function retrieve(
  service: string,
  user: string
): Promise<string | null> {
  return await invoke<{ value?: string }>("plugin:keystore|retrieve", {
    payload: {
      service,
      user,
    },
  }).then((r) => (r.value ? r.value : null));
}

export async function remove(service: string, user: string) {
  return await invoke<void>("plugin:keystore|remove", {
    payload: {
      service,
      user,
    },
  });
}
