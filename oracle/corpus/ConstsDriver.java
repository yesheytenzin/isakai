public class ConstsDriver {
    public static void main(String[] a) throws Exception {
        Class<?> c = Class.forName("org.isakai.oracle.Consts");
        System.out.println("small=" + c.getMethod("small").invoke(null));
        System.out.println("negsmall=" + c.getMethod("negSmall").invoke(null));
        System.out.println("justover=" + c.getMethod("justOver").invoke(null));
        System.out.println("justunder=" + c.getMethod("justUnder").invoke(null));
        System.out.println("big=" + c.getMethod("big").invoke(null));
        System.out.println("negbig=" + c.getMethod("negBig").invoke(null));
        System.out.println("min=" + c.getMethod("minInt").invoke(null));
        System.out.println("max=" + c.getMethod("maxInt").invoke(null));
        System.out.println("h16=" + c.getMethod("high16").invoke(null));
        System.out.println("h16neg=" + c.getMethod("high16Neg").invoke(null));
    }
}
