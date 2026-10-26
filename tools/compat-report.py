#!/usr/bin/env python3
"""Report APK ABI, translation and linkage gaps as JSON without running private app code."""
import argparse,hashlib,json,platform,re,shutil,subprocess,tempfile,zipfile
from pathlib import Path
from classfile import ClassFile
ROOT=Path(__file__).resolve().parents[1]

def native_inventory(archive):
    """Inspect headers without loading untrusted libraries or inferring Bionic support."""
    expected={'x86_64':(2,62),'x86':(1,3),'arm64-v8a':(2,183),'armeabi-v7a':(1,40)}
    libraries=sorted(n for n in archive.namelist() if n.startswith('lib/') and n.endswith('.so'))
    abis=sorted({n.split('/')[1] for n in libraries})
    details=[]
    for name in libraries:
        parts=name.split('/')
        item={'path':name,'abi':parts[1],'selected':parts[1]=='x86_64','problems':[]}
        with archive.open(name) as entry: header=entry.read(64)
        if len(parts)!=3:item['problems'].append('invalid_library_path')
        if len(header)<16 or header[:4]!=b'\x7fELF':
            item['problems'].append('invalid_elf_header')
        elif header[4] not in (1,2) or header[5] not in (1,2) or header[6]!=1:
            item['problems'].append('invalid_elf_identification')
        else:
            elf_class=header[4]; size=64 if elf_class==2 else 52
            item.update(elf_class=32 if elf_class==1 else 64,
                        endian='little' if header[5]==1 else 'big')
            if len(header)<size:item['problems'].append('truncated_elf_header')
            else:
                endian=item['endian']
                machine=int.from_bytes(header[18:20],endian)
                elf_type=int.from_bytes(header[16:18],endian)
                item.update(machine=machine,elf_type=elf_type)
                if elf_type!=3:item['problems'].append('not_shared_object')
                if int.from_bytes(header[20:24],endian)!=1:item['problems'].append('invalid_elf_version')
                ehsize_offset=52 if elf_class==2 else 40
                if int.from_bytes(header[ehsize_offset:ehsize_offset+2],endian)!=size:
                    item['problems'].append('invalid_elf_header_size')
                if parts[1] in expected and ((elf_class,machine)!=expected[parts[1]] or endian!='little'):
                    item['problems'].append('directory_elf_abi_mismatch')
        details.append(item)
    status='bytecode_only' if not abis else 'header_only' if 'x86_64' in abis else 'unsupported'
    if any(item['selected'] and item['problems'] for item in details):status='invalid_native_libraries'
    host=platform.machine()
    result={'available':abis,'selected':'x86_64' if 'x86_64' in abis else None,
            'status':status,'libraries':libraries,'elf_libraries':details,
            'host_arch':host,'host_supported':host in ('x86_64','AMD64'),
            'installer_selection':'x86_64_directory_and_elf_header',
            'notice':'ELF headers do not establish dependency, relocation, Bionic ABI, JNI or execution compatibility.'}
    if status=='unsupported':
        result['next_action']='Requires a native/foreign-ISA backend (F3); a matching directory alone cannot supply one.'
    elif status=='invalid_native_libraries':
        result['next_action']='Fix selected library paths/ELF headers; the installer rejects mismatched or invalid host libraries.'
    if not result['host_supported']:
        result['host_limitation']='The runtime, seccomp policy and packaging currently target x86_64 Linux only.'
    return result

