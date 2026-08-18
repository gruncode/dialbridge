# Privacy and data protection

DialBridge moves a phone number from a browser to a phone. A phone number
identifies a person, so it is personal data, and this document says exactly
what happens to it.

> This is an engineering description written to be accurate, not legal advice.
> If you deploy DialBridge for an organisation, have a lawyer review your own
> circumstances.

---

## The short version

The number you click is **encrypted in your browser** and **decrypted on your
phone**. Everything in between — a public ntfy server, Google's push
infrastructure, the relay — carries a blob it has no key for. No component of
this project stores the numbers you send, and there is no analytics, no
telemetry, and no account.

---

## What data exists, and where

| Data | Where it lives | Why | How long |
|---|---|---|---|
| Encryption key | Phone and browser only | Makes the number unreadable to everyone else | Until you re-pair or delete |
| Topic name (ntfy route) | Phone and browser; visible to the ntfy server | The delivery address | Until you re-pair or delete |
| Firebase token (Play route) | Phone and browser; visible to the relay and Google | The delivery address | Until you re-pair, delete, or reinstall |
| Server / relay address | Phone and browser | Where to send | Until changed |
| The phone number itself | In transit only, encrypted; then in a notification | The entire purpose | Never written to storage |

The app writes no logs of numbers. The relay logs only the *class* of a
delivery failure. The extension keeps no history of what you clicked.

---

## Who is responsible for what

**Using it yourself, for yourself.** GDPR contains a household exemption
(Article 2(2)(c)) for processing in the course of a purely personal activity.
An individual dialling their own contacts from their own laptop falls within
it, and the obligations below do not attach to you.

**Deploying it for an organisation** — a sales team, a support desk, a clinic —
takes you outside that exemption. You become the **controller** for the numbers
your staff dial and for the device tokens involved, with the usual
consequences: a lawful basis, a record of processing, a privacy notice for
staff, and the rights machinery below.

**Which third parties you involve depends on the transport you choose:**

- *ntfy route*: the operator of the ntfy server you point at. Use `ntfy.sh` and
  that is a third party you have no contract with; self-host and there is none.
- *Firebase route*: Google, as the carrier, plus whoever operates the relay
  (probably you). Google's role brings a transfer of the device token outside
  the EEA; the number itself does not travel readably, which is the point of
  the encryption.

**Self-hosting removes third parties entirely.** Run your own ntfy server and no
one outside your organisation handles anything.

---

## Lawful basis, if you need one

For an organisational deployment, legitimate interests (Article 6(1)(f)) is the
ordinary basis for dialling business contacts, with a balancing test recorded.
Consent is rarely the right choice here — the person being called is not the
person using the software.

---

## The rights, and how this project actually supports them

**Erasure.** The app has a *Delete everything this app stores* button. It stops
the subscription, deletes the Firebase registration where one exists, and
clears every stored value. The extension has *Forget this phone*, which removes
the address and key from the browser. Neither leaves a copy behind.

**Access and portability.** Everything the app holds is listed in the table
above and shown on its own screen — there is no hidden profile to export.

**Rectification.** Re-pairing replaces the address and key; the old pairing
stops working immediately.

**Objection and restriction.** Stop the subscription, or delete the pairing.
There is no server-side account that continues to exist without you.

---

## Security measures

- AES-256-GCM, a fresh 96-bit nonce per message, 128-bit authentication tag.
- The key is generated on the phone from a cryptographic source and travels
  only inside the pairing code you copy across yourself.
- Authenticated encryption means a forged or altered message is rejected rather
  than decrypted into something misleading — the decryption step doubles as the
  authentication step.
- The phone additionally refuses anything that does not decrypt to a valid
  E.164 number, so a compromised topic cannot be used to display arbitrary
  content.
- The relay validates the payload's shape and rate-limits per device token.

**Known limits, stated plainly.** The topic name and the device token are
delivery addresses, not secrets: whoever holds one can cause your phone to
show a notification, though they cannot make it show a number of their
choosing. The pairing code *is* sensitive, because it contains the key — treat
it like a password. And nothing here defends against a compromised phone or a
compromised browser, which see the number by definition.

---

## Google Play Data Safety

If you publish to Google Play, the declaration that matches this code is:

- **Data collected:** none.
- **Data shared:** none.
- **Data processed in transit:** phone numbers, encrypted end to end, not
  stored.
- **Encryption in transit:** yes, HTTPS throughout, plus application-layer
  end-to-end encryption.
- **Deletion:** yes — the app provides an in-app deletion control.

Re-check these against your own build before submitting: if you fork this and
add analytics, a crash reporter, or a log of numbers, the declaration above
stops being true.

---

## Contact

Add your contact address here before distributing the app to anyone else. A
privacy notice with no way to reach the controller is not a privacy notice.
