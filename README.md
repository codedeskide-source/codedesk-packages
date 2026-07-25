# Code Desk Official Package Repository — Test-Ready Baseline

This repository baseline is prepared for Code Desk 0.1.3 compatibility testing.

## Built-in language policy

Java, Python, HTML, and CSS belong in the main Code Desk application. They are
not initial-download packages. Their repository entries are added only when an
update, repair source, rollback version, or older compatible version is needed.

The current catalog contains only the Java 1.0.0 repository test/update package.

## Validation

Run:

```text
python tools/validate_repository.py
```

Current Java package archive:

- Size: 3658 bytes
- SHA-256: `3c02a9f36f2560eb446f34d1ecbb00e9810ae6c9a89647a5fbb2da4f9e645cf6`

## Important Code Desk 0.1.3 compatibility correction

The package archive contains a root `package.json` and `payload/`, as required.
The authoritative external package manifest contains the actual archive size
and SHA-256.

The archive's embedded `package.json` cannot contain the final hash and size of
the ZIP that contains that same file without creating circular self-reference.
Code Desk 0.1.3 must therefore compare the embedded and external manifests using
identity and installation fields, but must not require the embedded
`download.sha256` and `download.sizeBytes` values to equal the outer archive.

The outer archive is still securely verified against the authoritative external
manifest before extraction.
