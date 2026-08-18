// Relay for the Firebase transport.
//
// Firebase will not accept a message from a browser extension: sending requires
// a service-account credential, and shipping one to clients would hand every
// user the ability to push to every other user. So a small server stands in
// between. This is it.
//
// What it deliberately does NOT do:
//   * decrypt anything — it has no key, and the number is ciphertext to it
//   * store anything — no database, no queue, no state between requests
//   * log anything identifying — no device tokens, no payloads, no addresses
//
// That is what keeps the operator's obligations small: the only personal data
// it touches is a device token in transit, and it forgets that the moment the
// request ends.

const admin = require("firebase-admin");

if (!admin.apps.length) {
  admin.initializeApp();
}

// Requests are tiny and fixed in shape; anything larger is not from our client.
const MAX_PAYLOAD_CHARS = 512;
const MAX_TOKEN_CHARS = 4096;

// Crude, per-instance, in-memory rate limiting. It resets when the instance
// recycles, which is fine: it exists to blunt accidental loops and casual
// abuse, not to be an authorisation system.
const RATE_WINDOW_MS = 60_000;
const RATE_MAX = 60;
const seen = new Map();

function withinRateLimit(token, now) {
  const bucket = seen.get(token);
  if (!bucket || now - bucket.start >= RATE_WINDOW_MS) {
    seen.set(token, { count: 1, start: now });
    return true;
  }
  if (bucket.count >= RATE_MAX) return false;
  bucket.count += 1;
  return true;
}

function setCors(res) {
  res.set("Access-Control-Allow-Origin", "*");
  res.set("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.set("Access-Control-Allow-Headers", "Content-Type");
}

exports.relay = async (req, res) => {
  setCors(res);

  if (req.method === "OPTIONS") return res.status(204).send("");
  if (req.method !== "POST") return res.status(405).json({ error: "method-not-allowed" });

  const body = req.body && typeof req.body === "object" ? req.body : {};
  const { deviceToken, payload } = body;

  if (typeof deviceToken !== "string" || !deviceToken || deviceToken.length > MAX_TOKEN_CHARS) {
    return res.status(400).json({ error: "invalid-device-token" });
  }

  // The payload must look like our base64url ciphertext and nothing else. This
  // is what stops the relay being repurposed into a general message pipe.
  if (
    typeof payload !== "string" ||
    payload.length === 0 ||
    payload.length > MAX_PAYLOAD_CHARS ||
    !/^[A-Za-z0-9_-]+$/.test(payload)
  ) {
    return res.status(400).json({ error: "invalid-payload" });
  }

  if (!withinRateLimit(deviceToken, Date.now())) {
    res.set("Retry-After", "60");
    return res.status(429).json({ error: "rate-limit-exceeded" });
  }

  try {
    await admin.messaging().send({
      token: deviceToken,
      data: { payload },
      // High priority is not optional: a default-priority data message is
      // deferred while the phone is dozing, and a call request that arrives
      // twenty minutes late is worse than useless.
      android: { priority: "high" }
    });
    return res.status(202).json({ ok: true });
  } catch (error) {
    // Log the failure class only. The token and payload never reach the logs.
    const code = (error && error.code) || "unknown";
    console.error("relay failed:", code);

    if (
      code === "messaging/registration-token-not-registered" ||
      code === "messaging/invalid-registration-token"
    ) {
      return res.status(410).json({ error: "expired-device-token" });
    }
    return res.status(502).json({ error: "relay-failed" });
  }
};
