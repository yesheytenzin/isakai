.class public Lorg/isakai/oracle/Goto32;
.super Ljava/lang/Object;

.method public constructor <init>()V
    .registers 1
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method

# goto/32 with a small positive offset: exercises the 31t opcode path
# (goto_w emission + wide fixup) without needing a 32KB method body.
.method public static skip()I
    .registers 1
    goto/32 :target
    const/4 v0, 0x1
    :target
    const/4 v0, 0x2
    return v0
.end method

.method public static skipBack(I)I
    .registers 2
    const/4 v0, 0x0
    :loop
    add-int/lit8 v0, v0, 0x1
    if-lt v0, p0, :done
    goto/32 :loop
    :done
    return v0
.end method
