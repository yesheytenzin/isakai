.class public Lorg/isakai/oracle/Switch;
.super Ljava/lang/Object;

.method public constructor <init>()V
    .registers 1
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method

.method public static grade(I)Ljava/lang/String;
    .registers 2
    packed-switch p0, :table
    const-string v0, "unknown"
    return-object v0
    :table
    .packed-switch 0x1
        :one
        :two
        :three
    .end packed-switch
    :one
    const-string v0, "one"
    return-object v0
    :two
    const-string v0, "two"
    return-object v0
    :three
    const-string v0, "three"
    return-object v0
.end method

.method public static classify(I)Ljava/lang/String;
    .registers 2
    sparse-switch p0, :table
    const-string v0, "other"
    return-object v0
    :table
    .sparse-switch
        0xa -> :ten
        0x64 -> :hundred
    .end sparse-switch
    :ten
    const-string v0, "ten"
    return-object v0
    :hundred
    const-string v0, "hundred"
    return-object v0
.end method

.method public static table()[I
    .registers 2
    const/4 v0, 0x4
    new-array v0, v0, [I
    fill-array-data v0, :data
    return-object v0
    :data
    .array-data 4
        0x1 0x2 0x3 0x4
    .end array-data
.end method
