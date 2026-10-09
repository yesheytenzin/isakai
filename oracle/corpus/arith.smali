.class public Lorg/isakai/oracle/Arith;
.super Ljava/lang/Object;

.method public constructor <init>()V
    .registers 1
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method

.method public static add(II)I
    .registers 3
    .param p0, "a"
    .param p1, "b"
    add-int v0, p0, p1
    return v0
.end method

.method public static abs(I)I
    .registers 2
    if-gez p0, :nonneg
    neg-int v0, p0
    return v0
    :nonneg
    return p0
.end method

.method public static fib(I)I
    .registers 4
    const/4 v0, 0x0
    const/4 v1, 0x1
    if-le p0, v1, :done
    move v2, p0
    :loop
    add-int v3, v0, v1
    move v0, v1
    move v1, v3
    add-int/lit8 v2, v2, -0x1
    if-gtz v2, :loop
    move v0, v1
    :done
    return v0
.end method
