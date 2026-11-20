# Releasing

Pushing a tag like `v1.2.3` runs `.github/workflows/release.yml`. That builds a signed bundle,
uploads it to the Play **internal testing** track, and you promote it from the Play Console.

The version code comes from the tag: `major * 10000 + minor * 100 + patch`, so `v1.0.0` is
`10000`. Tags must keep going up.

```sh
git tag v1.0.0
git push origin v1.0.0
```

## One-off setup

### 1. Enroll in Play App Signing with the original key

The app was published in 2010 with a keystore that only exists on your machine. Google needs to
hold that key before it will accept App Bundles.

Play Console → the app → **Test and release → App integrity → App signing**. Choose to upload
the key from a Java keystore. Play gives you a PEPK tool and an encryption key. Run the tool
against the old keystore and upload the encrypted file it produces.

Keep the original keystore backed up somewhere safe and out of this repo. It's no longer needed
day to day, but it's the app's identity.

### 2. Make an upload key for CI

CI signs with a separate upload key. If that key ever leaks, Google can reset it, which they
can't do for the app signing key.

```sh
keytool -genkeypair -v -keystore upload.jks -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000
keytool -export -rfc -keystore upload.jks -alias upload -file upload_cert.pem
```

On the same App signing page, register `upload_cert.pem` as the upload key.

### 3. Service account for the Play API

1. In [Google Cloud Console](https://console.cloud.google.com/), pick or create a project and
   enable the **Google Play Android Developer API**.
2. Under IAM → Service accounts, create a service account (it needs no Cloud roles). Then
   Keys → Add key → JSON, and download the key file.
3. Play Console → **Users and permissions → Invite new users**. Enter the service account's
   email and give it access to this app with **Release apps to testing tracks**.

New permissions can take a while to work. If the first upload fails with a 403, wait an hour and
re-run the job.

### 4. GitHub secrets

In the repo settings, create an environment called `play` (Settings → Environments). Adding
yourself as a required reviewer there means each release waits for a click before it uploads.
Then add these secrets to that environment:

```sh
gh secret set --env play UPLOAD_KEYSTORE_BASE64   < <(base64 -w0 upload.jks)
gh secret set --env play UPLOAD_KEYSTORE_PASSWORD
gh secret set --env play UPLOAD_KEY_ALIAS          # "upload" if you used the command above
gh secret set --env play UPLOAD_KEY_PASSWORD
gh secret set --env play PLAY_SERVICE_ACCOUNT_JSON < service-account.json
```

Then delete the local copies of `upload.jks` and the JSON key, or move them somewhere safe.

### 5. Play Console paperwork

Play has added several requirements since 2011. It will block the release until these are done
under **Policy and programs → App content**:

- **Privacy policy**: required because the app uses the microphone. Point it at
  `https://github.com/bitplane/Microphone/blob/master/PRIVACY.md`.
- **Data safety**: no data is collected or shared. Audio is processed on the device only and the
  app has no internet access.
- **Foreground service permissions**: declare the `microphone` type. Describe it as "Plays
  microphone audio through the speaker or headphones live while the user has it turned on."
  Play asks for a short video showing the feature: screen-record turning it on, switching apps,
  and the notification.
- **Target audience** and **Content rating**: re-answer these if Play asks.

The store listing will also need fresh screenshots.
