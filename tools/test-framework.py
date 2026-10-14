#!/usr/bin/env python3
"""Compile all framework sources and execute every Java test main in isolation."""
from pathlib import Path
import subprocess
import tempfile
ROOT = Path(__file__).resolve().parents[1]
sources = sorted((ROOT / 'framework').rglob('*.java'))
tests = [p for p in sources if p.name.endswith('Test.java')]
with tempfile.TemporaryDirectory(prefix='isakai-framework-') as tmp:
    compilation=subprocess.run(['javac', '--release', '17', '-d', tmp, *map(str, sources)])
    if compilation.returncode:raise SystemExit('FAIL: framework compilation')
    for path in tests:
        name = '.'.join(path.relative_to(ROOT / 'framework').with_suffix('').parts)
        result=subprocess.run(['java', '--enable-native-access=ALL-UNNAMED', '-ea', '-Djava.awt.headless=true', '-cp', tmp, name], timeout=30)
        if result.returncode:raise SystemExit('FAIL: '+name)
        print('PASS:', name, flush=True)
print('PASS:', len(tests), 'Java test programs')
