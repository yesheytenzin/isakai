#!/usr/bin/env python3
"""Fetch only the immutable, checksum-pinned APKs in the single tracker."""
import hashlib
import json
from pathlib import Path
import urllib.request
ROOT = Path(__file__).resolve().parents[1]
for app in json.loads((ROOT / 'progress.json').read_text())['apps']:
    target = ROOT / app['artifact']
    target.parent.mkdir(parents=True, exist_ok=True)
    if not target.exists():
        partial = target.with_suffix('.download')
        urllib.request.urlretrieve(app['source_url'], partial)
        if hashlib.sha256(partial.read_bytes()).hexdigest() != app['sha256']:
            partial.unlink()
            raise SystemExit('Checksum mismatch: ' + app['name'])
        partial.replace(target)
    if hashlib.sha256(target.read_bytes()).hexdigest() != app['sha256']:
        raise SystemExit('Checksum mismatch: ' + app['name'])
    print('PASS:', app['name'], app['version'], app['sha256'])
