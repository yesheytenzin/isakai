.class public Lorg/isakai/oracle/Sync;
.super Ljava/lang/Object;

.method public constructor <init>()V
    .registers 1
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method

.method public static castLen(Ljava/lang/Object;)I
    .registers 3
    check-cast p0, Ljava/lang/String;
    :try_start
    monitor-enter p0
    :try_end
    .catchall {:try_start .. :try_end} :release
    invoke-virtual {p0}, Ljava/lang/String;->length()I
    move-result v0
    monitor-exit p0
    return v0
    :release
    move-exception v1
    monitor-exit p0
    throw v1
.end method

.method public static isString(Ljava/lang/Object;)Z
    .registers 3
    instance-of v0, p0, Ljava/lang/String;
    return v0
.end method
