# Java Language Support Changelog

## 1.2.0 — 2026-08-02

- Separates the `java17` language/version ID from the `javac` compiler ID.
- Declares Java family, language version, JDK/compiler ranges, source/target
  release, runtime kind, operating-system restrictions, and built-in status.
- Uses consistent `codedesk-language-java` and `java17` identifiers across
  install, uninstall, catalog, and version metadata.

## 1.1.1 — 2026-07-26

- Removed the duplicate `compilerId` property from the 1.1.0 language file.
- Allows installed 1.1.0 packages to update cleanly through the registry.

## 1.1.0 — 2026-07-26

- Added editable MANIFEST.MF templates and PINF-aware JAR metadata.
- Added Java 17 build operating-system compatibility information.

## 1.0.0 — 2026-07-25

- Initial repository test release.
- Added Java file recognition metadata.
- Added Java keyword and common-type autocomplete data.
- Added JDK 17 compiler discovery metadata.
- Added starter templates.
- Added repository installation, repair, update, and uninstall test payload.
