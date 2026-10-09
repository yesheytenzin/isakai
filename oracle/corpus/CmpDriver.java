public class CmpDriver {
    public static void main(String[] a) throws Exception {
        Class<?> c = Class.forName("org.isakai.oracle.Cmp");
        float nan = Float.NaN;
        System.out.println("cmpl=" + c.getMethod("cmpFloat", float.class, float.class).invoke(null, 1.5f, 2.5f));
        System.out.println("cmpl_nan=" + c.getMethod("cmpFloat", float.class, float.class).invoke(null, nan, 1.0f));
        System.out.println("cmpg_nan=" + c.getMethod("cmpFloatG", float.class, float.class).invoke(null, nan, 1.0f));
        System.out.println("cmpl_negzero=" + c.getMethod("cmpFloat", float.class, float.class).invoke(null, -0.0f, 0.0f));
        System.out.println("cmpd=" + c.getMethod("cmpDouble", double.class, double.class).invoke(null, 3.0, 2.0));
        System.out.println("signf=" + c.getMethod("signF", float.class).invoke(null, -1.5f));
        System.out.println("cmpdz=" + c.getMethod("cmpDoubleZero", double.class).invoke(null, -2.5));
        System.out.println("cmplong=" + c.getMethod("cmpLong", long.class, long.class).invoke(null, Long.MIN_VALUE, Long.MAX_VALUE));
        System.out.println("cmplong_eq=" + c.getMethod("cmpLong", long.class, long.class).invoke(null, 7L, 7L));
    }
}
