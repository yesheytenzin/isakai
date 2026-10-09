public class Goto32Driver {
    public static void main(String[] a) throws Exception {
        Class<?> c = Class.forName("org.isakai.oracle.Goto32");
        System.out.println("skip=" + c.getMethod("skip").invoke(null));
        System.out.println("back=" + c.getMethod("skipBack", int.class).invoke(null, 5));
    }
}
