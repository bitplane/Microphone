# Releasing

1. Write the release notes. The version code comes from the tag, `major * 10000 + minor * 100 +
   patch`, so `v1.0.2` is `10002`. Add `fastlane/metadata/android/<language>/changelogs/10002.txt`
   for each language (500 characters max). The release fails early if `en-US` is missing.
2. Tag and push:

   ```sh
   git tag v1.0.2
   git push origin v1.0.2
   ```

`.github/workflows/release.yml` then:

1. builds an APK and an App Bundle, both signed with the app's original 2010 key;
2. publishes the APK as a GitHub Release;
3. sends the bundle to Play **production** with fastlane `supply`, along with the release notes
   and the store listing. Google reviews it, then it goes live. This step is skipped, with a
   warning, until `PLAY_SERVICE_ACCOUNT_JSON` is set.

Web and Play installs are signed with the same key, so each can upgrade the other. Tags must keep
going up.

## Store listing

The listing lives in `fastlane/metadata/android/<language>/`: `title.txt` (30 characters max),
`short_description.txt` (80), `full_description.txt` (4000) and `images/phoneScreenshots/`. Every
release replaces the listing on Play with these files, so edit them here, not in the Play Console.

The screenshots are only kept in `en-US`. The other languages link to its `images` folder, so
they all show the same ones. Phone screenshots can't be taller than twice their width; take them
at 1080x1920 (`adb shell wm size 1080x1920`).

The job runs in the `release` environment, which only release tags can use. If you want each
release to wait for your approval, add yourself as a required reviewer there.

## One-off setup

### 1. GitHub secrets (needed for any release)

The original keystore is a JKS file at `~/Documents/android.bitplane.net.keystore`, alias
`gazdroid`. Create an environment called `release` (Settings → Environments), then add these
secrets to it:

```sh
gh secret set --env release SIGNING_KEYSTORE_BASE64 < <(base64 -w0 ~/Documents/android.bitplane.net.keystore)
gh secret set --env release SIGNING_KEYSTORE_PASSWORD
gh secret set --env release SIGNING_KEY_ALIAS --body gazdroid
gh secret set --env release SIGNING_KEY_PASSWORD   # the same as the keystore password, unless you set them differently
```

That's enough for GitHub Releases. Keep a backup of the keystore and its password somewhere
outside GitHub. If it's lost, the app can never be updated again.

### 2. Play App Signing

The app was published before App Bundles existed. Google needs a copy of the key before it will
accept a bundle.

Play Console → Microphone → **Test and release → App integrity → App signing**. Choose to upload
the key from a Java keystore. Play gives you a PEPK tool and an encryption key. Run the tool
against the keystore and upload the encrypted file it produces. Don't register a separate upload
key: CI uploads with the same key.

### 3. Service account for the Play API

1. In [Google Cloud Console](https://console.cloud.google.com/), pick or create a project and
   enable the **Google Play Android Developer API**.
2. Under IAM → Service accounts, create a service account (it needs no Cloud roles). Then
   Keys → Add key → JSON, and download the key file.
3. Play Console → **Users and permissions → Invite new users**. Enter the service account's
   email and give it access to this app with **Release to production, exclude devices, and use
   Play App Signing** and **Manage store presence**.
4. `gh secret set --env release PLAY_SERVICE_ACCOUNT_JSON < service-account.json`, then delete
   the JSON file.

New permissions can take a while to work. If the first upload fails with a 403, wait an hour and
re-run the job. The GitHub Release step handles re-runs.

### 4. Play Console paperwork

Play will block the release until these are done under **Policy and programs → App content**:

- **Privacy policy**: required because the app uses the microphone. Point it at
  `https://github.com/bitplane/Microphone/blob/master/PRIVACY.md`.
- **Data safety**: no data is collected or shared. Audio is processed on the device only and the
  app has no internet access.
- **Foreground service permissions**: declare the `microphone` type. Describe it as "Plays
  microphone audio through the speaker or headphones live while the user has it turned on."
  Play asks for a short video showing the feature: screen-record turning it on, switching apps,
  and the notification.
- **Target audience** and **Content rating**: re-answer these if Play asks.

## Android developer verification

The package name is registered to the bitplane account. That was done by uploading an APK
containing `assets/adi-registration.properties`. The file only had to be in that one APK, so it
isn't in the repo.
