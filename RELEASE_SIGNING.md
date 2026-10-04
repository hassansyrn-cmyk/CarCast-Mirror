# Release signing

CarCast never stores a keystore, password, private key, or signing token in the repository.

## Local or CI environment variables

- `CARCAST_RELEASE_STORE_FILE` — absolute or workspace-relative path to the protected `.jks` file
- `CARCAST_RELEASE_STORE_PASSWORD`
- `CARCAST_RELEASE_KEY_ALIAS`
- `CARCAST_RELEASE_KEY_PASSWORD`

When `CARCAST_RELEASE_STORE_FILE` is present, the Gradle `release` build type uses the corresponding signing configuration. Without it, `bundleRelease` remains intentionally unsigned so the project can be built and inspected without inventing key ownership.

## Ownership rules

- The release keystore is owned by the publisher account, not by an individual developer.
- Store the keystore in a secret manager with offline recovery documentation.
- Use Google Play App Signing for Play distribution; upload-key rotation and recovery belong in Play Console.
- Never paste passwords into issues, chat, logs, or workflow output.
- CI should receive the file through a protected secret/file mechanism and delete it after the job.
- Verify the final AAB signature with `apksigner verify --verbose` before upload.

## Local release build

```bash
export CARCAST_RELEASE_STORE_FILE=/secure/path/carcast-upload.jks
export CARCAST_RELEASE_STORE_PASSWORD='use-a-secret-manager'
export CARCAST_RELEASE_KEY_ALIAS='carcast-upload'
export CARCAST_RELEASE_KEY_PASSWORD='use-a-secret-manager'
./gradlew clean testDebugUnitTest bundleRelease
```

The repository workflow intentionally builds and tests debug artifacts only until signing ownership is configured.

## Exact upload-key creation instructions

Run this locally on a trusted machine. Choose a strong password and store it in a password manager; do not send it in chat or commit it.

```bash
mkdir -p "$HOME/.carcast-keys"
umask 077
keytool -genkeypair -v \
  -keystore "$HOME/.carcast-keys/carcast-upload.jks" \
  -alias carcast-upload \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass 'CHOOSE_AND_STORE_LOCALLY' \
  -keypass 'CHOOSE_AND_STORE_LOCALLY' \
  -dname 'CN=CarCast Mirror Upload, OU=Mobile, O=Publisher, C=US'
keytool -list -v -keystore "$HOME/.carcast-keys/carcast-upload.jks" -alias carcast-upload
```

This is the **upload key**, not the Play app-signing key. Google Play App Signing stores and uses the app-signing key to sign delivered APKs. The publisher uses the upload key to authenticate AAB uploads. Keep a secure offline backup and never commit the JKS file.

## New preferred environment names

```text
CARCAST_KEYSTORE_PATH
CARCAST_KEYSTORE_PASSWORD
CARCAST_KEY_ALIAS
CARCAST_KEY_PASSWORD
CARCAST_ADMOB_APP_ID
CARCAST_ADMOB_BANNER_AD_UNIT_ID
```

The older `CARCAST_RELEASE_*` aliases remain accepted locally for compatibility. Release ads are disabled when either production AdMob identifier is missing; debug builds always use Google's official test identifiers.

## GitHub Actions setup

Create a protected GitHub Environment named `play-release` under **Settings → Environments**. Add these secrets to that environment:

```text
CARCAST_KEYSTORE_BASE64
CARCAST_KEYSTORE_PASSWORD
CARCAST_KEY_ALIAS
CARCAST_KEY_PASSWORD
CARCAST_ADMOB_APP_ID
CARCAST_ADMOB_BANNER_AD_UNIT_ID
```

Encode the JKS locally for the secret value, without printing it:

```bash
base64 -w 0 "$HOME/.carcast-keys/carcast-upload.jks" > /tmp/carcast-upload.jks.b64
```

Paste the contents of that protected file into the GitHub secret and delete the temporary file. The manual `Android Signed Play Beta` workflow is the only workflow that consumes these secrets. Pull requests and ordinary branch CI never require them. The workflow writes the keystore only under the ephemeral runner temp directory, does not upload it, and removes it in an always-run cleanup step.
