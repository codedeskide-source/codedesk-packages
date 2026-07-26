# Code Desk Official Package Repository

This Build 26 archive is ready to become the root contents of
`codedeskide-source/codedesk-packages`. Extract and commit the archive
**contents** so `catalog.json` is directly at the repository root.

The catalog contains ten CodeDesk 0.1.3 packages:

- Java 17 update/repair support with editable MANIFEST.MF data
- Python, HTML, CSS, and cross-platform Scripts support
- Optional C17 compiler integration for GCC/Clang
- Optional C++17 compiler integration for G++/Clang++
- Optional C# console and Windows GUI integration for the .NET SDK
- Two QA packages for lifecycle and compiler-configuration testing

Java 17, Python, HTML, CSS, and Scripts have base definitions in CodeDesk.
Their registry entries provide update/repair payloads. C, C++, and C# appear
only when their packages are installed and enabled.

Compiler packages do not redistribute third-party toolchains. They define the
language models, file templates, diagnostics, and commands used to invoke a
compatible compiler already installed on the computer.

Each language definition also declares the operating systems its build adapter
can target. Native C/C++ packages currently declare the host OS, C# console
projects support the three desktop systems, and the C# Windows Forms starter
is restricted to Windows.

After changing a language payload, run:

```text
python tools/build_language_packages.py
python tools/validate_repository.py
```

The builder deterministically refreshes package ZIPs, SHA-256 values, sizes,
version manifests, catalog versions, and `repository-inventory.json`.
