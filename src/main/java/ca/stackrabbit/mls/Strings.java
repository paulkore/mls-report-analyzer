package ca.stackrabbit.mls;

public class Strings {

    public static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    public static String join(String[] values, String delimiter) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String str : values) {
            if (first) {
                first = false;
            } else {
                sb.append(delimiter);
            }
            sb.append(str);
        }
        return sb.toString();
    }

}
