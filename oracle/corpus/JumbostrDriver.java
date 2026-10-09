public class JumbostrDriver {
    public static void main(String[] a) throws Exception {
        Class<?> c = Class.forName("org.isakai.oracle.Jumbostr");
        System.out.println("first=" + c.getMethod("pick", int.class).invoke(null, 0));
        System.out.println("mid=" + c.getMethod("pick", int.class).invoke(null, 150));
        System.out.println("high=" + c.getMethod("pick", int.class).invoke(null, 299));
        System.out.println("def=" + c.getMethod("pick", int.class).invoke(null, 9999));
        System.out.println("pinned=" + c.getMethod("pinned").invoke(null));
    }
}
