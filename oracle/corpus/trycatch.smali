.class public Lorg/isakai/oracle/TryCatch;
.super Ljava/lang/Object;

.method public constructor <init>()V
    .registers 1
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method

.method public static divOr(II)I
    .registers 4
    :try_start
    div-int v0, p0, p1
    :try_end
    .catchall {:try_start .. :try_end} :catch_all
    return v0
    :catch_all
    move-exception v1
    const/4 v0, -0x1
    return v0
.end method

.method public static parseOr(Ljava/lang/String;I)I
    .registers 4
    :try_start
    invoke-static {p0}, Ljava/lang/Integer;->parseInt(Ljava/lang/String;)I
    move-result v0
    :try_end
    .catch Ljava/lang/NumberFormatException; {:try_start .. :try_end} :catch_num
    return v0
    :catch_num
    move-exception v0
    return p1
.end method
