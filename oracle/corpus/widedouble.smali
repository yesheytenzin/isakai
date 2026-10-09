.class public Lorg/isakai/oracle/Widedouble;
.super Ljava/lang/Object;

.method public constructor <init>()V
    .registers 1
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method

# double[] fill via aput-wide + readback via aget-wide. Before component
# tracking this emitted laload/lastore (.long) — a silent wrong-code path
# that reinterprets the bit patterns. daload/dastore must round-trip.
.method public static fillRead(DD)D
    .registers 10
    const/4 v0, 0x2
    new-array v0, v0, [D
    const/4 v1, 0x0
    aput-wide p0, v0, v1
    const/4 v1, 0x1
    aput-wide p2, v0, v1
    const/4 v1, 0x0
    aget-wide v2, v0, v1
    const/4 v1, 0x1
    aget-wide v4, v0, v1
    add-double v2, v2, v4
    return-wide v2
.end method

# long[] control: same shape, must keep laload/lastore behavior.
.method public static fillReadLong(JJ)J
    .registers 10
    const/4 v0, 0x2
    new-array v0, v0, [J
    const/4 v1, 0x0
    aput-wide p0, v0, v1
    const/4 v1, 0x1
    aput-wide p2, v0, v1
    const/4 v1, 0x0
    aget-wide v2, v0, v1
    const/4 v1, 0x1
    aget-wide v4, v0, v1
    add-long v2, v2, v4
    return-wide v2
.end method

# move-object alias of a double[] must stay tracked through the copy.
.method public static aliasRead(D)D
    .registers 6
    const/4 v0, 0x1
    new-array v0, v0, [D
    move-object v1, v0
    const/4 v2, 0x0
    aput-wide p0, v1, v2
    aget-wide v2, v1, v2
    return-wide v2
.end method
