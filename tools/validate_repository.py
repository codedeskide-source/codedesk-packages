from pathlib import Path
import hashlib, json, sys, zipfile

root = Path(__file__).resolve().parents[1]
errors = []

def load_json(rel):
    try:
        return json.loads((root / rel).read_text(encoding="utf-8"))
    except Exception as exc:
        errors.append(f"{rel}: {exc}")
        return {}

catalog = load_json("catalog.json")
manifest = load_json("packages/languages/java/package.json")
release = load_json("packages/languages/java/versions/1.0.0/package-release.json")
archive = root / "packages/languages/java/versions/1.0.0/java-language-1.0.0.zip"

if catalog.get("repositoryId") != "codedesk-official":
    errors.append("catalog repositoryId must be codedesk-official")

packages = catalog.get("packages", [])
if len(packages) != 1 or packages[0].get("id") != manifest.get("id"):
    errors.append("catalog package entry does not match package manifest")

if archive.exists():
    size = archive.stat().st_size
    digest = hashlib.sha256(archive.read_bytes()).hexdigest()
    if manifest.get("download", {}).get("sizeBytes") != size:
        errors.append("manifest sizeBytes does not match archive")
    if manifest.get("download", {}).get("sha256") != digest:
        errors.append("manifest sha256 does not match archive")
    if release.get("size") != size or release.get("sha256") != digest:
        errors.append("release metadata does not match archive")
    with zipfile.ZipFile(archive) as z:
        names = set(z.namelist())
        if "package.json" not in names:
            errors.append("archive is missing package.json")
        if not any(n.startswith("payload/") for n in names):
            errors.append("archive is missing payload/")
else:
    errors.append("package archive is missing")

if errors:
    print("REPOSITORY VALIDATION FAILED")
    for error in errors:
        print(" -", error)
    sys.exit(1)

print("REPOSITORY VALIDATION PASSED")
print("Package archive:", archive.relative_to(root))
print("Size:", archive.stat().st_size)
print("SHA-256:", hashlib.sha256(archive.read_bytes()).hexdigest())
