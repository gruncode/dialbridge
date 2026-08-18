# Crypto interoperability check

The browser encrypts with Web Crypto; the phone decrypts with `javax.crypto`.
Two separate implementations have to agree on the wire format exactly, or the
app silently drops every message — so this proves they do, rather than assuming
it.

```bash
node encrypt.js                    # prints a key and a ciphertext
javac Decrypt.java
java Decrypt <key> <ciphertext>
```

Expected:

```
decrypted: +15555550123
forged message rejected: AEADBadTagException
```

The second line matters as much as the first: it confirms that flipping a
single bit makes decryption fail loudly instead of producing plausible
rubbish. That property is what lets the phone treat successful decryption as
proof the message came from the paired browser.

`Decrypt.java` mirrors `android/app/src/main/java/com/gruncode/dialbridge/Crypto.kt`.
It uses `java.util.Base64` where the app uses `android.util.Base64`, because
Android's version is unavailable outside a device — the two produce identical
base64url output.
