.class public Lorg/isakai/oracle/Moves;
.super Ljava/lang/Object;

.method public constructor <init>()V
    .registers 1
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method

# Exercises move/from16 (0x02) with a high source register.
.method public static from16()I
    .registers 20
    const/16 v10, 1234
    move/from16 v0, v10
    return v0
.end method

# Exercises move-object/from16 (0x08).
.method public static objFrom16()Ljava/lang/String;
    .registers 20
    const-string v10, "high-reg"
    move-object/from16 v0, v10
    return-object v0
.end method

# Exercises move-wide/from16 (0x05).
.method public static wideFrom16()J
    .registers 20
    const-wide/16 v10, 777
    move-wide/from16 v0, v10
    return-wide v0
.end method

# Narrow array variants: boolean/byte/char/short round-trips.
.method public static boolArr()I
    .registers 4
    const/4 v0, 3
    new-array v1, v0, [Z
    const/4 v0, 1
    const/4 v2, 0
    aput-boolean v0, v1, v2
    const/4 v2, 2
    aput-boolean v0, v1, v2
    const/4 v2, 0
    aget-boolean v0, v1, v2
    return v0
.end method

.method public static byteArr()I
    .registers 4
    const/4 v0, 2
    new-array v1, v0, [B
    const/16 v0, 0xAB
    const/4 v2, 1
    aput-byte v0, v1, v2
    aget-byte v0, v1, v2
    return v0
.end method

.method public static charArr()I
    .registers 4
    const/4 v0, 2
    new-array v1, v0, [C
    const/16 v0, 0x4242
    const/4 v2, 0
    aput-char v0, v1, v2
    aget-char v0, v1, v2
    return v0
.end method

.method public static shortArr()I
    .registers 4
    const/4 v0, 2
    new-array v1, v0, [S
    const/16 v0, -2000
    const/4 v2, 1
    aput-short v0, v1, v2
    aget-short v0, v1, v2
    return v0
.end method
