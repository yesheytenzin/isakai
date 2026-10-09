.class public Lorg/isakai/oracle/Cmp;
.super Ljava/lang/Object;

.method public constructor <init>()V
    .registers 1
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method

.method public static cmpFloat(FF)I
    .registers 3
    cmpl-float v0, p0, p1
    return v0
.end method

.method public static cmpFloatG(FF)I
    .registers 3
    cmpg-float v0, p0, p1
    return v0
.end method

.method public static cmpDouble(DD)I
    .registers 6
    cmpl-double v0, p0, p2
    return v0
.end method

.method public static cmpDoubleG(DD)I
    .registers 6
    cmpg-double v0, p0, p2
    return v0
.end method

.method public static cmpLong(JJ)I
    .registers 6
    cmp-long v0, p0, p2
    return v0
.end method

.method public static signF(F)I
    .registers 4
    const/4 v0, 0x0
    cmpl-float v1, p0, v0
    return v1
.end method

.method public static cmpDoubleZero(D)I
    .registers 5
    const-wide v0, 0x0
    cmpl-double v2, p0, v0
    return v2
.end method