class ClassPath:
    def __init__(self,roots,java):
        self.roots=roots;self.cache={};self.modules=[]
        home=Path(java).resolve().parent.parent
        for p in (home/'jmods').glob('*.jmod'):
            self.modules.append(zipfile.ZipFile(p))
        self.module_names={}
        for z in self.modules:
            for name in z.namelist():
                if name.startswith('classes/') and name.endswith('.class'):
                    self.module_names[name[8:-6]]=z
    def get(self,name):
        if name.startswith('['):name='java/lang/Object'
        if name in self.cache:return self.cache[name]
        result=None
        for root in self.roots:
            p=root/(name+'.class')
            if p.exists():result=ClassFile(p.read_bytes());break
        if result is None and name in self.module_names:
            result=ClassFile(self.module_names[name].read('classes/'+name+'.class'))
        self.cache[name]=result
        return result
    def has(self,kind,owner,name,desc,seen=None):
        seen=set() if seen is None else seen
        if owner in seen:return False
        seen.add(owner)
        cls=self.get(owner)
        if cls is None:return False
        if (name,desc) in (cls.fields if kind=='field' else cls.methods):return True
        # Signature-polymorphic JVM calls use the caller's descriptor.
        if owner in ('java/lang/invoke/MethodHandle','java/lang/invoke/VarHandle') and kind=='method':
            if any(n==name for n,d in cls.methods):return True
        if name=='<init>':return False
        return any(self.has(kind,p,name,desc,seen) for p in cls.parents)

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('apk',type=Path)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--classes-dir',type=Path)
    parser.add_argument('--runtime-log',type=Path)
    args=parser.parse_args()
    with args.apk.open('rb') as apk_file: apk_hash=hashlib.file_digest(apk_file,'sha256').hexdigest()
    report={'schema_version':1,'apk_sha256':apk_hash,
            'inspection':{},'translation':[],'unresolved_classes':[],'unresolved_members':[],
            'runtime_failure':None,'runtime_failures':[],
            'capabilities':{'activity_navigation':'partial','message_queue':'implemented_subset',
                            'unicode_text':'partial_host_shaping_no_compositor_ime','webview':'unavailable_renderer',
                            'sensors_location':'unavailable_without_host_provider_test_fakes_opt_in',
                            'native':'x86_64_symbol_subset'}}
    inspect=subprocess.run([str(ROOT/'zig-out/bin/inspect-test'),str(args.apk)],capture_output=True,text=True,timeout=30)
    output=inspect.stdout+inspect.stderr
    report['inspection']['exit_code']=inspect.returncode
    for key,value in re.findall(r'(package|min|target|app|launcher)=([^\s]+)',output):
        report['inspection'][key]=value
    report['inspection']['errors']=re.findall(r'INSPECT FAIL: ([A-Za-z0-9_]+)',output)
    with zipfile.ZipFile(args.apk) as z:
        report['abi']=native_inventory(z)
        with tempfile.TemporaryDirectory(prefix='isakai-report-') as tmp:
            classes=args.classes_dir or Path(tmp)/'classes'
            if not args.classes_dir:
                for name in sorted(z.namelist()):
                    if not re.fullmatch(r'classes(\d+)?\.dex',name):continue
                    entry=z.getinfo(name)
                    if entry.file_size>256*1024*1024:raise ValueError('DEX entry exceeds 256 MiB')
                    dex=Path(tmp)/name;dex.write_bytes(z.read(name))
                    p=subprocess.run([str(ROOT/'zig-out/bin/translate-test'),'--keep-going',str(dex),str(classes)],
                                     capture_output=True,text=True,timeout=300)
                    failures=[]
                    for cls,reason in re.findall(r'TRANSLATE FAIL class \d+ \(([^)]+)\): (\w+)',p.stdout+p.stderr):
                        failures.append({'class':cls,'reason':reason,'next_action':'Minimize the failing method and add an oracle regression.'})
                    methods=re.findall(r'translation failed: (\S+): (\w+)',p.stdout+p.stderr)
                    report['translation'].append({'dex':name,'exit_code':p.returncode,'failed_classes':failures,
                                                  'failed_methods':[{'method':m,'reason':e} for m,e in methods],
                                                  'parse_errors':re.findall(r'PARSE FAIL: (\w+)',p.stdout+p.stderr),
                                                  'next_action':'Fix reported parse/translation failures before runtime testing.' if p.returncode else None})
            else:report['translation_mode']='existing classes; no fresh translation assertion'
            cp=ClassPath([classes,ROOT/'zig-out/framework'],shutil.which('java'))
            missing_classes=set();missing_members={}
            for path in sorted(classes.rglob('*.class')):
                cls=ClassFile(path.read_bytes())
                for name in cls.classes:
                    if not name.startswith('[') and cp.get(name) is None:missing_classes.add(name)
                for kind,owner,name,desc in cls.references:
                    if cp.get(owner) is not None and not cp.has(kind,owner,name,desc):
                        key=(kind,owner,name,desc)
                        item=missing_members.setdefault(key,{'kind':kind,'owner':owner,'name':name,'descriptor':desc,
                                                            'callers':[],'next_action':'Verify reachability in the target flow, then implement or report this API explicitly.'})
                        if len(item['callers'])<5 and cls.name not in item['callers']:item['callers'].append(cls.name)
            report['unresolved_classes']=sorted(missing_classes)
            report['unresolved_members']=list(missing_members.values())
    if args.runtime_log:
        # Record exception type, symbolic API and stack locations, never exception payload/app data.
        text=args.runtime_log.read_text(errors='replace')
        matches=list(re.finditer(r'^(?:Exception in thread "[^"\n]+" |Caused by: |[ \t]*Suppressed: )([\w.$]+(?:Exception|Error))(?:: ([^\n]*))?',text,re.M))
        failures=[]
        for index,match in enumerate(matches):
            stop=matches[index+1].start() if index+1<len(matches) else len(text)
            failure={'type':match[1],'call_sites':re.findall(r'^\s+at ([\w.$<>]+)\(',text[match.end():stop],re.M)[:8]}
            if match[1].endswith(('NoSuchMethodError','NoSuchFieldError','NoClassDefFoundError')):
                failure['symbol']=match[2]
            failures.append(failure)
        report['runtime_failures']=failures
        if failures: report['runtime_failure']=failures[0]
    report['notice']='Static linkage findings include unreachable code and are not proof of runtime failure or support.'
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(report,indent=2)+'\n')
    print('REPORT:',args.output,'missing classes',len(report['unresolved_classes']),'members',len(report['unresolved_members']))
if __name__=='__main__':main()
