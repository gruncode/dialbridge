// End-to-end encryption of the dialled number.
//
// Why this exists: whichever transport carries the message — a public ntfy
// server, or Firebase Cloud Messaging with a relay in front of it — somebody
// else's computer handles the packet on its way to the phone. A phone number
// is personal data, so the honest engineering answer is that the intermediaries
// must not be able to read it.
//
// The browser and the phone share a 256-bit key, exchanged once during pairing
// and never sent anywhere afterwards. Everything in between sees ciphertext.
//
// AES-GCM is used because both sides have it natively: Web Crypto in the
// browser, javax.crypto on Android, with identical wire format (the
// authentication tag is appended to the ciphertext by both implementations).

const DialBridgeCrypto = (function () {
  "use strict";

  const IV_BYTES = 12; // 96 bits, the size AES-GCM is designed around

  /** base64url without padding — safe inside a URL, a QR code or a text field. */
  function toBase64Url(bytes) {
    let binary = "";
    bytes.forEach((b) => (binary += String.fromCharCode(b)));
    return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  }

  function fromBase64Url(text) {
    const padded = text.replace(/-/g, "+").replace(/_/g, "/");
    const binary = atob(padded + "===".slice((padded.length + 3) % 4));
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    return bytes;
  }

  /**
   * Decode a pairing string produced by the phone.
   *
   * One opaque blob rather than three separate fields, because pairing is the
   * step where users give up: asking someone to copy a server, a topic and a
   * key correctly is asking for support requests.
   */
  function decodePairing(text) {
    const trimmed = String(text || "").trim().replace(/^dialbridge:/i, "");
    if (!trimmed) throw new Error("Empty pairing code");

    let parsed;
    try {
      parsed = JSON.parse(new TextDecoder().decode(fromBase64Url(trimmed)));
    } catch (error) {
      throw new Error("That does not look like a pairing code");
    }

    if (parsed.v !== 1) throw new Error("Pairing code from a different version");
    if (!parsed.k) throw new Error("Pairing code carries no key");
    if (parsed.t !== "ntfy" && parsed.t !== "fcm") {
      throw new Error("Unknown transport in pairing code");
    }
    return parsed;
  }

  /** Import the raw key from a pairing code into a Web Crypto key object. */
  async function importKey(base64UrlKey) {
    return crypto.subtle.importKey(
      "raw",
      fromBase64Url(base64UrlKey),
      { name: "AES-GCM" },
      false,          // not extractable: it cannot be read back out again
      ["encrypt"]     // this side only ever encrypts
    );
  }

  /**
   * Encrypt one phone number.
   *
   * Returns base64url(iv || ciphertext || tag) — a single string the transports
   * can carry as an opaque message body.
   */
  async function encryptNumber(base64UrlKey, number) {
    const key = await importKey(base64UrlKey);
    const iv = crypto.getRandomValues(new Uint8Array(IV_BYTES));

    const ciphertext = new Uint8Array(
      await crypto.subtle.encrypt(
        { name: "AES-GCM", iv, tagLength: 128 },
        key,
        new TextEncoder().encode(number)
      )
    );

    const packet = new Uint8Array(iv.length + ciphertext.length);
    packet.set(iv, 0);
    packet.set(ciphertext, iv.length);
    return toBase64Url(packet);
  }

  return { decodePairing, encryptNumber, toBase64Url, fromBase64Url };
})();

if (typeof module !== "undefined" && module.exports) {
  module.exports = DialBridgeCrypto;
}
