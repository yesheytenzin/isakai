.class public Lorg/isakai/oracle/Arrays;
.super Ljava/lang/Object;

.method public constructor <init>()V
    .registers 1
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method

.method public static sum([I)I
    .registers 5
    array-length v0, p0
    const/4 v1, 0x0
    const/4 v2, 0x0
    :loop
    if-ge v2, v0, :done
    aget v3, p0, v2
    add-int v1, v1, v3
    add-int/lit8 v2, v2, 0x1
    goto :loop
    :done
    return v1
.end method

.method public static fill(II)[I
    .registers 4
    new-array v0, p0, [I
    const/4 v1, 0x0
    :loop
    if-ge v1, p0, :done
    aput p1, v0, v1
    add-int/lit8 v1, v1, 0x1
    goto :loop
    :done
    return-object v0
.end method

.method public static firstEq([Ljava/lang/Object;Ljava/lang/Object;)Z
    .registers 4
    const/4 v0, 0x0
    aget-object v1, p0, v0
    if-eq v1, p1, :same
    const/4 v0, 0x0
    return v0
    :same
    const/4 v0, 0x1
    return v0
.end method
