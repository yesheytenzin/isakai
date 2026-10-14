#!/usr/bin/env python3
"""Inspect actual FHS installation, legacy policy migration and uninstall."""
from pathlib import Path
import os
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix='isakai-package-') as tmp:
    root = Path(tmp) / 'root'
    stage = Path(tmp) / 'stage'
    policies = root / 'usr/share/polkit-1/actions'
    policies.mkdir(parents=True)
    legacy = policies / 'id.isakai.allocate-uid.policy'
    legacy.write_text('old policy placeholder')
    unrelated = policies / 'org.example.keep.policy'
    unrelated.write_text('unrelated package')
    def make(target):
        command = ['make', target, f'DESTDIR={root}', 'PREFIX=/usr', f'STAGE={stage}']
        print('RUN:', ' '.join(command), flush=True)
        subprocess.run(command, cwd=ROOT, check=True)
    make('install')
    policy = policies / 'id.isakai.policy'
    assert not legacy.exists(), 'legacy policy must be removed during migration'
    actions = ET.parse(policy).findall('action')
    expected = {'id.isakai.allocate-uid': 'isakai-allocate-uid',
                'id.isakai.remove-uid': 'isakai-remove-uid', 'id.isakai.run-app': 'isakai-run-app'}
    assert {a.attrib['id'] for a in actions} == expected.keys() and len(actions) == 3
    for action in actions:
        wrapper = expected[action.attrib['id']]
        path = action.find("annotate[@key='org.freedesktop.policykit.exec.path']").text
        assert path == '/usr/bin/' + wrapper
        assert os.access(root / path.lstrip('/'), os.X_OK), wrapper
        assert 'exec /usr/bin/isakai uid-helper ' in (root / path.lstrip('/')).read_text()
        assert all(n.text == 'auth_admin' for n in action.find('defaults'))
    assert (root / 'usr/bin/isakai').is_file()
    assert (root / 'usr/share/isakai/framework/org/isakai/runtime/IsakaiMain.class').is_file()
    # Debian ships the same bundle and all wrappers. Arch uses this Makefile.
    manifest = (ROOT / 'packaging/debian/isakai.install').read_text()
    for entry in ['id.isakai.policy', *expected.values()]:
        assert entry in manifest, entry
    assert 'make install DESTDIR="$pkgdir" PREFIX=/usr' in (ROOT / 'packaging/arch/PKGBUILD').read_text()
    make('uninstall')
    assert not policy.exists() and not legacy.exists()
    assert unrelated.read_text() == 'unrelated package'
    for wrapper in expected.values():
        assert not (root / 'usr/bin' / wrapper).exists()
    assert not (root / 'usr/bin/isakai').exists()
    assert not (root / 'usr/share/isakai').exists()
    print('PASS: three policy actions, executable wrappers, legacy migration, Debian/Arch mapping and uninstall')
