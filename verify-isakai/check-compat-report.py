#!/usr/bin/env python3
"""Seed linkage, ABI and bytecode failures and assert actionable report content."""
import hashlib,json,struct,subprocess,tempfile,zipfile,zlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def run(*args):subprocess.run(list(map(str,args)),check=True,cwd=ROOT,timeout=120)
def uleb(data,offset):
    value=shift=0
    for _ in range(5):
        byte=data[offset];offset+=1;value|=(byte&127)<<shift
        if byte<128:return value,offset
        shift+=7
    raise ValueError('invalid ULEB')
with tempfile.TemporaryDirectory(prefix='isakai-report-test-') as temp:
    base=Path(temp);apk=base/'fixture.apk';run(ROOT/'zig-out/bin/fixture-apk',apk)
    with zipfile.ZipFile(apk) as z:entries={n:z.read(n) for n in z.namelist()}
    classes=base/'classes';classes.mkdir()
    api=base/'Api.java';caller=base/'Caller.java'
    api.write_text('public class Api { public static void missing() {} }')
    caller.write_text('public class Caller { public void call() { Api.missing(); } }')
    run('javac','--release','17','-d',classes,api,caller)
    api.write_text('public class Api {}');run('javac','--release','17','-d',classes,api)
    report=base/'report.json'
    run('python3',ROOT/'tools/compat-report.py',apk,'--classes-dir',classes,'--output',report)
    data=json.loads(report.read_text())
    assert any(m['owner']=='Api' and m['name']=='missing' for m in data['unresolved_members']),data['unresolved_members']
    runtime=base/'runtime.log'
    runtime.write_text('Exception in thread "main" android.view.InflateException: PRIVATE_USER_TEXT\n'
                       '\tat app.Screen.inflate(Screen.java:1)\n'
                       'Caused by: java.lang.NoSuchMethodError: \'void android.view.View.missing()\'\n'
                       '\tat app.CustomView.create(CustomView.java:2)\n')
    run('python3',ROOT/'tools/compat-report.py',apk,'--classes-dir',classes,'--runtime-log',runtime,'--output',report)
    data=json.loads(report.read_text())
    assert len(data['runtime_failures'])==2 and data['runtime_failures'][1]['symbol']=="'void android.view.View.missing()'"
    assert data['runtime_failures'][1]['call_sites']==['app.CustomView.create']
    assert 'PRIVATE_USER_TEXT' not in report.read_text()
    arm=base/'arm.apk'
    with zipfile.ZipFile(arm,'w') as z:
        for n,b in entries.items():z.writestr(n,b)
        z.writestr('lib/arm64-v8a/libseed.so',b'not loaded')
    run('python3',ROOT/'tools/compat-report.py',arm,'--classes-dir',classes,'--output',report)
    data=json.loads(report.read_text());assert data['abi']['status']=='unsupported' and data['abi']['next_action']
    dex=bytearray(entries['classes.dex'])
    count,offset=struct.unpack_from('<II',dex,96)
    patched=False
    for index in range(count):
        cursor=struct.unpack_from('<I',dex,offset+32*index+24)[0]
        if not cursor:continue
        counts=[]
        for _ in range(4):value,cursor=uleb(dex,cursor);counts.append(value)
        for _ in range(counts[0]+counts[1]):
            _,cursor=uleb(dex,cursor);_,cursor=uleb(dex,cursor)
        for _ in range(counts[2]+counts[3]):
            _,cursor=uleb(dex,cursor);_,cursor=uleb(dex,cursor);code,cursor=uleb(dex,cursor)
            if code and not patched:
                dex[code+16]=0x73 # unused DEX opcode, deliberately unsupported
                patched=True
    assert patched
    dex[12:32]=hashlib.sha1(dex[32:]).digest()
    struct.pack_into('<I',dex,8,zlib.adler32(dex[12:]))
    broken=base/'opcode.apk'
    with zipfile.ZipFile(broken,'w') as z:
        for n,b in entries.items():z.writestr(n,dex if n=='classes.dex' else b)
    run('python3',ROOT/'tools/compat-report.py',broken,'--output',report)
    data=json.loads(report.read_text())
    failures=[m for t in data['translation'] for m in t['failed_methods']]
    assert any(m['reason']=='UnsupportedOpcode' and 'Counter' in m['method'] for m in failures),data['translation']
print('PASS: seeded missing-method, ARM-only ABI and unsupported-opcode diagnostics')
