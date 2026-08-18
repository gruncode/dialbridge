# Browser Dial relay

Only needed for the **Firebase / Google Play Services** transport. If you use
the app's own ntfy connection, there is no server and nothing here to run.

## Why a server is unavoidable here

Firebase requires a service-account credential to send a message. That
credential cannot be shipped inside a browser extension — anyone who extracted
it could push to every device registered to the project. So the extension talks
to this relay, and the relay talks to Firebase.

## What it can and cannot see

It receives a device token and a base64url blob. The blob is AES-GCM ciphertext
whose key exists only on the paired phone and browser, so the relay forwards a
phone number it cannot read. It keeps no database, writes no request logs, and
records only the *class* of a Firebase failure when one occurs.

## Deploy

Google Cloud Functions (2nd gen):

```bash
gcloud functions deploy browser-dial-relay \
  --gen2 --runtime=nodejs22 --region=europe-west1 \
  --source=. --entry-point=relay \
  --trigger-http --allow-unauthenticated \
  --memory=256Mi --max-instances=2 \
  --project=YOUR_PROJECT
```

`--allow-unauthenticated` is required because the extension has no Google
identity. Abuse is limited by the payload shape check and per-instance rate
limiting; for a deployment beyond personal use, put an API gateway or a shared
secret in front of it.

Any Node host works equally well — the handler is a plain
`(req, res)` function.

## Your obligations if you run it

Running this makes you a data controller for the device tokens passing through
it, and it places Google in your processing chain as the message carrier. See
[PRIVACY.md](../PRIVACY.md) for what that means in practice and what the
project does to keep the exposure minimal.
