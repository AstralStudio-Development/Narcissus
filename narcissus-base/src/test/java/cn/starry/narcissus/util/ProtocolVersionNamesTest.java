package cn.starry.narcissus.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProtocolVersionNamesTest
{
    @Test
    void mapsSupportedProtocolIdsToDisplayVersions()
    {
        assertEquals("1.8", ProtocolVersionNames.forProtocol(47));
        assertEquals("1.21.11", ProtocolVersionNames.forProtocol(774));
        assertEquals("26.2", ProtocolVersionNames.forProtocol(776));
        assertEquals("12345", ProtocolVersionNames.forProtocol(12345));
    }
}
