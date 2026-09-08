import urllib.request
import json
import os
import sys
import re
import subprocess
import urllib.parse

def get_token():
    env_token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    if env_token:
        return env_token
    try:
        url = subprocess.check_output(["git", "config", "remote.origin.url"], text=True).strip()
        match = re.search(r"ghp_[a-zA-Z0-9]+", url)
        if match:
            return match.group(0)
    except Exception:
        pass
    return ""

def main():
    token = get_token()
    if not token:
        print("Error: No se encontró token de GitHub.")
        sys.exit(1)

    repo = "ArribaJesucristo/pina4viewer"
    
    # Leer versión actual desde version.json
    with open("version.json", "r", encoding="utf-8") as f:
        vinfo = json.load(f)
    
    version_name = vinfo.get("versionName", "8.4.2")
    tag = f"v{version_name}"
    name = f"{tag}: Solucion de seleccion en dialogos de canales, deportes y zona horaria (Android TV / Fire TV)"
    body = f"### Novedades en {tag}\n\n{vinfo.get('changelog', '')}"
    apk_path = os.path.abspath(f"pina4viewer-{tag}.apk")
    
    if not os.path.exists(apk_path):
        # Fallback al build folder
        fallback = os.path.abspath(r"pina4viewer-kotlin\app\build\outputs\apk\release\app-release.apk")
        if os.path.exists(fallback):
            apk_path = fallback
        else:
            print(f"Error: No se encontró el APK {apk_path}")
            sys.exit(1)

    print(f"1. Creando release {tag}...")
    release_data = json.dumps({
        "tag_name": tag,
        "target_commitish": "main",
        "name": name,
        "body": body,
        "draft": False,
        "prerelease": False
    }).encode("utf-8")

    req = urllib.request.Request(
        f"https://api.github.com/repos/{repo}/releases",
        data=release_data,
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "Content-Type": "application/json",
            "User-Agent": "Mozilla/5.0"
        }
    )

    with urllib.request.urlopen(req) as resp:
        res = json.loads(resp.read().decode("utf-8"))
        release_id = res["id"]
        upload_url = res["upload_url"].split("{")[0]
        print(f"Release {release_id} creada exitosamente!")

    print(f"2. Subiendo APK ({os.path.getsize(apk_path)} bytes)...")
    with open(apk_path, "rb") as f:
        apk_data = f.read()

    apk_name = f"pina4viewer-{tag}.apk"
    upload_req_url = f"{upload_url}?name={urllib.parse.quote(apk_name)}"

    req2 = urllib.request.Request(
        upload_req_url,
        data=apk_data,
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "Content-Type": "application/vnd.android.package-archive",
            "User-Agent": "Mozilla/5.0"
        }
    )

    with urllib.request.urlopen(req2) as resp2:
        res2 = json.loads(resp2.read().decode("utf-8"))
        print(f"Asset subido: {res2.get('name')} ({res2.get('size')} bytes)")
        print(f"URL de descarga: {res2.get('browser_download_url')}")

if __name__ == "__main__":
    main()
