"""Read APK pins from their single authored source in progress/APPS.md."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def load_apps(root=ROOT):
    path = Path(root) / 'progress' / 'APPS.md'
    blocks = re.findall(r'^```json\n(.*?)^```\s*$', path.read_text(), re.M | re.S)
    if len(blocks) != 1:
        raise ValueError(f'{path}: expected one JSON block containing APK pins')
    apps = json.loads(blocks[0])
    required = ('name', 'version', 'package', 'source_url', 'sha256', 'artifact')
    if not isinstance(apps, list) or not apps:
        raise ValueError(f'{path}: APK pins must be a nonempty list')
    packages = set()
    for app in apps:
        if not isinstance(app, dict) or any(not isinstance(app.get(key), str) or not app[key] for key in required):
            raise ValueError(f'{path}: each APK pin requires {", ".join(required)}')
        if not re.fullmatch(r'[0-9a-f]{64}', app['sha256']):
            raise ValueError(f'{path}: invalid SHA-256 for {app["package"]}')
        if app['package'] in packages:
            raise ValueError(f'{path}: duplicate package {app["package"]}')
        packages.add(app['package'])
    return apps
