public class MovesDriver {
    public static void main(String[] a) throws Exception {
        Class<?> c = Class.forName("org.isakai.oracle.Moves");
        System.out.println("from16=" + c.getMethod("from16").invoke(null));
        System.out.println("objfrom16=" + c.getMethod("objFrom16").invoke(null));
        System.out.println("widefrom16=" + c.getMethod("wideFrom16").invoke(null));
        System.out.println("bool=" + c.getMethod("boolArr").invoke(null));
        System.out.println("byte=" + c.getMethod("byteArr").invoke(null));
        System.out.println("char=" + c.getMethod("charArr").invoke(null));
        System.out.println("short=" + c.getMethod("shortArr").invoke(null));
    }
}
