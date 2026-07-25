import json, hashlib, urllib.request, sys

BASE = "https://raw.githubusercontent.com/codedeskide-source/codedesk-packages/main"

def download(url):
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "CodeDesk-Repository-Verifier/1.0"}
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read()

errors = []
catalog_url = BASE + "/catalog.json"

try:
    catalog = json.loads(download(catalog_url).decode("utf-8"))
except Exception as exc:
    print("Could not download or parse catalog.json:", exc)
    sys.exit(1)

print("Catalog downloaded:", catalog_url)

for entry in catalog["packages"]:
    try:
        manifest = json.loads(download(entry["manifestUrl"]).decode("utf-8"))
        archive = download(manifest["download"]["url"])
        sha_ok = hashlib.sha256(archive).hexdigest() == manifest["download"]["sha256"]
        size_ok = len(archive) == manifest["download"]["sizeBytes"]
        print(
            f"{entry['id']}: manifest OK; archive {len(archive)} bytes; "
            f"SHA {'OK' if sha_ok else 'FAILED'}; "
            f"size {'OK' if size_ok else 'FAILED'}"
        )
        if not sha_ok or not size_ok:
            errors.append(entry["id"])
    except Exception as exc:
        errors.append(entry["id"])
        print(f"{entry['id']}: FAILED - {exc}")

if errors:
    print("DOWNLOAD VERIFICATION FAILED:", ", ".join(errors))
    sys.exit(1)

print("All catalog manifests and package archives downloaded and verified.")
