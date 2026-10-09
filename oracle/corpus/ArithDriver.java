public class ArithDriver {
    public static void main(String[] a) throws Exception {
        Class<?> c = Class.forName("org.isakai.oracle.Arith");
        System.out.println("add=" + c.getMethod("add", int.class, int.class).invoke(null, 17, 25));
        System.out.println("abs1=" + c.getMethod("abs", int.class).invoke(null, -7));
        System.out.println("abs2=" + c.getMethod("abs", int.class).invoke(null, 9));
        System.out.println("fib=" + c.getMethod("fib", int.class).invoke(null, 10));
    }
}
