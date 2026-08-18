// Tests for the number recogniser. Run with: node extension/test/phone.test.js
//
// No test framework on purpose: the extension has no build step, and a single
// dependency-free file keeps it that way. Node's own assert is enough.

const assert = require("assert");
const phone = require("../src/phone.js");

const GR = "30"; // Greece, used as the "user's country" in these cases

const cases = [
  // [input, country, expected]
  ["+30 690 000 0000", GR, "+306900000000"],
  ["+30-690-000-0000", GR, "+306900000000"],
  ["0030 690 000 0000", GR, "+306900000000"],
  ["6900000000", GR, "+306900000000"],       // national mobile
  ["210 7654321", GR, "+302107654321"],      // national landline
  ["(210) 765-4321", GR, "+302107654321"],
  ["+44 20 7946 0958", GR, "+442079460958"], // foreign number, country ignored
  ["+1 555 555 0123", "", "+15555550123"],   // international needs no country

  // Rejections — each of these has bitten a naive implementation somewhere.
  ["12345", GR, null],                       // too short to be a phone number
  ["2024", GR, null],                        // a year
  ["1234567890123456789", GR, null],         // longer than E.164 allows
  ["6900000000", "", null],                  // national, but no country set
  ["abc6900000000", GR, null],               // letters mixed in
  ["", GR, null],
  [null, GR, null]
];

let failures = 0;

for (const [input, country, expected] of cases) {
  const actual = phone.toE164(input, country);
  try {
    assert.strictEqual(actual, expected);
    console.log(`  ok   ${JSON.stringify(input)} (${country || "no country"}) -> ${actual}`);
  } catch (error) {
    failures += 1;
    console.error(
      `  FAIL ${JSON.stringify(input)} (${country || "no country"}) -> ${actual}, expected ${expected}`
    );
  }
}

// Finding several numbers inside a sentence is the content script's main job,
// so it gets a case of its own.
const sentence = "Call 210 7654321 or +30 690 000 0000, ref 2024 order 12345.";
const found = phone.findNumbers(sentence, GR).map((hit) => hit.e164);
try {
  assert.deepStrictEqual(found, ["+302107654321", "+306900000000"]);
  console.log(`  ok   found ${found.length} numbers in a sentence, ignored the noise`);
} catch (error) {
  failures += 1;
  console.error(`  FAIL sentence scan produced ${JSON.stringify(found)}`);
}

if (failures > 0) {
  console.error(`\n${failures} test(s) failed`);
  process.exit(1);
}
console.log("\nAll tests passed.");
