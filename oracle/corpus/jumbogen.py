#!/usr/bin/env python3
"""Generate oracle/corpus/jumbostr.smali: >255 distinct strings so high pool
indexes force the wide (ldc_w) string path, plus one const-string/jumbo ref
that explicitly decodes a 32-bit string index.

Regenerate: python3 oracle/corpus/jumbogen.py
Output: oracle/corpus/jumbostr.smali (+ JumbostrDriver.java if missing).
"""
import pathlib

HERE = pathlib.Path(__file__).parent
N = 300  # distinct strings; pool idx of the last exceeds 255
CLS = "Lorg/isakai/oracle/Jumbostr;"


def main() -> None:
    lines: list[str] = [
        f".class public {CLS}",
        ".super Ljava/lang/Object;",
        "",
        ".method public constructor <init>()V",
        "    .registers 1",
        "    invoke-direct {p0}, Ljava/lang/Object;-><init>()V",
        "    return-void",
        ".end method",
        "",
        ".method public static pick(I)Ljava/lang/String;",
        "    .registers 3",
    ]
    for i in range(N):
        lines += [
            f'    const-string v1, "jumbo-filler-{i:04d}"',
            f"    const/16 v2, {i}",
            f"    if-ne p0, v2, :next{i}",
            "    move-object v0, v1",
            "    return-object v0",
            f"    :next{i}",
        ]
    lines += [
        '    const-string v0, "jumbo-default"',
        "    return-object v0",
        ".end method",
        "",
        # Explicit jumbo opcode: const-string/jumbo with a 32-bit idx.
        # smali assembles this to 0x1b when the index needs it.
        ".method public static pinned()Ljava/lang/String;",
        "    .registers 1",
        '    const-string/jumbo v0, "jumbo-pinned"',
        "    return-object v0",
        ".end method",
    ]
    (HERE / "jumbostr.smali").write_text("\n".join(lines) + "\n")

    drv = HERE / "JumbostrDriver.java"
    if not drv.exists():
        drv.write_text(
            "public class JumbostrDriver {\n"
            '    public static void main(String[] a) throws Exception {\n'
            '        Class<?> c = Class.forName("org.isakai.oracle.Jumbostr");\n'
            '        System.out.println("first=" + c.getMethod("pick", int.class).invoke(null, 0));\n'
            '        System.out.println("mid=" + c.getMethod("pick", int.class).invoke(null, 150));\n'
            '        System.out.println("high=" + c.getMethod("pick", int.class).invoke(null, 299));\n'
            '        System.out.println("def=" + c.getMethod("pick", int.class).invoke(null, 9999));\n'
            '        System.out.println("pinned=" + c.getMethod("pinned").invoke(null));\n'
            '    }\n'
            "}\n"
        )
    print(f"wrote jumbostr.smali with {N} filler strings")


if __name__ == "__main__":
    main()
