package cn.starry.narcissus.util;

public class TextUtil {

    public static String replaceChar(String messages) {
        return messages.replaceAll("&", "§");
    }

    /** Returns a minimal JSON text component preserving legacy section colors. */
    public static String toJsonComponent(String text) {
        return "{\"text\":\"" + escapeJson(text) + "\"}";
    }

    /** Returns a JSON text component with a legacy-compatible click event. */
    public static String toJsonClickableComponent(String text, String action, String value) {
        return "{\"text\":\"" + escapeJson(text) + "\",\"clickEvent\":{\"action\":\""
                + escapeJson(action) + "\",\"value\":\"" + escapeJson(value) + "\"}}";
    }

    /** Returns a component with a plain prefix and a clickable suffix child. */
    public static String toJsonClickableSuffixComponent(String text, String suffix,
                                                        String action, String value) {
        return "{\"text\":\"" + escapeJson(text) + "\",\"extra\":[{\"text\":\""
                + escapeJson(suffix) + "\",\"clickEvent\":{\"action\":\""
                + escapeJson(action) + "\",\"value\":\"" + escapeJson(value) + "\"}}]}";
    }

    private static String escapeJson(String text) {
        String value = text == null ? "" : text;
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    escaped.append(c);
                    break;
            }
        }
        return escaped.toString();
    }
}
