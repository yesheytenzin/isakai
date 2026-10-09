.class public Lorg/isakai/oracle/Wide;
.super Ljava/lang/Object;

.method public constructor <init>()V
    .registers 1
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method

.method public static ladd(JJ)J
    .registers 6
    add-long v0, p0, p2
    return-wide v0
.end method

.method public static consts()J
    .registers 8
    const-wide/16 v0, 1000
    const-wide/32 v2, 70000
    const-wide v4, 0x123456789ABCDEF0L
    const-wide/high16 v6, 0x4000
    add-long v0, v0, v2
    add-long v0, v0, v4
    add-long v0, v0, v6
    return-wide v0
.end method

.method public static dmul(DD)D
    .registers 6
    mul-double v0, p0, p2
    return-wide v0
.end method

.method public static fma(FFF)F
    .registers 4
    mul-float v0, p0, p1
    add-float v0, v0, p2
    return v0
.end method

.method public static convRoundTrip(J)I
    .registers 5
    long-to-int v0, p0
    int-to-long v1, v0
    sub-long v0, p0, v1
    long-to-int v0, v0
    return v0
.end method

.method public static negAll(JD)J
    .registers 7
    neg-long v0, p0
    double-to-long v2, p2
    add-long v0, v0, v2
    return-wide v0
.end method
