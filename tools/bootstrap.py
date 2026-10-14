#!/usr/bin/env python3
"""Install checksum-pinned local build dependencies, without global changes."""
import argparse,hashlib,json,os,shutil,tarfile,time,urllib.request,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser()
parser.add_argument('names',nargs='*',default=['zig','jdk'])
args=parser.parse_args()
lock=json.loads((ROOT/'tools/dependencies.json').read_text())
base=ROOT/'.cache/toolchain';base.mkdir(parents=True,exist_ok=True)
def digest(path):
    with path.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()
for name in args.names:
    if name not in lock:raise SystemExit('Unknown dependency: '+name)
    spec=lock[name]
    archive=base/(name+('.zip' if name=='dex_tools' else '.tar.gz' if name=='jdk' else '.tar.xz'))
    if archive.exists() and digest(archive)!=spec['sha256']:
        print('Discarding incomplete or corrupt cached archive:',archive,flush=True);archive.unlink()
    if not archive.exists():
        partial=archive.with_suffix(archive.suffix+'.partial')
        for attempt in range(3):
            try:
                print('Downloading:',name,spec['version'],flush=True)
                with urllib.request.urlopen(spec['url'],timeout=60) as response,partial.open('wb') as out:
                    shutil.copyfileobj(response,out,1024*1024)
                if digest(partial)!=spec['sha256']:raise ValueError('Checksum mismatch: '+str(partial))
                partial.replace(archive);break
            except (OSError,ValueError):
                partial.unlink(missing_ok=True)
                if attempt==2:raise
                time.sleep(1)
    dest=base/name
    if not dest.exists():
        staging=base/(name+'.extracting')
        if staging.exists():shutil.rmtree(staging)
        staging.mkdir()
        try:
            if name=='dex_tools':
                with zipfile.ZipFile(archive) as z:z.extractall(staging)
                for f in staging.rglob('*.sh'):f.chmod(0o755)
            else:
                with tarfile.open(archive) as t:t.extractall(staging,filter='data')
            staging.replace(dest)
        finally:
            if staging.exists():shutil.rmtree(staging)
    print('VERIFIED:',name,spec['version'],spec['sha256'],flush=True)
zig=next((base/'zig').glob('*/zig'),None)
java=next((base/'jdk').glob('*/bin/java'),None)
dex=next((base/'dex_tools').glob('*/d2j-smali.sh'),None)
lines=[]
if zig:lines.append('export PATH="'+str(zig.parent)+':$PATH"')
if java:lines+=['export JAVA_HOME="'+str(java.parent.parent)+'"','export PATH="$JAVA_HOME/bin:$PATH"']
if dex:lines.append('export D2J="'+str(dex.parent)+'"')
(base/'env.sh').write_text('\n'.join(lines)+'\n')
print('Run: source .cache/toolchain/env.sh')
