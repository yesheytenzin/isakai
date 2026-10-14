public final class RegreuseDriver {
    public static void main(String[] args) throws Exception {
        java.lang.reflect.Method method = Class.forName("org.isakai.oracle.Regreuse")
                .getMethod("sentinel", int.class, boolean.class);
        for (int value : new int[]{-1, 0, 1, 0x7f0903ca}) {
            System.out.println(value + ":" + method.invoke(null, value, false));
        }
        java.lang.reflect.Method references = method.getDeclaringClass()
                .getMethod("references", Object.class, Object.class);
        Object shared = new Object();
        System.out.println("same:" + references.invoke(null, shared, shared));
        System.out.println("different:" + references.invoke(null, shared, new Object()));
        System.out.println("nulls:" + references.invoke(null, new Object[]{null, null}));
        System.out.println("one-null:" + references.invoke(null, shared, null));
        try {
            method.invoke(null, 42, true);
            throw new AssertionError("throwing path did not throw");
        } catch (java.lang.reflect.InvocationTargetException expected) {
            System.out.println("throw:" + expected.getCause().getClass().getName());
        }
    }
}
