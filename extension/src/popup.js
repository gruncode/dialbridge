// Settings screen. Plain DOM code — the extension has no build step, so what
// is reviewed here is exactly what runs.

(function () {
  "use strict";

  var DEFAULTS = {
    pairing: null,
    countryCode: "",
    markPlainText: true,
    enabled: true
  };

  var pairingField = document.getElementById("pairing");
  var pairedLine = document.getElementById("paired");
  var countryField = document.getElementById("countryCode");
  var markField = document.getElementById("markPlainText");
  var enabledField = document.getElementById("enabled");
  var status = document.getElementById("status");

  function say(text, isError) {
    status.textContent = text;
    status.style.color = isError ? "#b91c1c" : "";
  }

  /** Describe the current pairing without ever showing the key itself. */
  function describe(pairing) {
    if (!pairing) return "Not paired.";
    if (pairing.t === "fcm") {
      return "Paired over Firebase, via " + hostOf(pairing.r) + ". Encrypted.";
    }
    return "Paired over ntfy, via " + hostOf(pairing.s) + ". Encrypted.";
  }

  function hostOf(url) {
    try {
      return new URL(url).host;
    } catch (error) {
      return "an unknown server";
    }
  }

  function load() {
    chrome.storage.sync.get(DEFAULTS, function (stored) {
      countryField.value = stored.countryCode || "";
      markField.checked = Boolean(stored.markPlainText);
      enabledField.checked = Boolean(stored.enabled);
      pairedLine.textContent = describe(stored.pairing);
      // The pairing box stays empty on purpose: the stored code contains the
      // key, and there is no reason to put it back on screen.
      pairingField.value = "";
    });
  }

  function save(onDone) {
    var values = {
      countryCode: countryField.value.replace(/\D/g, ""),
      markPlainText: markField.checked,
      enabled: enabledField.checked
    };

    var typed = pairingField.value.trim();
    if (typed) {
      try {
        values.pairing = DialBridgeCrypto.decodePairing(typed);
      } catch (error) {
        say(error.message, true);
        return;
      }
    }

    chrome.storage.sync.set(values, function () {
      countryField.value = values.countryCode;
      if (values.pairing) {
        pairingField.value = "";
        pairedLine.textContent = describe(values.pairing);
      }
      say("Saved.");
      if (onDone) onDone();
    });
  }

  document.getElementById("save").addEventListener("click", function () {
    save();
  });

  // The test sends an obviously fake number, so the path from browser to phone
  // can be proved without anyone's real line being dialled by accident.
  document.getElementById("test").addEventListener("click", function () {
    save(function () {
      say("Sending…");
      chrome.runtime.sendMessage(
        { type: "dial", number: "+15555550123" },
        function (result) {
          if (chrome.runtime.lastError) {
            say(chrome.runtime.lastError.message, true);
          } else if (result && result.ok) {
            say("Sent. Your phone should show a notification.");
          } else {
            say((result && result.error) || "Failed to send.", true);
          }
        }
      );
    });
  });

  // Erasure, on the browser side: removes the delivery address and the key, so
  // this computer can no longer reach the phone and holds nothing about it.
  document.getElementById("forget").addEventListener("click", function () {
    chrome.storage.sync.remove("pairing", function () {
      pairedLine.textContent = describe(null);
      say("Pairing deleted from this browser.");
    });
  });

  load();
})();
