.class public Lorg/isakai/oracle/Constclass;
.super Ljava/lang/Object;

.method public constructor <init>()V
    .registers 1
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method

.method public static stringClass()Ljava/lang/Class;
    .registers 1
    const-class v0, Ljava/lang/String;
    return-object v0
.end method

.method public static intClass()Ljava/lang/Class;
    .registers 1
    const-class v0, I
    return-object v0
.end method

.method public static arrayClass()Ljava/lang/Class;
    .registers 1
    const-class v0, [Ljava/lang/String;
    return-object v0
.end method

.method public static boolClass()Ljava/lang/Class;
    .registers 1
    const-class v0, Z
    return-object v0
.end method
