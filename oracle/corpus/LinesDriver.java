public class LinesDriver {
    public static void main(String[] a) throws Exception {
        Class<?> c = Class.forName("org.isakai.oracle.Lines");
        System.out.println("add=" + c.getMethod("add", int.class, int.class).invoke(null, 3, 4));
        System.out.println("pick_neg=" + c.getMethod("pick", int.class).invoke(null, -5));
        System.out.println("pick_pos=" + c.getMethod("pick", int.class).invoke(null, 5));
        System.out.println("loop=" + c.getMethod("loopSum", int.class).invoke(null, 10));
    }
}
