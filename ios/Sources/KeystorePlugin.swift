import SwiftRs
import Tauri
import UIKit
import WebKit

import LocalAuthentication

class StoreRequest: Decodable {
  let value: String
}

class KeystorePlugin: Plugin {
  @objc public func store(_ invoke: Invoke) throws {
    let args = try invoke.parseArgs(StoreRequest.self)
      
    guard let secretData = args.value.data(using: .utf8) else {
        throw NSError(domain: "StoreErrorDomain", code: -1, userInfo: [NSLocalizedDescriptionKey: "Invalid secret string"])
    }

    // Create an access control object that requires user presence (biometrics or device passcode)
    // and makes the item accessible only when the device is unlocked.
    var error: Unmanaged<CFError>?
    guard let accessControl = SecAccessControlCreateWithFlags(
        nil,
        kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
        .userPresence,
        &error
    ) else {
        throw error!.takeRetainedValue() as Error
    }

    // Build the keychain query. The account attribute here is used as the key to store/retrieve the secret.
    let account = "com.impierce.identity-wallet.unime-dev"
    let query: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrAccount as String: account,
        kSecAttrAccessControl as String: accessControl,
        kSecValueData as String: secretData
    ]

    // Delete any existing item with this account.
    SecItemDelete(query as CFDictionary)
    
    // Add the new item to the keychain.
    let status = SecItemAdd(query as CFDictionary, nil)
    guard status == errSecSuccess else {
        // throw KeychainError(status: status)
        throw NSError(domain: NSOSStatusErrorDomain, code: Int(status), userInfo: nil)
    }

    invoke.resolve()
  }

  @objc public func retrieve(_ invoke: Invoke) throws {
      let account = "com.impierce.identity-wallet.unime-dev"
      let context = LAContext()
      context.localizedReason = "Access your UniMe password"

      let query: [String: Any] = [
          kSecClass as String: kSecClassGenericPassword,
          kSecAttrAccount as String: account,
          kSecReturnData as String: true,
          kSecUseAuthenticationContext as String: context,
          // kSecUseOperationPrompt as String: "Authenticate to retrieve your secret"
      ]

      var item: CFTypeRef?
      let status = SecItemCopyMatching(query as CFDictionary, &item)

      // Check the result of the query.
      guard status == errSecSuccess else {
          throw NSError(domain: NSOSStatusErrorDomain, code: Int(status), userInfo: nil)
      }

      // Convert the returned data into a String.
      guard let data = item as? Data,
            let secret = String(data: data, encoding: .utf8) else {
          throw NSError(domain: "com.impierce.identity-wallet", code: -1, userInfo: [NSLocalizedDescriptionKey: "Unable to decode secret"])
      }

      invoke.resolve(["value": secret])
  }

  @objc public func remove(_ invoke: Invoke) throws {
      let account = "com.impierce.identity-wallet.unime-dev"
      
      let query: [String: Any] = [
          kSecClass as String: kSecClassGenericPassword,
          kSecAttrAccount as String: account
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
