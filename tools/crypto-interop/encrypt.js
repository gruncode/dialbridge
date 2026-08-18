const crypto = require("crypto");
globalThis.crypto = crypto.webcrypto;              // same API the extension uses
globalThis.btoa = (s) => Buffer.from(s, "binary").toString("base64");
globalThis.atob = (s) => Buffer.from(s, "base64").toString("binary");
const C = require("../../extension/src/crypto.js");

(async () => {
  const raw = crypto.randomBytes(32);
  const key = raw.toString("base64url");
  const payload = await C.encryptNumber(key, "+15555550123");
  console.log(key);
  console.log(payload);
})();
