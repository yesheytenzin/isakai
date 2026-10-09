public class FilledarrayDriver {
    public static void main(String[] a) throws Exception {
        Class<?> c = Class.forName("org.isakai.oracle.Filledarray");
        System.out.println("ints=" + java.util.Arrays.toString((int[]) c.getMethod("ints", int.class, int.class, int.class).invoke(null, 3, 1, 4)));
        System.out.println("range=" + java.util.Arrays.toString((int[]) c.getMethod("range", int.class, int.class, int.class, int.class).invoke(null, 5, 6, 7, 8)));
        System.out.println("strings=" + java.util.Arrays.toString((Object[]) c.getMethod("strings", String.class, String.class).invoke(null, "a", "b")));
        System.out.println("floats=" + java.util.Arrays.toString((float[]) c.getMethod("floats", float.class, float.class).invoke(null, 1.5f, 2.5f)));
    }
}
