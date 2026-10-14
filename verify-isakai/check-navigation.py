#!/usr/bin/env python3
"""Run the actual Wayland navigation fixture; require a real mapped surface."""
import json,os,subprocess,tempfile,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
evidence=ROOT/'.cache/isakai/evidence';evidence.mkdir(parents=True,exist_ok=True)
with tempfile.TemporaryDirectory(prefix='isakai-navigation-') as temp:
    base=Path(temp)
    subprocess.run(['javac','--release','17','-cp',str(ROOT/'zig-out/framework'),'-d',temp,str(ROOT/'verify-isakai/NavigationProbe.java')],check=True)
    command=['java','--enable-native-access=ALL-UNNAMED','-Djava.library.path='+str(ROOT/'zig-out/framework/lib'),'-cp',temp+':'+str(ROOT/'zig-out/framework'),'org.isakai.verify.NavigationProbe',temp]
    p=subprocess.Popen(command,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
    mapped=False
    screenshots=set()
    version=json.loads(subprocess.check_output(['hyprctl','-j','version'],text=True))['version']
    lua=int(version.split('.')[1])>=55
    titles=set()
    try:
        deadline=time.monotonic()+15
        while p.poll() is None and time.monotonic()<deadline:
            clients=json.loads(subprocess.check_output(['hyprctl','-j','clients'],text=True))
            client=next((c for c in clients if c['class']=='org.isakai.navigation' and c.get('mapped')),None)
            if client:titles.add(client.get("title"))
            if client and client.get('title') in {'Navigation FIRST','Navigation SECOND 中文 😀'} and client['title'] not in screenshots:
                address=client['address']
                focus='hl.dsp.focus({window="address:'+address+'"})' if lua else 'focuswindow'
                command=['hyprctl','dispatch',focus] if lua else ['hyprctl','dispatch',focus,'address:'+address]
                subprocess.run(command,check=True,capture_output=True,timeout=5)
                # Wait for compositor presentation after mapping/focus, not just client registration.
                time.sleep(.12)
                fresh=json.loads(subprocess.check_output(['hyprctl','-j','clients'],text=True))
                client=next((c for c in fresh if c.get('address')==address and c.get('mapped')),None)
                if not client:continue
                x,y=client['at'];w,h=client['size']
                title=client['title'];suffix='first' if title=='Navigation FIRST' else 'second'
                subprocess.run(['grim','-g',f'{x},{y} {w}x{h}',str(evidence/f'navigation-{suffix}.png')],check=True)
                screenshots.add(title);mapped=True
            time.sleep(.05)
        output,_=p.communicate(timeout=3)
        (evidence/'navigation.log').write_text(output)
        assert p.returncode==0,output
        assert mapped,'No mapped Wayland surface'
        assert {'Navigation FIRST','Navigation SECOND 中文 😀'} <= titles,('Window title changes missing',titles)
        assert {'Navigation FIRST','Navigation SECOND 中文 😀'} <= screenshots,('Missing per-screen evidence',screenshots)
        assert 'PASS: live activity navigation/result/lifecycle' in output,output
        print(output)
    finally:
        if p.poll() is None:p.kill();p.wait()
