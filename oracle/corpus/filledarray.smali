.class public Lorg/isakai/oracle/Filledarray;
.super Ljava/lang/Object;

.method public constructor <init>()V
    .registers 1
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method

.method public static ints(III)[I
    .registers 4
    filled-new-array {p0, p1, p2}, [I
    move-result-object v0
    return-object v0
.end method

.method public static range(IIII)[I
    .registers 6
    move v0, p0
    move v1, p1
    move v2, p2
    move v3, p3
    filled-new-array/range {v0 .. v3}, [I
    move-result-object v0
    return-object v0
.end method

.method public static strings(Ljava/lang/String;Ljava/lang/String;)[Ljava/lang/String;
    .registers 3
    filled-new-array {p0, p1}, [Ljava/lang/String;
    move-result-object v0
    return-object v0
.end method

.method public static floats(FF)[F
    .registers 3
    filled-new-array {p0, p1}, [F
    move-result-object v0
    return-object v0
.end method
