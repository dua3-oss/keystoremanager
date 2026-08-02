Keystore Manager
================

Keystore Manager is a JavaFX desktop application to view and manage cryptographic keystores. It lets you create new keystores, inspect entries, generate keys and certificates, export selected items into a new keystore, and validate PEM data — all with a clean, system-aware light/dark UI.

- Status: 0.0.1-SNAPSHOT
- License: GPL-3.0-only (see `LICENSE`)

Features
--------
- Tabbed interface — manage multiple keystores at once; an empty tab is always available to start new work.
- Create keystores (choose folder, name, type such as PKCS12, and password).
- Generate keys and certificates via guided dialogs:
  - Private keys (algorithm, key size, validity, CA flag, subject fields, etc.).
  - Secret keys (choose algorithm).
- Inspect entries and view details for keys and certificates.
- Export selected entries to a new keystore (type, target location, password).
- Validate PEM content (quickly parse and display information, optional passphrase).
- Light/Dark theme support (follows system by default).

Requirements
------------
- Java 25

Build and Run
-------------
The build prints a helpful JavaFX note and verifies your JavaFX setup when running.

Build:

```
./gradlew build
```

Run (will first check JavaFX availability):

```
./gradlew run
```

If JavaFX is not available at runtime, the `verifyJavaFxSetup` task will fail with an explanatory message.

macOS Release Signing
---------------------

For a signed local macOS build, place the following values in the ignored
`.secrets.env` file: `MAC_DEV_SIGN_IDENTITY`, `MAC_DEV_SIGN_CERT_P12` (Base64
PKCS#12), and `MAC_DEV_SIGN_CERT_PASSWORD`. Gradle imports the certificate into
an ephemeral build keychain before `jpackage` runs.

For non-snapshot versions, the same task also notarizes and staples every DMG
in `build/distributions`. This requires `APPLE_NOTARY_KEY_ID`,
`APPLE_NOTARY_ISSUER_ID`, and `APPLE_NOTARY_KEY_P8` (Base64 API key data).

```
./gradlew createSignedArtifacts
```

In GitHub Actions, configure the same three names as organization secrets. The
workflow imports the certificate into the runner keychain and the Gradle build
uses the resulting `MAC_SIGN_KEYCHAIN`.

Microsoft Store MSIX
--------------------

`createMsix` builds an unsigned x64 MSIX from the Windows `jpackage` app image.
This is intentional: the Microsoft Store signs packages during ingestion, so no
Windows signing certificate is needed for a Store submission. The package is
written to `build/distributions/KeystoreManager-<version>-x64.msix`.

First reserve the application name in Partner Center, then copy the **Package/
Identity/Name** and **Package/Identity/Publisher** values exactly as shown
there. The publisher must be the complete X.500 distinguished name, normally
`CN=<publisher ID>`; it is not the publisher display name. Add these to the
ignored `.secrets.env` file for a local build:

```
MS_STORE_IDENTITY_NAME=<Partner Center package identity name>
MS_STORE_PUBLISHER=CN=<Partner Center publisher value>
MS_STORE_PUBLISHER_DISPLAY_NAME=dua3
```

Run the package build on Windows with the Windows 10 or 11 SDK installed (it
provides `MakeAppx.exe`):

```
./gradlew createMsix
```

The release workflow runs this task on every tag and publishes the resulting
MSIX alongside the other release artifacts. Configure
`MS_STORE_IDENTITY_NAME` and `MS_STORE_PUBLISHER` as GitHub Actions secrets.
Store the raw values only—do not include shell quotes or an
`MS_STORE_PUBLISHER=` prefix. `MS_STORE_PUBLISHER_DISPLAY_NAME` is optional and defaults to `dua3`. The first
Store submission must still be created in Partner Center, including the Store
listing and age-rating questionnaire; that step establishes the Store identity
used by the manifest. Upload the unsigned MSIX from the GitHub release there
and Microsoft Store performs the signing.
For later API-driven submissions, add `MS_STORE_APP_ID`,
`MS_STORE_TENANT_ID`, `MS_STORE_CLIENT_ID`, and `MS_STORE_CLIENT_SECRET` after
associating a Microsoft Entra application with the Partner Center account.

Entry Points
------------
- Main class: `com.dua3.app.keystoremanager.Main`
- Application class: `com.dua3.app.keystoremanager.KeyStoreManager`

Usage Notes
-----------
- The UI starts with an empty tab. Load or create a keystore to begin.
- When a tab contains a keystore and it is the last tab, a new empty tab is added automatically.
- View menu: switch appearance (System/Light/Dark).
- Tools menu: quick PEM validation.

Technology
----------
- Java 25, JavaFX
- Bouncy Castle (crypto provider + PKIX)
- dua3 Utility libraries (core, FX, controls)
- Atlantafx theme (Primer Light/Dark)
- Gradle build; SpotBugs and Forbidden APIs plugins (Forbidden APIs disabled for Java 25 currently)

Roadmap
-------
- Add screenshots and short walkthroughs
- Optional publishing configuration to produce releasable artifacts with POM license metadata
- Additional keystore operations and quality-of-life improvements

License
-------
This project is licensed under the GNU General Public License v3.0 only. See the `LICENSE` file for details.

Notes:
- Gradle wrapper scripts are part of the Gradle distribution and remain under their original licenses.
- Dependencies are licensed under their respective terms.

Acknowledgements
----------------
- Bouncy Castle — https://www.bouncycastle.org/
- JavaFX — https://openjfx.io/
- Atlantafx — https://github.com/mkpaz/atlantafx
- dua3 Utility — https://www.dua3.com

Contact
-------
- Author: Axel Howind (dua3)
- Email: axh@dua3.com
- Repository: https://github.com/dua3-oss/keystoremanager
