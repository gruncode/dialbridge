// Runs on every page. Two jobs:
//
//   1. Intercept clicks on tel: links so they go to the phone instead of
//      whatever the desktop would otherwise launch.
//   2. Optionally mark up phone numbers that appear as plain text, so they
//      become clickable too.
//
// Everything here is defensive: a content script shares the page with code it
// does not control, so it must never throw into the page's own handlers and
// never rewrite anything the user is editing.

(function () {
  "use strict";

  var MARK_CLASS = "dialbridge-number";

  // Populated from storage before any marking happens.
  var settings = { countryCode: "", markPlainText: true, enabled: true };

  // Elements whose text must never be rewritten: either it is code, or it is
  // something the user is typing into, or it is already a link.
  var SKIP_TAGS = {
    A: true, SCRIPT: true, STYLE: true, NOSCRIPT: true, TEXTAREA: true,
    INPUT: true, SELECT: true, OPTION: true, CODE: true, PRE: true,
    IFRAME: true, CANVAS: true, SVG: true
  };

  /** Ask the background worker to send a number to the phone. */
  function send(e164) {
    chrome.runtime.sendMessage({ type: "dial", number: e164 }, function () {
      // Reading lastError suppresses Chrome's "unchecked runtime.lastError"
      // console noise when the worker is asleep; the message still arrives.
      void chrome.runtime.lastError;
    });
  }

  /**
   * Capture-phase click handler.
   *
   * Capture phase matters: by the time a click reaches the page's own handlers
   * the browser may already be navigating to the tel: URL, which on a desktop
   * pops up an "open with which application?" dialog. Intercepting early lets
   * us stop that cleanly.
   */
  function onClick(event) {
    if (!settings.enabled) return;

    var target = event.target;
    if (!target || !target.closest) return;

    // Case 1: a number we marked up ourselves.
    var marked = target.closest("." + MARK_CLASS);
    if (marked && marked.dataset && marked.dataset.dialbridgeNumber) {
      event.preventDefault();
      event.stopPropagation();
      send(marked.dataset.dialbridgeNumber);
      flash(marked);
      return;
    }

    // Case 2: an ordinary tel: link placed by the site.
    var link = target.closest('a[href^="tel:"]');
    if (link) {
      var raw = decodeURIComponent(link.getAttribute("href").slice(4));
      var e164 = DialBridgePhone.toE164(raw, settings.countryCode);
      if (e164) {
        event.preventDefault();
        event.stopPropagation();
        send(e164);
        flash(link);
      }
    }
  }

  /** Brief visual acknowledgement, so a click never feels ignored. */
  function flash(element) {
    element.classList.add("dialbridge-sent");
    setTimeout(function () {
      element.classList.remove("dialbridge-sent");
    }, 900);
  }

  /**
   * Wrap plain-text phone numbers in clickable spans.
   *
   * Works on text nodes only and rebuilds each one in a fragment, so the page's
   * own elements and event listeners are left untouched.
   */
  function markTextNode(node) {
    var text = node.nodeValue;
    if (!text || text.length < 9) return;

    var hits = DialBridgePhone.findNumbers(text, settings.countryCode);
    if (!hits.length) return;

    var fragment = document.createDocumentFragment();
    var cursor = 0;

    hits.forEach(function (hit) {
      if (hit.index > cursor) {
        fragment.appendChild(
          document.createTextNode(text.slice(cursor, hit.index))
        );
      }
      var span = document.createElement("span");
      span.className = MARK_CLASS;
      span.dataset.dialbridgeNumber = hit.e164;
      span.title = "Send " + hit.e164 + " to your phone";
      span.textContent = hit.raw;
      fragment.appendChild(span);
      cursor = hit.index + hit.length;
    });

    if (cursor < text.length) {
      fragment.appendChild(document.createTextNode(text.slice(cursor)));
    }
    node.parentNode.replaceChild(fragment, node);
  }

  /** Walk a subtree and mark every eligible text node inside it. */
  function scan(root) {
    if (!settings.enabled || !settings.markPlainText) return;
    if (!settings.countryCode) return; // nothing to anchor national numbers to

    var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
      acceptNode: function (node) {
        var parent = node.parentNode;
        if (!parent || SKIP_TAGS[parent.nodeName]) return NodeFilter.FILTER_REJECT;
        if (parent.isContentEditable) return NodeFilter.FILTER_REJECT;
        if (parent.classList && parent.classList.contains(MARK_CLASS)) {
          return NodeFilter.FILTER_REJECT;
        }
        return NodeFilter.FILTER_ACCEPT;
      }
    });

    // Collect first, then modify: replacing nodes while walking invalidates
    // the walker's position.
    var pending = [];
    var current;
    while ((current = walker.nextNode())) pending.push(current);
    pending.forEach(markTextNode);
  }

  /** Re-scan content that arrives after load, throttled to stay cheap. */
  function watchForNewContent() {
    var queued = false;
    var observer = new MutationObserver(function (records) {
      if (queued) return;
      queued = true;
      setTimeout(function () {
        queued = false;
        records.forEach(function (record) {
          record.addedNodes.forEach(function (node) {
            if (node.nodeType === Node.ELEMENT_NODE) scan(node);
          });
        });
      }, 400);
    });
    observer.observe(document.body, { childList: true, subtree: true });
  }

  // Click handling is installed immediately; marking waits for settings, since
  // national numbers cannot be normalised without a country code.
  document.addEventListener("click", onClick, true);

  chrome.storage.sync.get(
    { countryCode: "", markPlainText: true, enabled: true },
    function (stored) {
      settings = stored;
      if (document.body) {
        scan(document.body);
        watchForNewContent();
      }
    }
  );

  // Keep every open tab consistent when the options change.
  chrome.storage.onChanged.addListener(function (changes, area) {
    if (area !== "sync") return;
    Object.keys(changes).forEach(function (key) {
      settings[key] = changes[key].newValue;
    });
  });
})();
