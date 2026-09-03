# CI/CD Setup Guide

This document describes the automated build and release process for Hanten.

## Automated Workflows

The project uses GitHub Actions for continuous integration and automated releases:

### 1. Release Workflow (release.yml)
Automatically builds and publishes signed release APKs to GitHub Releases.

**Trigger:** Push a git tag with format `v*` (e.g., `v9.4.2`)
```bash
git tag v9.4.2
git push origin v9.4.2
```

**Output:** Signed release APK published to GitHub Releases

### 2. Nightly Workflow (nightly.yml)
Builds and publishes nightly APKs on a weekly schedule.

**Trigger:** Every Sunday at 2:00 UTC (or manual trigger via `workflow_dispatch`)
**Smart Skip:** Automatically skips the build if there are no new commits since the last nightly release

**Output:** Pre-release APK tagged as `N{YYYYMMDD}` (e.g., `N20251208`)

### 3. Debug Workflow (debug.yml)
Builds debug APK on pull requests for validation.

**Trigger:** On every pull request to `main` or `devel` branches
**Output:** Debug APK available as workflow artifact (7-day retention)

## Required GitHub Secrets

To enable automated signing, configure the following secrets in your GitHub repository settings:

### Setup Instructions

1. **Get the Keystore File (base64-encoded)**
   - If you have an existing keystore:
     ```bash
     base64 -w 0 your-keystore.jks
     ```
   - Copy the output

2. **Create GitHub Secrets**
   Navigate to: **Settings → Secrets and variables → Actions → New repository secret**

   Create these secrets:
   - **KEYSTORE_FILE**: Base64-encoded keystore file (entire output from step 1)
   - **KEYSTORE_PASSWORD**: Password for the keystore
   - **KEY_ALIAS**: Alias of the signing key (default: `hanten-key`)
   - **KEY_PASSWORD**: Password for the signing key

### Example for Fresh Setup

The single stable release keystore (generated 2026-09-03, valid until 2056):
```
Location (local): C:\Users\Raven\hanten-keystore\hanten-release.jks (NOT in repo)
Key Alias: hanten-key
Keystore/ Key Password: stored in password manager + GitHub secrets (KEYSTORE_PASSWORD / KEY_PASSWORD)
SHA-256 Fingerprint: D3:F2:AB:2D:82:AF:A0:AB:02:D5:F6:83:98:26:84:7D:85:0B:39:F8:02:65:77:7D:EB:DE:AB:E6:79:E3:CC:67
```
Local builds pick it up automatically via user env vars `KEYSTORE_FILE` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`.
CI uses the same key via GitHub secrets (`KEYSTORE_FILE` = base64 of the same file).

## Local Development Setup

The `build.gradle` is configured to support both local development and CI environments:

### For CI Environments
Environment variables are read automatically:
- `KEYSTORE_FILE`: Path to keystore (or set via secrets)
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

### For Local Development
If environment variables are not set, the build system will prompt for credentials interactively.

To set up locally with a keystore:
```bash
export KEYSTORE_FILE=/path/to/keystore.jks
export KEYSTORE_PASSWORD=your-password
export KEY_ALIAS=hanten-key
export KEY_PASSWORD=key-password

./gradlew assembleRelease
```

## Building Variants

### Debug Build
```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

### Release Build (requires signing setup)
```bash
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

### Nightly Build (requires signing setup)
```bash
./gradlew assembleNightly
# Output: app/build/outputs/apk/nightly/app-nightly.apk
# Version: N{YYYYMMDD} (auto-generated from current date)
```

## Monitoring Builds

- **Release builds**: Check GitHub Releases
- **Nightly builds**: Check GitHub Releases (marked as pre-release)
- **PR builds**: Check "Actions" tab → "Debug Build" → Artifacts section

## Troubleshooting

### Build fails with "SDK location not found"
Ensure Android SDK is properly set up. The workflows use `android-actions/setup-android@v3` which handles this automatically.

### Signing fails with "keystore corrupted or password incorrect"
- Verify the base64 encoding of the keystore is correct
- Ensure all password secrets are set correctly
- Test locally: `keytool -list -v -keystore keystore.jks -storepass <password>`

### Nightly build is skipped unexpectedly
The workflow checks for commits since the last nightly release. If no commits exist, the build is skipped. Force a build with the "workflow_dispatch" trigger.

## Certificate Fingerprints

Current release keystore SHA-256 fingerprint:
```
D3:F2:AB:2D:82:AF:A0:AB:02:D5:F6:83:98:26:84:7D:85:0B:39:F8:02:65:77:7D:EB:DE:AB:E6:79:E3:CC:67
```

This matches the built-in app validator check in `AppValidator.kt`. All release builds must use a keystore with this fingerprint for proper app signature validation.
