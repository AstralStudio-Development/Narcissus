package cn.starry.narcissus.functions.world;

import cn.starry.narcissus.functions.world.nbt.CompoundTag;
import cn.starry.narcissus.functions.world.nbt.ListTag;
import cn.starry.narcissus.functions.world.nbt.NamedTag;
import cn.starry.narcissus.functions.world.nbt.StringTag;

import java.util.regex.Pattern;

/** Rewrites and highlights configured legacy and modern sign text components. */
public final class SignTextUtil
{
    private static final String PLACEHOLDER = "{nar_ver}";
    private static final String[] HIGHLIGHTED_TEXTS = {"XXCDHG4", "串串香小香肠"};
    private static final String BLUE = "blue";
    private static final Pattern COLOR_FIELD = Pattern.compile(
            "\\\"color\\\"\\s*:\\s*\\\"[^\\\"]*\\\"", Pattern.CASE_INSENSITIVE);

    private SignTextUtil()
    {
    }

    /** Replaces one sign line and applies blue to the version or configured text. */
    public static String replaceVersionText(String value, String version)
    {
        if (value == null || version == null || !shouldHighlight(value))
            return value;

        String replaced = value.replace(PLACEHOLDER, escapeJson(version));
        String trimmed = replaced.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}"))
        {
            if (COLOR_FIELD.matcher(replaced).find())
                return COLOR_FIELD.matcher(replaced).replaceFirst("\\\"color\\\":\\\"" + BLUE + "\\\"");
            return "{\"color\":\"" + BLUE + "\"," + replaced.substring(1);
        }
        // Legacy sign text uses section formatting; modern direct NBT components
        // are handled in replaceTag below without embedding formatting codes.
        return "§9" + replaced;
    }

    /** Recursively rewrites placeholders and highlights configured sign text. */
    public static void replacePlaceholders(CompoundTag data, String version)
    {
        if (data == null || version == null)
            return;
        for (NamedTag child : data.getTags())
            replaceTag(child, version);
    }

    private static void replaceTag(NamedTag tag, String version)
    {
        if (tag instanceof StringTag stringTag)
        {
            stringTag.setValue(replaceVersionText(stringTag.getValue(), version));
        }
        else if (tag instanceof CompoundTag compoundTag)
        {
            NamedTag text = compoundTag.get("text");
            boolean handledDirectText = false;
            if (text instanceof StringTag textTag && shouldHighlight(textTag.getValue()))
            {
                textTag.setValue(textTag.getValue().replace(PLACEHOLDER, version));
                compoundTag.put("color", new StringTag(BLUE));
                handledDirectText = true;
            }
            for (NamedTag child : compoundTag.getTags())
            {
                if (!handledDirectText || child != text)
                    replaceTag(child, version);
            }
        }
        else if (tag instanceof ListTag listTag)
        {
            for (NamedTag child : listTag.getTags())
                replaceTag(child, version);
        }
    }

    private static boolean shouldHighlight(String value)
    {
        if (value.contains(PLACEHOLDER))
            return true;
        for (String highlightedText : HIGHLIGHTED_TEXTS)
        {
            if (value.contains(highlightedText))
                return true;
        }
        return false;
    }

    private static String escapeJson(String value)
    {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\b", "\\b")
                .replace("\f", "\\f");
    }
}
