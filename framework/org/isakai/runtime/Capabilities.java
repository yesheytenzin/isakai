package org.isakai.runtime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
/** Symbol-only diagnostics: no intent extras, app strings or user data. */
public final class Capabilities {
    private static final Set<String> reported=ConcurrentHashMap.newKeySet();
    private Capabilities() {}
    public static void unavailable(String api) {
        String site=StackWalker.getInstance().walk(frames -> frames
                .filter(frame -> !frame.getClassName().startsWith("java.")
                        && !frame.getClassName().startsWith("android.")
                        && !frame.getClassName().startsWith("org.isakai."))
                .findFirst().map(frame -> frame.getClassName()+"."+frame.getMethodName()
                        +" bci="+frame.getByteCodeIndex()).orElse("unknown"));
        String key=api+" at "+site;
        if(reported.add(key))System.err.println("ISAKAI_UNSUPPORTED "+key);
    }
}
