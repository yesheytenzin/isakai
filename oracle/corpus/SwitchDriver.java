public class SwitchDriver {
    public static void main(String[] a) throws Exception {
        Class<?> c = Class.forName("org.isakai.oracle.Switch");
        System.out.println("g1=" + c.getMethod("grade", int.class).invoke(null, 1));
        System.out.println("g3=" + c.getMethod("grade", int.class).invoke(null, 3));
        System.out.println("g9=" + c.getMethod("grade", int.class).invoke(null, 9));
        System.out.println("c10=" + c.getMethod("classify", int.class).invoke(null, 10));
        System.out.println("c100=" + c.getMethod("classify", int.class).invoke(null, 100));
        System.out.println("c50=" + c.getMethod("classify", int.class).invoke(null, 50));
        System.out.println("tab=" + java.util.Arrays.toString((int[]) c.getMethod("table").invoke(null)));
    }
}
