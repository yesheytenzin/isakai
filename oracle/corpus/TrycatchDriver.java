public class TrycatchDriver {
    public static void main(String[] a) throws Exception {
        Class<?> c = Class.forName("org.isakai.oracle.TryCatch");
        System.out.println("div=" + c.getMethod("divOr", int.class, int.class).invoke(null, 20, 4));
        System.out.println("div0=" + c.getMethod("divOr", int.class, int.class).invoke(null, 20, 0));
        System.out.println("parse=" + c.getMethod("parseOr", String.class, int.class).invoke(null, "41", 0));
        System.out.println("parsebad=" + c.getMethod("parseOr", String.class, int.class).invoke(null, "xx", 7));
    }
}
