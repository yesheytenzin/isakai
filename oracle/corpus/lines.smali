.class public Lorg/isakai/oracle/Lines;
.super Ljava/lang/Object;

.method public constructor <init>()V
    .registers 1
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method

.method public static add(II)I
    .registers 3
    .line 10
    add-int v0, p0, p1
    .line 11
    return v0
.end method

.method public static pick(I)I
    .registers 3
    .line 20
    if-gez p0, :nonneg
    .line 21
    neg-int v0, p0
    return v0
    :nonneg
    .line 22
    return p0
.end method

.method public static loopSum(I)I
    .registers 4
    .line 30
    const/4 v0, 0x0
    const/4 v1, 0x0
    :loop
    .line 31
    if-ge v1, p0, :done
    .line 32
    add-int v0, v0, v1
    add-int/lit8 v1, v1, 0x1
    goto :loop
    :done
    .line 33
    return v0
.end method
