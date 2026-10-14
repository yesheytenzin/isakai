"""Bounded classfile metadata reader for compatibility diagnostics (no code execution)."""
import struct
class ClassFile:
    def __init__(self, data):
        self.data=data; self.offset=0
        if self.u4()!=0xcafebabe:raise ValueError('invalid classfile magic')
        self.u2();self.u2()
        cp=[None]*self.u2();i=1
        while i<len(cp):
            tag=self.u1()
            if tag==1:
                n=self.u2();cp[i]=(tag,self.take(n).decode('utf-8','replace'))
            elif tag in (3,4):cp[i]=(tag,self.take(4))
            elif tag in (5,6):cp[i]=(tag,self.take(8));i+=1
            elif tag in (7,8,16,19,20):cp[i]=(tag,self.u2())
            elif tag in (9,10,11,12,17,18):cp[i]=(tag,self.u2(),self.u2())
            elif tag==15:cp[i]=(tag,self.u1(),self.u2())
            else:raise ValueError('unsupported constant tag '+str(tag))
            i+=1
        self.cp=cp
        self.u2()
        self.name=self.class_name(self.u2())
        parent=self.u2()
        self.parents=([self.class_name(parent)] if parent else [])+[self.class_name(self.u2()) for _ in range(self.u2())]
        self.fields=self.members()
        self.methods=self.members()
        self.references=[]
        self.classes=set()
        for e in cp:
            if not e:continue
            if e[0]==7:self.classes.add(cp[e[1]][1])
            if e[0] in (9,10,11):
                pair=cp[e[2]]
                self.references.append(('field' if e[0]==9 else 'method',self.class_name(e[1]),cp[pair[1]][1],cp[pair[2]][1]))
    def take(self,n):
        if n<0 or self.offset+n>len(self.data):raise ValueError('truncated classfile')
        v=self.data[self.offset:self.offset+n];self.offset+=n;return v
    def u1(self):return self.take(1)[0]
    def u2(self):return struct.unpack('>H',self.take(2))[0]
    def u4(self):return struct.unpack('>I',self.take(4))[0]
    def class_name(self,i):return self.cp[self.cp[i][1]][1]
    def members(self):
        result=set()
        for _ in range(self.u2()):
            self.u2();name=self.cp[self.u2()][1];desc=self.cp[self.u2()][1]
            result.add((name,desc))
            for _ in range(self.u2()):self.u2();self.take(self.u4())
        return result
