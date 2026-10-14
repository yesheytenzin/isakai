#!/usr/bin/env python3
"""Drive the shipped JVM entry point: idle app executors cannot hang exit or hide crashes."""
import subprocess,tempfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix='isakai-process-exit-') as directory:
    temp=Path(directory);(temp/'data').mkdir()
    subprocess.run(['javac','--release','17','-cp',str(ROOT/'zig-out/framework'),'-d',str(temp),str(ROOT/'verify-isakai/ProcessExitProbe.java')],check=True)
    for name,expected in [('Idle',0),('Crash',1)]:
        command=['java','-cp',str(ROOT/'zig-out/framework')+':'+str(temp),'org.isakai.runtime.IsakaiMain',str(temp/'data'),'org.isakai.verify.ProcessExitProbe$'+name,'-','org.isakai.processprobe']
        result=subprocess.run(command,text=True,capture_output=True,timeout=5)
        assert result.returncode==expected,(name,result.returncode,result.stdout,result.stderr)
        if name=='Crash':assert 'SEEDED_WORKER_CRASH' in result.stderr and 'isakai: run complete' not in result.stdout
        else:assert 'isakai: run complete' in result.stdout
        print('PASS:',name,'exit',result.returncode)
