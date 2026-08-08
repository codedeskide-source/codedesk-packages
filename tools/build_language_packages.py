from __future__ import annotations

from copy import deepcopy
from hashlib import sha256
from pathlib import Path
import json
import zipfile


ROOT = Path(__file__).resolve().parents[1]
ZERO_SHA = "0" * 64
PACKAGES = {
    "java": "1.2.0",
    "c": "1.1.0",
    "cpp": "1.1.0",
    "csharp": "1.2.0",
    "scripts": "1.0.0",
}
PAYLOAD_FOLDERS = (
    "language",
    "compiler",
    "templates",
    "artifact",
    "autocomplete",
    "documentation",
    "learning",
    "corrections",
    "libraries",
    "diagnostics",
    "build",
    "project-templates",
)


def read_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(value, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )


def archive_entry(zf: zipfile.ZipFile, name: str, data: bytes) -> None:
    info = zipfile.ZipInfo(name, (2026, 7, 26, 12, 0, 0))
    info.compress_type = zipfile.ZIP_DEFLATED
    info.external_attr = 0o644 << 16
    zf.writestr(info, data)


def build_package(language: str, version: str) -> tuple[str, str, int]:
    folder = ROOT / "packages" / "languages" / language
    root_manifest_path = folder / "package.json"
    version_manifest_path = folder / "versions" / version / "package.json"
    manifest = read_json(root_manifest_path)
    if manifest["version"] != version:
        raise ValueError(
            f"{root_manifest_path} declares {manifest['version']}, expected {version}"
        )

    if not version_manifest_path.exists():
        write_json(version_manifest_path, deepcopy(manifest))
    archive_name = manifest["download"]["fileName"]
    archive_path = version_manifest_path.parent / archive_name
    archive_path.parent.mkdir(parents=True, exist_ok=True)

    embedded = deepcopy(manifest)
    embedded["download"]["sizeBytes"] = 1
    embedded["download"]["sha256"] = ZERO_SHA

    with zipfile.ZipFile(archive_path, "w") as zf:
        archive_entry(
            zf,
            "package.json",
            (json.dumps(embedded, indent=2, ensure_ascii=False) + "\n").encode(
                "utf-8"
            ),
        )
        payload_count = 0
        for folder_name in PAYLOAD_FOLDERS:
            source_folder = folder / folder_name
            if not source_folder.is_dir():
                continue
            for source in sorted(source_folder.rglob("*")):
                if not source.is_file():
                    continue
                relative = source.relative_to(folder).as_posix()
                archive_entry(zf, f"payload/{relative}", source.read_bytes())
                payload_count += 1
        if payload_count == 0:
            raise ValueError(f"{language} package has no payload files")

    data = archive_path.read_bytes()
    digest = sha256(data).hexdigest()
    size = len(data)
    for path in (root_manifest_path, version_manifest_path):
        external = read_json(path)
        external["download"]["sizeBytes"] = size
        external["download"]["sha256"] = digest
        write_json(path, external)
    return manifest["id"], version, size


def refresh_catalog(results: list[tuple[str, str, int]]) -> None:
    versions = {package_id: version for package_id, version, _ in results}
    catalog_path = ROOT / "catalog.json"
    catalog = read_json(catalog_path)
    for entry in catalog["packages"]:
        version = versions.get(entry["id"])
        if version is None:
            continue
        entry["version"] = version
        language = entry["id"].removeprefix("codedesk-language-")
        entry["manifestUrl"] = (
            "https://raw.githubusercontent.com/"
            "codedeskide-source/codedesk-packages/main/packages/languages/"
            f"{language}/versions/{version}/package.json"
        )
    write_json(catalog_path, catalog)


def refresh_inventory() -> None:
    inventory_path = ROOT / "repository-inventory.json"
    files = []
    for path in sorted(ROOT.rglob("*")):
        if not path.is_file() or path == inventory_path:
            continue
        relative = path.relative_to(ROOT).as_posix()
        data = path.read_bytes()
        files.append(
            {
                "path": relative,
                "sizeBytes": len(data),
                "sha256": sha256(data).hexdigest(),
            }
        )
    write_json(
        inventory_path,
        {
            "generatedAt": "2026-07-26T23:30:00Z",
            "files": files,
        },
    )


def main() -> None:
    results = [
        build_package(language, version)
        for language, version in PACKAGES.items()
    ]
    refresh_catalog(results)
    refresh_inventory()
    total_bytes = sum(size for _, _, size in results)
    print(
        f"Built {len(results)} language packages "
        f"({total_bytes} archive bytes)."
    )


if __name__ == "__main__":
    main()
