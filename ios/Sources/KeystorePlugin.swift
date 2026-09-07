import SwiftRs
import Tauri
import UIKit
import WebKit

import LocalAuthentication

class Prompt: Decodable {
  var title: String?
  var subtitle: String?
  var cancelLabel: String?
  var reason: String?
}

class StoreRequest: Decodable {
  let key: String
  let value: String
  var prompt: Prompt?
}

class RetrieveRequest: Decodable {
  let key: String
  var prompt: Prompt?
}

class RemoveRequest: Decodable {
  let key: String
}

let DEFAULT_REASON = "Authenticate to access your secret"

class KeystorePlugin: Plugin {
  @objc public func store(_ invoke: Invoke) throws {
    let args = try invoke.parseArgs(StoreRequest.self)

    guard let secretData = args.value.data(using: .utf8) else {
      throw NSError(
        domain: "StoreErrorDomain", code: -1,
        userInfo: [NSLocalizedDescriptionKey: "Invalid secret string"])
    }

    // Require user presence (biometrics or device passcode) and keep the item
    // on this device only, readable while it is unlocked.
    var error: Unmanaged<CFError>?
    guard
      let accessControl = SecAccessControlCreateWithFlags(
        nil,
        kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
        .userPresence,
        &error
      )
    else {
      throw error!.takeRetainedValue() as Error
    }

    // The caller's key is the keychain account, so each key addresses its own item.
    let query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrAccount as String: args.key,
      kSecAttrAccessControl as String: accessControl,
      kSecValueData as String: secretData,
    ]

    // Replace any item already stored under this key.
    SecItemDelete([
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrAccount as String: args.key,
    ] as CFDictionary)

    let status = SecItemAdd(query as CFDictionary, nil)
    guard status == errSecSuccess else {
      throw NSError(domain: NSOSStatusErrorDomain, code: Int(status), userInfo: nil)
    }

    invoke.resolve()
  }

  @objc public func retrieve(_ invoke: Invoke) throws {
    let args = try invoke.parseArgs(RetrieveRequest.self)

    let context = LAContext()
    context.localizedReason = args.prompt?.reason ?? DEFAULT_REASON
    if let cancelLabel = args.prompt?.cancelLabel {
      context.localizedCancelTitle = cancelLabel
    }

    let query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrAccount as String: args.key,
      kSecReturnData as String: true,
      kSecUseAuthenticationContext as String: context,
    ]

    var item: CFTypeRef?
    let status = SecItemCopyMatching(query as CFDictionary, &item)

    // Nothing stored under this key is not an error.
    if status == errSecItemNotFound {
      invoke.resolve(["value": nil])
      return
    }

    guard status == errSecSuccess else {
      throw NSError(domain: NSOSStatusErrorDomain, code: Int(status), userInfo: nil)
    }

    guard let data = item as? Data,
      let secret = String(data: data, encoding: .utf8)
    else {
      throw NSError(
        domain: "com.impierce.identity-wallet", code: -1,
        userInfo: [NSLocalizedDescriptionKey: "Unable to decode secret"])
    }

    invoke.resolve(["value": secret])
  }

  @objc public func remove(_ invoke: Invoke) throws {
    let args = try invoke.parseArgs(RemoveRequest.self)

    let query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrAccount as String: args.key,
    ]

    let status = SecItemDelete(query as CFDictionary)

    guard status == errSecSuccess || status == errSecItemNotFound else {
      throw NSError(domain: NSOSStatusErrorDomain, code: Int(status), userInfo: nil)
    }

    invoke.resolve()
  }
}

@_cdecl("init_plugin_keystore")
func initPlugin() -> Plugin {
  return KeystorePlugin()
}
