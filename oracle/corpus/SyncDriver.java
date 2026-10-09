public class SyncDriver {
    public static void main(String[] a) throws Exception {
        Class<?> c = Class.forName("org.isakai.oracle.Sync");
        System.out.println("len=" + c.getMethod("castLen", Object.class).invoke(null, "hello"));
        System.out.println("isStr=" + c.getMethod("isString", Object.class).invoke(null, "x"));
        System.out.println("isNotStr=" + c.getMethod("isString", Object.class).invoke(null, 42));
        try {
            c.getMethod("castLen", Object.class).invoke(null, 42);
            System.out.println("castFail=MISSING");
        } catch (Exception e) {
            System.out.println("castFail=" + e.getCause().getClass().getSimpleName());
        }
    }
}
