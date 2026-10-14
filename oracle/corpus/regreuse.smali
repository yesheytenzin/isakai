.class public Lorg/isakai/oracle/Regreuse;
.super Ljava/lang/Object;

# Dead throwing block reuses integer registers for objects. Multiple live
# predecessors at :compare must still compare the reaching integer values.
.method public static sentinel(IZ)I
    .registers 4
    if-nez p1, :throwing
    if-eqz p0, :compare
    goto :compare
  :throwing
    const-string p0, "unused throwing path"
    new-instance v0, Ljava/lang/IllegalStateException;
    invoke-direct {v0, p0}, Ljava/lang/IllegalStateException;-><init>(Ljava/lang/String;)V
    throw v0
  :compare
    const/4 v0, -0x1
    if-eq p0, v0, :bad
    const/4 v0, 0x1
    return v0
  :bad
    const/4 v0, 0x0
    return v0
.end method

.method public static references(Ljava/lang/Object;Ljava/lang/Object;)I
    .registers 3
    if-eq p0, p1, :same
    const/4 v0, 0x0
    return v0
  :same
    const/4 v0, 0x1
    return v0
.end method
