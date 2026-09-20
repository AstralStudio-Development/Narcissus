package cn.starry.narcissus.functions.world;

import cn.starry.narcissus.functions.modern.ModernBlockStateMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Shared schematic-local palette of canonical modern block-state strings. */
public final class ModernBlockPalette
{
    private final List<String> states = new ArrayList<>();
    private final Map<String, Integer> indices = new HashMap<>();

    public ModernBlockPalette()
    {
        this.indexOf("minecraft:air");
    }

    public int indexOf(String state)
    {
        String canonical = ModernBlockStateMap.canonicalize(state);
        Integer existing = this.indices.get(canonical);
        if (existing != null)
            return existing;
        int index = this.states.size();
        this.states.add(canonical);
        this.indices.put(canonical, index);
        return index;
    }

    public String stateAt(int index)
    {
        if (index < 0 || index >= this.states.size())
            return "minecraft:air";
        return this.states.get(index);
    }

    public int size()
    {
        return this.states.size();
    }

    public List<String> getStates()
    {
        return Collections.unmodifiableList(this.states);
    }
}
