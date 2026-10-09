public class WideDriver {
    public static void main(String[] a) throws Exception {
        Class<?> c = Class.forName("org.isakai.oracle.Wide");
        System.out.println("ladd=" + c.getMethod("ladd", long.class, long.class).invoke(null, 1L<<40, 5L));
        System.out.println("dmul=" + c.getMethod("dmul", double.class, double.class).invoke(null, 2.5, 4.0));
        System.out.println("fma=" + c.getMethod("fma", float.class, float.class, float.class).invoke(null, 2f, 3f, 4f));
        System.out.println("conv=" + c.getMethod("convRoundTrip", long.class).invoke(null, (1L<<33)|7L));
        System.out.println("consts=" + Long.toHexString((long)c.getMethod("consts").invoke(null)));
        System.out.println("neg=" + c.getMethod("negAll", long.class, double.class).invoke(null, 10L, 2.9));
    }
}
