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
