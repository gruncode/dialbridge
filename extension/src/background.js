// Service worker: the only part that touches the network.
//
// It encrypts the number, then hands it to whichever transport the phone asked
// for during pairing:
//
//   ntfy  — published straight to a topic. No server of ours involved.
//   fcm   — posted to a small relay, which forwards it through Firebase Cloud
//           Messaging. Needed because Firebase will not accept messages from a
//           browser extension: sending requires a service-account credential
//           that cannot be shipped to clients safely.
//
// Under both transports the payload is ciphertext. Neither the ntfy operator,
// nor Google, nor the relay can read the number — only the paired phone holds
// the key. That is what keeps a phone number, which is personal data, out of
// everyone else's systems.

importScripts("/src/crypto.js", "/src/phone.js");

const DEFAULTS = {
  pairing: null,        // decoded pairing object from the phone
  countryCode: "",
  markPlainText: true,
  enabled: true
};

async function getSettings() {
  return chrome.storage.sync.get(DEFAULTS);
}

/** Short-lived badge feedback, so a click always produces a visible result. */
async function badge(text, colour) {
  await chrome.action.setBadgeBackgroundColor({ color: colour });
  await chrome.action.setBadgeText({ text });
  setTimeout(() => chrome.action.setBadgeText({ text: "" }), 2500);
}

/** Publish ciphertext to an ntfy topic. */
async function sendViaNtfy(pairing, payload) {
  const headers = { "Content-Type": "application/json" };
  if (pairing.a) headers.Authorization = "Bearer " + pairing.a;

  const response = await fetch(String(pairing.s).replace(/\/+$/, "") + "/", {
    method: "POST",
    headers,
    body: JSON.stringify({
      topic: pairing.c,
      message: payload,
      // Highest ntfy priority: the phone must be woken, not merely informed.
      priority: 5
    })
  });

  if (!response.ok) throw new Error("ntfy replied " + response.status);
}

/** Hand ciphertext to the relay, which forwards it through Firebase. */
async function sendViaRelay(pairing, payload) {
  const response = await fetch(String(pairing.r).replace(/\/+$/, ""), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ deviceToken: pairing.d, payload })
  });

  if (!response.ok) throw new Error("Relay replied " + response.status);
}

/** Encrypt a number and send it by whichever route the phone chose. */
async function dispatch(e164) {
  const settings = await getSettings();
  const pairing = settings.pairing;

  if (!pairing || !pairing.k) {
    await badge("PAIR", "#b45309");
    return { ok: false, error: "Not paired with a phone yet" };
  }

  try {
    const payload = await BrowserDialCrypto.encryptNumber(pairing.k, e164);

    // Android-via-Firebase and iPhone-via-Apple both go through a relay and
    // take the same request shape, so one branch serves both.
    if (pairing.t === "fcm" || pairing.t === "apns") {
      await sendViaRelay(pairing, payload);
    } else {
      await sendViaNtfy(pairing, payload);
    }

    await badge("OK", "#15803d");
    return { ok: true };
  } catch (error) {
    await badge("ERR", "#b91c1c");
    // The message text is shown in the popup only; nothing is logged, because
    // logs of who you called are exactly what this design avoids creating.
    return { ok: false, error: String(error.message || error) };
  }
}

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message && message.type === "dial" && message.number) {
    dispatch(message.number).then(sendResponse);
    return true; // keep the channel open for the async reply
  }
  return false;
});

// A right-click entry for numbers the page renders in a way the detector cannot
// see — inside a canvas, an image caption, or an unusual widget.
chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.create({
    id: "browser-dial-selection",
    title: "Send number to my phone",
    contexts: ["selection"]
  });
});

chrome.contextMenus.onClicked.addListener(async (info) => {
  if (info.menuItemId !== "browser-dial-selection" || !info.selectionText) return;
  const settings = await getSettings();
  const e164 = BrowserDialPhone.toE164(info.selectionText, settings.countryCode);
  if (e164) {
    await dispatch(e164);
  } else {
    await badge("?", "#b45309");
  }
});
