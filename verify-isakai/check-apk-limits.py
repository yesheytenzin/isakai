#!/usr/bin/env python3
"""Exercise APK file/entry limits and host ABI staging through the real CLI."""
import json,os,subprocess,tempfile,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
binary=ROOT/'zig-out/bin/isakai'
inspect=ROOT/'zig-out/bin/inspect-test'
def run(args, **kwargs):
    return subprocess.run(list(map(str,args)),capture_output=True,text=True,timeout=60,**kwargs)
with tempfile.TemporaryDirectory(prefix='isakai-apk-') as temp:
    base=Path(temp)
    fixture=base/'fixture.apk'
    subprocess.run([str(ROOT/'zig-out/bin/fixture-apk'),str(fixture)],check=True)
    for size in (256*1024*1024-1,256*1024*1024,256*1024*1024+1,1024*1024*1024):
        path=base/'sparse.apk'
        with path.open('wb') as f:f.truncate(size)
        result=run([inspect,path])
        assert result.returncode!=0
        text=result.stdout+result.stderr
        assert ('ApkTooLarge' in text)==(size>256*1024*1024),(size,text)
        path.unlink()
    with zipfile.ZipFile(fixture) as z:
        original={n:z.read(n) for n in z.namelist()}
    env=dict(os.environ,ISAKAI_NO_PRIVILEGE='1',ISAKAI_ALLOW_UNSIGNED='1')
    for abis in (['arm64-v8a'],['arm64-v8a','x86_64']):
        path=base/'native.apk'
        with zipfile.ZipFile(path,'w') as z:
            for n,data in original.items():z.writestr(n,data)
            for abi in abis:z.writestr('lib/'+abi+'/libfixture.so',b'ABI staging canary, never loaded')
        result=run([inspect,path])
        if 'x86_64' not in abis:
            assert result.returncode==1 and 'NativeUnsupported' in result.stderr
        else:
            assert result.returncode==0,result.stderr
            work=base/'work'
            result=run([binary,'--work',work,'install',path],env=env)
            assert result.returncode==0,result.stderr
            record=json.loads((work/'registry.json').read_text())['org.isakai.fixture']
            code=Path(record['apk'])
            assert (code/'lib/x86_64/libfixture.so').exists()
            assert not (code/'lib/arm64-v8a').exists()
            result=run([binary,'--work',work,'uninstall','org.isakai.fixture'],env=env)
            assert result.returncode==0,result.stderr
    path=base/'entry-too-large.apk'
    with zipfile.ZipFile(path,'w',compression=zipfile.ZIP_DEFLATED) as z:
        for n,data in original.items():z.writestr(n,data)
        with z.open('assets/oversized','w',force_zip64=True) as entry:
            block=bytes(1024*1024)
            for _ in range(257):entry.write(block)
    result=run([inspect,path])
    assert result.returncode==1 and 'EntryTooLarge' in result.stderr,result.stderr
    # Five individually bounded entries exceed the expanded-total 1 GiB cap.
    path=base/'total-too-large.apk'
    with zipfile.ZipFile(path,'w',compression=zipfile.ZIP_DEFLATED) as z:
        for n,data in original.items():z.writestr(n,data)
        for i in range(5):
            with z.open('assets/expanded'+str(i),'w',force_zip64=True) as entry:
                for _ in range(220):entry.write(block)
    result=run([inspect,path])
    assert result.returncode==1 and 'EntryTooLarge' in result.stderr,result.stderr
print('PASS: 256 MiB file boundary, 256 MiB entry cap, 1 GiB expanded cap, ARM-only denial and mixed-ABI host staging')
