package cn.starry.narcissus.util;

/** Human-readable Minecraft versions for the native protocol ids. */
public final class ProtocolVersionNames
{
    private ProtocolVersionNames()
    {
    }

    public static String forProtocol(int protocolId)
    {
        return switch (protocolId)
        {
            case 47 -> "1.8";
            case 774 -> "1.21.11";
            case 775 -> "26.1 / 26.1.1 / 26.1.2";
            case 776 -> "26.2";
            case 777 -> "26.3";
            default -> Integer.toString(protocolId);
        };
    }
}
