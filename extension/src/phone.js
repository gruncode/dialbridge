// Phone-number recognition and normalisation.
//
// Deliberately small and dependency-free: a full parser such as libphonenumber
// is ~200 KB and would dominate the extension. The trade-off is documented in
// the README — this recognises the common written forms and converts them to
// E.164 (the +<country><number> form every dialer understands), and it errs
// towards missing an odd number rather than dialling a wrong one.
//
// Loaded as a classic script (Chrome content scripts cannot be ES modules), so
// it publishes a single global rather than using export.

var BrowserDialPhone = (function () {
  "use strict";

  // Characters people use to make numbers readable, plus the ones that survive
  // a copy/paste from a spreadsheet or a PDF.
  var SEPARATORS = /[\s().‐-― /-]/g;

  // A candidate must contain at least this many digits to be considered a
  // phone number. Nine keeps order numbers, dates and prices out of the results.
  var MIN_DIGITS = 9;

  // The upper bound comes from E.164 itself: no phone number exceeds 15 digits.
  var MAX_DIGITS = 15;

  // Matches numbers as they appear in running text: either an international
  // form (leading + or 00) or a run of digits with separators between them.
  var PATTERN =
    /(?:\+|00)\d[\d\s().‐-― /-]{7,20}\d|\b\d[\d\s().‐-― /-]{7,20}\d\b/g;

  /**
   * Convert a written phone number into E.164, or return null when the text is
   * not a number we are willing to dial.
   *
   * @param {string} raw          text as it appeared on the page
   * @param {string} countryCode  digits only, e.g. "30" for Greece; used when
   *                              the number carries no country code of its own
   * @returns {string|null}       "+301234567890", or null
   */
  function toE164(raw, countryCode) {
    if (!raw) return null;

    // Strip the decoration humans add, keeping only digits and a leading plus.
    var text = String(raw).replace(SEPARATORS, "");

    // "00" is the international prefix in most of the world and means the same
    // thing as "+", so normalise both to the same internal form.
    var international = false;
    if (text.charAt(0) === "+") {
      international = true;
      text = text.slice(1);
    } else if (text.slice(0, 2) === "00") {
      international = true;
      text = text.slice(2);
    }

    // Anything left over that is not a digit disqualifies the candidate: it was
    // an identifier or a date that merely looked numeric.
    if (!/^\d+$/.test(text)) return null;
    if (text.length < MIN_DIGITS || text.length > MAX_DIGITS) return null;

    // A number written in international form is already complete.
    if (international) return "+" + text;

    // A national number needs the user's country code. Without one configured
    // we refuse rather than guess — dialling the wrong country is worse than
    // doing nothing at all.
    var cc = String(countryCode || "").replace(/\D/g, "");
    if (!cc) return null;

    // Many countries write national numbers with a trunk prefix (a leading 0)
    // that is dropped once the country code is added.
    var national = text.replace(/^0+/, "");
    if (!national) return null;

    var combined = cc + national;
    if (combined.length > MAX_DIGITS) return null;

    return "+" + combined;
  }

  /**
   * Find every dialable number in a piece of text.
   *
   * @param {string} text
   * @param {string} countryCode
   * @returns {Array<{raw: string, e164: string, index: number, length: number}>}
   */
  function findNumbers(text, countryCode) {
    var found = [];
    if (!text) return found;

    // The pattern is global, so reset it: a regex literal keeps its lastIndex
    // between calls and would silently skip matches on the second invocation.
    PATTERN.lastIndex = 0;

    var match;
    while ((match = PATTERN.exec(text)) !== null) {
      var e164 = toE164(match[0], countryCode);
      if (e164) {
        found.push({
          raw: match[0],
          e164: e164,
          index: match.index,
          length: match[0].length
        });
      }
    }
    return found;
  }

  return { toE164: toE164, findNumbers: findNumbers, PATTERN: PATTERN };
})();

// Make the module usable from Node for the unit tests without affecting the
// browser, where `module` does not exist.
if (typeof module !== "undefined" && module.exports) {
  module.exports = BrowserDialPhone;
}
