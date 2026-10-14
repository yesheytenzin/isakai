package org.isakai.verify;

import android.app.Application;
import java.nio.file.*;
import java.util.*;

/** App-side assertions, invoked by the real IsakaiMain under the production policy. */
public final class RuntimeProbe extends Application {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static Path property(String key) { return Path.of(System.getProperty(key)); }
    private static void deniedWrite(Path path) throws Exception {
        try {
            Files.writeString(path, "escape", StandardOpenOption.WRITE);
        } catch (AccessDeniedException expected) { return; }
        throw new AssertionError("write unexpectedly allowed: " + path);
    }
    @Override public void onCreate() {
        try {
            Path data = Path.of(getDataDirString());
            Files.writeString(data.resolve("allowed.txt"), "persistent-data");
            check(Files.readString(data.resolve("allowed.txt")).equals("persistent-data"), "data round-trip");
            Path stage = property("probe.stage").resolve("transfer.txt");
            Files.writeString(stage, "transfer");
            check(Files.readString(stage).equals("transfer"), "stage round-trip");
            Path code = property("probe.code");
            check(Files.readString(code).equals("readonly"), "code is readable");
            deniedWrite(code);
            deniedWrite(property("probe.outside"));
            try {
                Files.readString(property("probe.other"));
                throw new AssertionError("other app data unexpectedly readable");
            } catch (AccessDeniedException expected) {}
            Path temp = Files.createTempFile("isakai-runtime-", ".tmp");
            check(temp.startsWith(data.resolve("cache/tmp")), "JVM temp must be inside app data");
            Files.delete(temp);
            Path external=getExternalFilesDir("Documents").toPath();
            check(external.startsWith(data),"external files must remain inside app data");
            Files.writeString(external.resolve("unicode.txt"),"ཀ 中文 😀");
            check(Files.readString(external.resolve("unicode.txt")).equals("ཀ 中文 😀"),"Unicode storage round-trip");
            android.database.sqlite.SQLiteOpenHelper helper=new android.database.sqlite.SQLiteOpenHelper(
                    this,"runtime.db",null,1,db -> { throw new AssertionError("unexpected database corruption"); }) {
                public void onCreate(android.database.sqlite.SQLiteDatabase db) {
                    db.execSQL("CREATE TABLE probe (value TEXT)");
                    db.execSQL("INSERT INTO probe VALUES ('中文 😀')");
                }
                public void onUpgrade(android.database.sqlite.SQLiteDatabase db,int oldVersion,int newVersion) {
                    throw new AssertionError("unexpected version change");
                }
            };
            helper.setWriteAheadLoggingEnabled(true);
            android.database.sqlite.SQLiteDatabase database=helper.getWritableDatabase();
            check(database.getVersion()==1,"database version was not saved");
            check(database.isWriteAheadLoggingEnabled(),"helper WAL request was not applied");
            try(android.database.Cursor rows=database.rawQuery("SELECT value FROM probe",null)) {
                check(rows.moveToFirst() && "中文 😀".equals(rows.getString(0)),"database Unicode round-trip");
            }
            try(android.database.Cursor rows=database.rawQueryWithFactory((db,driver,editTable,query)-> {
                query.bindLong(1,42);query.bindString(2,"ཀ 中文 😀");query.bindBlob(3,new byte[]{0,1,-1});query.bindNull(4);
                return new android.database.sqlite.SQLiteCursor(driver,editTable,query);
            },"SELECT ?, ?, ?, ?",null,null)) {
                check(rows instanceof java.io.Closeable,"Android cursor Closeable ABI");
                check(rows.moveToFirst() && rows.getLong(0)==42 && "ཀ 中文 😀".equals(rows.getString(1))
                        && java.util.Arrays.equals(rows.getBlob(2),new byte[]{0,1,-1}) && rows.isNull(3),"custom query factory typed bindings");
            }
            try(android.database.sqlite.SQLiteStatement scalar=database.compileStatement("SELECT 7")) {
                check(scalar.simpleQueryForLong()==7 && scalar.simpleQueryForLong()==7,"scalar statements must be reusable");
            }
            try(android.database.Cursor rows=database.rawQuery("SELECT length('😀'),hex('😀'),'é' = 'é' COLLATE UNICODE, 'a' < 'b' COLLATE UNICODE",null)) {
                check(rows.moveToFirst() && rows.getInt(0)==1 && "F09F9880".equals(rows.getString(1))
                        && rows.getInt(2)==1 && rows.getInt(3)==1,"standard UTF-8 storage and ICU Unicode collation");
            }
            try(android.database.sqlite.SQLiteStatement binding=database.compileStatement("SELECT ?20")) {
                binding.bindLong(20,99);check(binding.simpleQueryForLong()==99,"high parameter index binding");
                binding.clearBindings();check(binding.simpleQueryForLong()==0,"clearBindings must clear all parameters");
            }
            database.execSQL("CREATE TABLE concurrent_probe (value INTEGER)");
            database.beginTransactionNonExclusive();
            database.execSQL("INSERT INTO concurrent_probe VALUES (1)");
            java.util.concurrent.CountDownLatch attempted=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.atomic.AtomicReference<Throwable> workerFailure=new java.util.concurrent.atomic.AtomicReference<>();
            Thread transactionWorker=new Thread(()-> {
                try {
                    check(!database.inTransaction(),"transaction state leaked between threads");
                    attempted.countDown();database.beginTransactionNonExclusive();
                    try { database.execSQL("INSERT INTO concurrent_probe VALUES (2)");database.setTransactionSuccessful(); }
                    finally { database.endTransaction(); }
                }catch(Throwable failure) { workerFailure.set(failure); }
            });
            transactionWorker.start();check(attempted.await(5,java.util.concurrent.TimeUnit.SECONDS),"transaction worker did not start");
            database.setTransactionSuccessful();database.endTransaction();transactionWorker.join(5000);
            check(!transactionWorker.isAlive() && workerFailure.get()==null,"concurrent transaction failed: "+workerFailure.get());
            database.beginTransaction();
            database.execSQL("INSERT INTO concurrent_probe VALUES (3)");
            database.beginTransactionNonExclusive();database.endTransaction();
            database.setTransactionSuccessful();database.endTransaction();
            try(android.database.Cursor rows=database.rawQuery("SELECT count(*) FROM concurrent_probe",null)) {
                check(rows.moveToFirst() && rows.getInt(0)==2,"serialized transactions/nested rollback");
            }
            android.os.CancellationSignal cancelled=new android.os.CancellationSignal();cancelled.cancel();
            try { database.rawQueryWithFactory(null,"SELECT 1",null,null,cancelled);throw new AssertionError("cancelled query executed"); }
            catch(android.os.CancellationSignal.OperationCanceledException expected) {}
            helper.close();
            check(helper.getReadableDatabase().getVersion()==1,"database version did not survive reopen");
            helper.close();
            android.database.sqlite.SQLiteOpenHelper failing=new android.database.sqlite.SQLiteOpenHelper(this,"rollback.db",null,1) {
                public void onCreate(android.database.sqlite.SQLiteDatabase db) {
                    db.execSQL("CREATE TABLE partial (value TEXT)");
                    throw new IllegalArgumentException("seeded migration failure");
                }
                public void onUpgrade(android.database.sqlite.SQLiteDatabase db,int oldVersion,int newVersion) {}
            };
            try { failing.getWritableDatabase();throw new AssertionError("failed migration accepted"); }
            catch (IllegalArgumentException expected) {}
            try(android.database.sqlite.SQLiteDatabase reopened=android.database.sqlite.SQLiteDatabase.openFromContext(this,"rollback.db")) {
                check(reopened.getVersion()==0,"failed migration committed database version");
                try(android.database.Cursor rows=reopened.rawQuery("SELECT count(*) FROM sqlite_master WHERE name='partial'",null)) {
                    check(rows.moveToFirst() && rows.getInt(0)==0,"failed migration committed partial schema");
                }
            }
            System.out.println("PROBE DATABASE PASS typed factories/UTF-8/ICU/scalar reuse/bind clearing/serialized and nested transactions/version/reopen/migration rollback");
            android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(4,4,android.graphics.Bitmap.Config.ARGB_8888);
            new android.graphics.Canvas(bitmap).fillRect(1,1,3,3,0xff123456);
            check(bitmap.getPixel(1,1)==0xff123456 && bitmap.getPixel(0,0)==0,"Canvas must modify its target bitmap");
            android.graphics.Canvas textCanvas = new android.graphics.Canvas(240,64);
            textCanvas.drawText("ཀ 中文 😀",4,4,24,0xff000000);
            boolean hasInk=false;
            for(int pixel:textCanvas.getPixels()) if(pixel!=0) { hasInk=true;break; }
            check(hasInk,"Unicode host text must render under confinement");
            Map<String,String> status = new HashMap<>();
            for (String line : Files.readAllLines(Path.of("/proc/self/status"))) {
                int colon = line.indexOf(':');
                if (colon > 0) status.put(line.substring(0, colon), line.substring(colon + 1).trim());
            }
            check("1".equals(status.get("NoNewPrivs")), "NoNewPrivs");
            check("2".equals(status.get("Seccomp")), "seccomp filter");
            String uid = System.getProperty("probe.uid");
            for (String key : List.of("Uid", "Gid"))
                for (String id : status.get(key).split("\\s+")) check(id.equals(uid), key + " must equal app UID");
            String parentNamespace = System.getProperty("probe.parentNamespace");
            String mountNamespace = Files.readSymbolicLink(Path.of("/proc/self/ns/mnt")).toString();
            if (parentNamespace != null) {
                check(!mountNamespace.equals(parentNamespace), "mount namespace must differ");
                check(status.get("Groups").isEmpty(), "supplementary groups must be empty");
            }
            // Desktop bridge: the endpoint the launcher prepared must be
            // reachable from inside the confinement (Landlock read grant on
            // the socket directory) and answer the JSON protocol. The
            // launcher passes ISAKAI_BRIDGE; an empty value means "no
            // bridge" (headless probe runs).
            String bridgeDir = System.getenv("ISAKAI_BRIDGE");
            if (bridgeDir != null && !bridgeDir.isEmpty()) {
                java.nio.channels.SocketChannel channel = java.nio.channels.SocketChannel.open(java.net.StandardProtocolFamily.UNIX);
                channel.connect(java.net.UnixDomainSocketAddress.of(Path.of(bridgeDir, "bridge.sock")));
                channel.write(java.nio.ByteBuffer.wrap("{\"op\":\"caps\"}\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                java.nio.ByteBuffer reply = java.nio.ByteBuffer.allocate(512);
                StringBuilder line = new StringBuilder();
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
                while (System.nanoTime() < deadline && line.indexOf("\n") < 0) {
                    int read = channel.read(reply);
                    if (read < 0) break;
                    if (read == 0) { Thread.sleep(10); continue; }
                    line.append(new String(reply.array(), 0, read, java.nio.charset.StandardCharsets.UTF_8));
                    reply.clear();
                }
                channel.close();
                check(line.indexOf("{\"ok\":true") == 0, "desktop bridge round-trip: " + line);
                System.out.println("PROBE BRIDGE PASS endpoint=" + bridgeDir + " reply=" + line.toString().trim());
            }
            // Exercise a real subprocess through the inherited policy if available.
            if (Files.isExecutable(Path.of("/usr/bin/ffprobe"))) {
                Process decoder = new ProcessBuilder("/usr/bin/ffprobe", "-version").redirectErrorStream(true).start();
                String output = new String(decoder.getInputStream().readAllBytes());
                check(decoder.waitFor() == 0 && output.contains("ffprobe version"), "media subprocess: " + output);
            }
            System.out.println("PROBE PASS uid=" + uid + " nnp=1 seccomp=2 mount=" + mountNamespace
                + " data=rw stage=rw code=ro outside=denied other=denied");
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
