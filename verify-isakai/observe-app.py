#!/usr/bin/env python3
"""Capture a pinned real APK window and its launch outcome without declaring acceptance."""
import argparse,json,os,signal,subprocess,sys,time,threading
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from pinned_apps import load_apps
parser=argparse.ArgumentParser()
parser.add_argument('app',type=int)
parser.add_argument('--work',type=Path,help='Use a separately installed fresh work tree')
parser.add_argument('--startup-timeout',type=float,default=180,help='Bound cold translation plus window startup (seconds)')
args=parser.parse_args()
if not 0 < args.startup_timeout <= 600:parser.error('--startup-timeout must be in (0, 600]')
app=load_apps()[args.app]
evidence=ROOT/'.cache/isakai/evidence'
evidence.mkdir(parents=True,exist_ok=True)
work=args.work or ROOT/'.cache/isakai/baseline'/app['package']
bridge=Path('/tmp')/('isakai-observe-'+str(os.getuid())+'-'+str(args.app))
env=dict(os.environ,ISAKAI_NO_PRIVILEGE='1',ISAKAI_FRAMEWORK=str(ROOT/'zig-out/framework'),ISAKAI_WINDOW='1',ISAKAI_BRIDGE_DIR=str(bridge),ISAKAI_STAGE_DIR=str(bridge)+'-stage')
log=evidence/(app['package']+'.window.log')
with log.open('w') as out:
    p=subprocess.Popen([str(ROOT/'zig-out/bin/isakai'),'--work',str(work),'launch',app['package']],env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,start_new_session=True)
    def capture():
        for chunk in iter(lambda:p.stdout.read1(4096),b''):
            out.write(chunk.decode('utf-8',errors='replace'));out.flush()
    reader=threading.Thread(target=capture,daemon=True);reader.start()
    try:
        deadline=time.monotonic()+args.startup_timeout
        while time.monotonic()<deadline:
            if p.poll() is not None:raise RuntimeError('launch exited: '+str(p.returncode))
            clients=json.loads(subprocess.check_output(['hyprctl','-j','clients'],text=True))
            client=next((c for c in clients if c['class']==app['package'] and c.get('mapped')),None)
            if client:
                time.sleep(2)
                clients=json.loads(subprocess.check_output(["hyprctl","-j","clients"],text=True))
                client=next(c for c in clients if c["class"]==app["package"] and c.get("mapped"))
                version=json.loads(subprocess.check_output(['hyprctl','-j','version'],text=True))['version']
                lua=int(version.split('.')[1])>=55
                address=client['address']
                focus='hl.dsp.focus({window="address:'+address+'"})' if lua else 'focuswindow'
                command=['hyprctl','dispatch',focus] if lua else ['hyprctl','dispatch',focus,'address:'+address]
                subprocess.run(command,check=True,capture_output=True,timeout=5)
                active=None
                for _ in range(20):
                    time.sleep(.1)
                    candidate=json.loads(subprocess.check_output(['hyprctl','-j','activewindow'],text=True))
                    if candidate.get('address')==address:
                        active=candidate
                        break
                if active is None:raise RuntimeError('test window did not gain compositor focus')
                client=active
                x,y=client["at"];w,h=client["size"]
                shot=evidence/(app['package']+'.png')
                subprocess.run(['grim','-g',f'{x},{y} {w}x{h}',str(shot)],check=True)
                close=['hyprctl','dispatch','hl.dsp.window.close({window="address:'+address+'"})'] if lua else ['hyprctl','dispatch','closewindow','address:'+address]
                subprocess.run(close,check=True)
                print('OBSERVED:',shot,'exit',p.wait(timeout=15))
                text=log.read_text()
                if 'never appeared' in text:
                    raise RuntimeError('desktop bridge socket never appeared; desktop integration is disabled')
                if not (Path(str(bridge))/'bridge.sock').exists():
                    raise RuntimeError('bridge endpoint '+str(bridge)+'/bridge.sock missing after the run')
                break
            time.sleep(.2)
        else:raise RuntimeError('no mapped window within deadline')
    finally:
        if p.poll() is None:
            os.killpg(p.pid,signal.SIGTERM)
            try:p.wait(timeout=5)
            except subprocess.TimeoutExpired:
                os.killpg(p.pid,signal.SIGKILL);p.wait(timeout=5)
        # Also reap descendants if the launcher exited before its JVM.
        try:os.killpg(p.pid,signal.SIGTERM)
        except ProcessLookupError:pass
        reader.join(timeout=5)
print(log.read_text())
