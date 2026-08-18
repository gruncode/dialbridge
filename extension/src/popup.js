// Settings screen. Small enough to stay plain DOM code — no framework, so the
// whole extension remains readable and reviewable without a build step.

(function () {
  "use strict";

  var DEFAULTS = {
    server: "https://ntfy.sh",
    topic: "",
    authToken: "",
    countryCode: "",
    markPlainText: true,
    enabled: true
  };

  var fields = {};
  Object.keys(DEFAULTS).forEach(function (key) {
    fields[key] = document.getElementById(key);
  });

  var status = document.getElementById("status");

  function say(text, isError) {
    status.textContent = text;
    status.style.color = isError ? "#b91c1c" : "";
  }

  /** Fill the form from stored settings. */
  function load() {
    chrome.storage.sync.get(DEFAULTS, function (stored) {
      Object.keys(DEFAULTS).forEach(function (key) {
        var element = fields[key];
        if (!element) return;
        if (element.type === "checkbox") {
          element.checked = Boolean(stored[key]);
        } else {
          element.value = stored[key] || "";
        }
      });
    });
  }

  /** Read the form back out, normalising what the user typed. */
  function collect() {
    return {
      // A trailing slash here would produce a double slash in the request URL.
      server: (fields.server.value || DEFAULTS.server).trim().replace(/\/+$/, ""),
      // ntfy topics are path segments: anything else would silently 404.
      topic: fields.topic.value.trim().replace(/[^A-Za-z0-9_-]/g, ""),
      authToken: fields.authToken.value.trim(),
      countryCode: fields.countryCode.value.replace(/\D/g, ""),
      markPlainText: fields.markPlainText.checked,
      enabled: fields.enabled.checked
    };
  }

  function save(onDone) {
    var values = collect();
    chrome.storage.sync.set(values, function () {
      // Show the cleaned-up values, so the user sees what was actually stored
      // rather than what they typed.
      fields.server.value = values.server;
      fields.topic.value = values.topic;
      fields.countryCode.value = values.countryCode;
      say("Saved.");
      if (onDone) onDone(values);
    });
  }

  document.getElementById("save").addEventListener("click", function () {
    save();
  });

  // The test sends a harmless, obviously fake number: it proves the path from
  // browser to phone without anyone's real line being dialled by accident.
  document.getElementById("test").addEventListener("click", function () {
    save(function (values) {
      if (!values.topic) {
        say("Set a topic first — the phone app shows one.", true);
        return;
      }
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

  load();
})();
