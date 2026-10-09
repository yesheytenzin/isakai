public class WidedoubleDriver {
    public static void main(String[] a) throws Exception {
        Class<?> c = Class.forName("org.isakai.oracle.Widedouble");
        System.out.println("dsum=" + c.getMethod("fillRead", double.class, double.class).invoke(null, 1.5, 2.25));
        System.out.println("lsum=" + c.getMethod("fillReadLong", long.class, long.class).invoke(null, 1L << 40, 7L));
        System.out.println("alias=" + c.getMethod("aliasRead", double.class).invoke(null, -3.75));
    }
}
