public class WidefillDriver {
    public static void main(String[] a) throws Exception {
        Class<?> c = Class.forName("org.isakai.oracle.Widefill");
        System.out.println("long=" + c.getMethod("longFill").invoke(null));
        System.out.println("double=" + c.getMethod("doubleFill").invoke(null));
        System.out.println("sum18=" + c.getMethod("manyArgs").invoke(null));
    }
}
