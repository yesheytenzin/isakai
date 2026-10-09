public class ArraysDriver {
    public static void main(String[] a) throws Exception {
        Class<?> c = Class.forName("org.isakai.oracle.Arrays");
        int[] arr = {3, 1, 4, 1, 5};
        System.out.println("sum=" + c.getMethod("sum", int[].class).invoke(null, (Object) arr));
        int[] f = (int[]) c.getMethod("fill", int.class, int.class).invoke(null, 4, 7);
        System.out.println("fill=" + java.util.Arrays.toString(f));
        Object o = new Object();
        Object[] oa = {o};
        System.out.println("eq=" + c.getMethod("firstEq", Object[].class, Object.class).invoke(null, oa, o));
        System.out.println("ne=" + c.getMethod("firstEq", Object[].class, Object.class).invoke(null, oa, new Object()));
    }
}
