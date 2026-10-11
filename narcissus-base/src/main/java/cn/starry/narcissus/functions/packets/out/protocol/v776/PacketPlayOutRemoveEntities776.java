package cn.starry.narcissus.functions.packets.out.protocol.v776;

import cn.starry.narcissus.functions.packets.out.protocol.v774.PacketPlayOutRemoveEntities774;

/** 26.2 changes the registered id, not the entity-list body. */
public final class PacketPlayOutRemoveEntities776 extends PacketPlayOutRemoveEntities774 {
    public PacketPlayOutRemoveEntities776(int... entityIds) { super(entityIds); }
}
