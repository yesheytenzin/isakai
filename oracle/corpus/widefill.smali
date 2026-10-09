.class public Lorg/isakai/oracle/Widefill;
.super Ljava/lang/Object;

.method public constructor <init>()V
    .registers 1
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method

.method public static longFill()J
    .registers 3
    const/4 v0, 0x3
    new-array v1, v0, [J
    fill-array-data v1, :data_long
    const/4 v0, 0x1
    aget-wide v0, v1, v0
    return-wide v0
    :data_long
    .array-data 8
        0x1111111122222222L
        0x7FFFFFFFFFFFFFFFL
        -0x8000000000000000L
    .end array-data
.end method

.method public static doubleFill()D
    .registers 3
    const/4 v0, 0x2
    new-array v1, v0, [D
    fill-array-data v1, :data_double
    const/4 v0, 0x1
    aget-wide v0, v1, v0
    return-wide v0
    :data_double
    .array-data 8
        2.5
        -0.0
    .end array-data
.end method

.method public static manyArgs()I
    .registers 20
    const/4 v0, 0x1
    const/4 v1, 0x2
    const/4 v2, 0x3
    const/4 v3, 0x4
    const/4 v4, 0x5
    const/4 v5, 0x6
    const/4 v6, 0x7
    const/16 v7, 0x8
    const/16 v8, 0x9
    const/16 v9, 0xa
    const/16 v10, 0xb
    const/16 v11, 0xc
    const/16 v12, 0xd
    const/16 v13, 0xe
    const/16 v14, 0xf
    const/16 v15, 0x10
    const/16 v16, 0x11
    const/16 v17, 0x12
    invoke-static/range {v0 .. v17}, Lorg/isakai/oracle/Widefill;->sum18(IIIIIIIIIIIIIIIIII)I
    move-result v0
    return v0
.end method

.method public static sum18(IIIIIIIIIIIIIIIIII)I
    .registers 20
    add-int v0, p0, p1
    add-int v0, v0, p2
    add-int v0, v0, p3
    add-int v0, v0, p4
    add-int v0, v0, p5
    add-int v0, v0, p6
    add-int v0, v0, p7
    add-int v0, v0, p8
    add-int v0, v0, p9
    add-int v0, v0, p10
    add-int v0, v0, p11
    add-int v0, v0, p12
    add-int v0, v0, p13
    add-int v0, v0, p14
    add-int v0, v0, p15
    add-int v0, v0, p16
    add-int v0, v0, p17
    return v0
.end method
