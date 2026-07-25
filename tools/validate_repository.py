from pathlib import Path
import json, hashlib, zipfile, sys

root = Path(__file__).resolve().parents[1]
errors = []
catalog = json.loads((root / "catalog.json").read_text(encoding="utf-8"))

for entry in catalog["packages"]:
    manifest_rel = entry["manifestUrl"].split("/main/", 1)[1]
    manifest_path = root / manifest_rel
    if not manifest_path.is_file():
        errors.append(f"Missing manifest: {manifest_rel}")
        continue

    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    archive_rel = manifest["download"]["url"].split("/main/", 1)[1]
    archive_path = root / archive_rel

    if not archive_path.is_file():
        errors.append(f"Missing archive: {archive_rel}")
        continue

    data = archive_path.read_bytes()
    if hashlib.sha256(data).hexdigest() != manifest["download"]["sha256"]:
        errors.append(f"SHA mismatch: {archive_rel}")
    if len(data) != manifest["download"]["sizeBytes"]:
        errors.append(f"Size mismatch: {archive_rel}")

    try:
        with zipfile.ZipFile(archive_path) as zf:
            names = zf.namelist()
            if "package.json" not in names:
                errors.append(f"Missing package.json in {archive_rel}")
            if not any(name.startswith("payload/") for name in names):
                errors.append(f"Missing payload/ in {archive_rel}")
    except zipfile.BadZipFile:
        errors.append(f"Invalid ZIP: {archive_rel}")

if errors:
    print("REPOSITORY VALIDATION FAILED")
    for error in errors:
        print(" -", error)
    sys.exit(1)

print(f"Repository validation passed: {len(catalog['packages'])} catalog packages.")
