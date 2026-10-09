.class public Lorg/isakai/oracle/Consts;
.super Ljava/lang/Object;

.method public constructor <init>()V
    .registers 1
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method

.method public static small()I
    .registers 1
    const v0, 0x7FFF
    return v0
.end method

.method public static negSmall()I
    .registers 1
    const v0, -0x8000
    return v0
.end method

.method public static justOver()I
    .registers 1
    const v0, 0x8000
    return v0
.end method

.method public static justUnder()I
    .registers 1
    const v0, -0x8001
    return v0
.end method

.method public static big()I
    .registers 1
    const v0, 0x12345678
    return v0
.end method

.method public static negBig()I
    .registers 1
    const v0, -0x12345678
    return v0
.end method

.method public static minInt()I
    .registers 1
    const v0, -0x80000000
    return v0
.end method

.method public static maxInt()I
    .registers 1
    const v0, 0x7FFFFFFF
    return v0
.end method

.method public static high16()I
    .registers 1
    const/high16 v0, 0x1234
    return v0
.end method

.method public static high16Neg()I
    .registers 1
    const/high16 v0, -0x1
    return v0
.end method
