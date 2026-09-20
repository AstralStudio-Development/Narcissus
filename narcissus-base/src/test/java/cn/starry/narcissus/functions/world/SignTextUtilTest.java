package cn.starry.narcissus.functions.world;

import cn.starry.narcissus.functions.world.nbt.CompoundTag;
import cn.starry.narcissus.functions.world.nbt.StringTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignTextUtilTest
{
    @Test
    void colorsVersionAndConfiguredSignTextsBlue()
    {
        assertEquals("§91.21.11",
                SignTextUtil.replaceVersionText("{nar_ver}", "1.21.11"));
        assertEquals("{\"color\":\"blue\",\"text\":\"1.21.11\"}",
                SignTextUtil.replaceVersionText("{\"text\":\"{nar_ver}\"}", "1.21.11"));
        assertEquals("{\"text\":\"1.21.11\",\"color\":\"blue\"}",
                SignTextUtil.replaceVersionText("{\"text\":\"{nar_ver}\",\"color\":\"white\"}", "1.21.11"));
        assertEquals("§9XXCDHG4",
                SignTextUtil.replaceVersionText("XXCDHG4", "1.21.11"));
        assertEquals("{\"color\":\"blue\",\"text\":\"串串香小香肠\"}",
                SignTextUtil.replaceVersionText("{\"text\":\"串串香小香肠\"}", "1.21.11"));
    }

    @Test
    void rewritesModernSignPayloadInPlace()
    {
        CompoundTag data = new CompoundTag();
        data.put("front_text", new CompoundTag());
        CompoundTag frontText = data.get("front_text").toCompoundTag();
        frontText.put("messages", new StringTag("{\"text\":\"{nar_ver}\"}"));
        data.put("back_text", new StringTag("{\"text\":\"unchanged\"}"));
        CompoundTag directComponent = new CompoundTag();
        directComponent.put("text", new StringTag("{nar_ver}"));
        data.put("direct_component", directComponent);
        CompoundTag directHighlightedComponent = new CompoundTag();
        directHighlightedComponent.put("text", new StringTag("XXCDHG4"));
        data.put("direct_highlighted_component", directHighlightedComponent);
        data.put("highlighted_json", new StringTag("{\"text\":\"串串香小香肠\"}"));

        SignTextUtil.replacePlaceholders(data, "26.2");

        String messages = frontText.get("messages").toStringTag().getValue();
        assertTrue(messages.contains("26.2"));
        assertTrue(messages.contains("\"color\":\"blue\""));
        assertTrue(messages.contains("26.2"));
        assertEquals("{\"text\":\"unchanged\"}",
                data.get("back_text").toStringTag().getValue());
        assertEquals("26.2", directComponent.get("text").toStringTag().getValue());
        assertEquals("blue", directComponent.get("color").toStringTag().getValue());
        assertEquals("XXCDHG4",
                directHighlightedComponent.get("text").toStringTag().getValue());
        assertEquals("blue",
                directHighlightedComponent.get("color").toStringTag().getValue());
        assertEquals("{\"color\":\"blue\",\"text\":\"串串香小香肠\"}",
                data.get("highlighted_json").toStringTag().getValue());
    }
}
