// Service worker: the only part that talks to the network.
//
// It publishes the chosen number to an ntfy topic. The phone is subscribed to
// that same topic and opens its dialer when a message arrives. There is no
// server of ours in the middle — ntfy.sh is a public relay, and anyone who
// prefers can self-host it and point the extension at their own address.

importScripts("/src/phone.js");

// Defaults live in one place so the popup and the worker cannot disagree.
const DEFAULTS = {
  server: "https://ntfy.sh",
  topic: "",
  authToken: "",
  countryCode: "",
  markPlainText: true,
  enabled: true
};

/** Read the user's settings, filling in defaults for anything unset. */
async function getSettings() {
  return chrome.storage.sync.get(DEFAULTS);
}

/** Short-lived badge feedback, so a click always produces a visible result. */
async function badge(text, colour) {
  await chrome.action.setBadgeBackgroundColor({ color: colour });
  await chrome.action.setBadgeText({ text });
  setTimeout(() => chrome.action.setBadgeText({ text: "" }), 2500);
}

/**
 * Publish one number to the configured topic.
 *
 * Priority 5 is ntfy's highest. It matters: the Android app is woken by this
 * message, and lower priorities can be delayed while the phone is dozing.
 */
async function publish(e164) {
  const settings = await getSettings();

  if (!settings.topic) {
    await badge("SET", "#b45309");
    return { ok: false, error: "No topic configured" };
  }

  const headers = { "Content-Type": "application/json" };
  if (settings.authToken) {
    headers.Authorization = "Bearer " + settings.authToken;
  }

  const body = JSON.stringify({
    topic: settings.topic,
    message: e164,
    title: "Call",
    priority: 5,
    tags: ["telephone_receiver"]
  });

  try {
    const response = await fetch(settings.server.replace(/\/+$/, "") + "/", {
      method: "POST",
      headers,
      body
    });

    if (!response.ok) {
      await badge("ERR", "#b91c1c");
      return { ok: false, error: "Server replied " + response.status };
    }

    await badge("OK", "#15803d");
    return { ok: true };
  } catch (error) {
    // Almost always a wrong server address or no connectivity.
    await badge("ERR", "#b91c1c");
    return { ok: false, error: String(error.message || error) };
  }
}

// Numbers arrive from the content script (a click) or from the popup (a test).
chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message && message.type === "dial" && message.number) {
    publish(message.number).then(sendResponse);
    return true; // keep the channel open for the async reply
  }
  return false;
});

// A right-click entry for numbers the page renders in a way we cannot detect —
// inside a canvas, an image caption, or an unusual widget. The user selects the
// text themselves and we normalise whatever they highlighted.
chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.create({
    id: "dialbridge-selection",
    title: "Send number to my phone",
    contexts: ["selection"]
  });
});

chrome.contextMenus.onClicked.addListener(async (info) => {
  if (info.menuItemId !== "dialbridge-selection" || !info.selectionText) return;
  const settings = await getSettings();
  const e164 = DialBridgePhone.toE164(info.selectionText, settings.countryCode);
  if (e164) {
    await publish(e164);
  } else {
    await badge("?", "#b45309");
  }
});
