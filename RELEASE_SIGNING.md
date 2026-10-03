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
