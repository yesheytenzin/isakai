public class ConstclassDriver {
    public static void main(String[] a) throws Exception {
        Class<?> c = Class.forName("org.isakai.oracle.Constclass");
        System.out.println("string=" + ((Class<?>) c.getMethod("stringClass").invoke(null)).getName());
        System.out.println("int=" + ((Class<?>) c.getMethod("intClass").invoke(null)).getName());
        System.out.println("array=" + ((Class<?>) c.getMethod("arrayClass").invoke(null)).getName());
        System.out.println("bool=" + ((Class<?>) c.getMethod("boolClass").invoke(null)).getName());
    }
}
