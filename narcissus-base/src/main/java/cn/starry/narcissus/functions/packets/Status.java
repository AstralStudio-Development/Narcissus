package cn.starry.narcissus.functions.packets;

/**
 * Created by Rigner on 29/08/16 for project Limbo.
 * All rights reserved.
 */
public enum Status
{
    HANDSHAKE,
    STATUS,
    LOGIN,
    /**
     * Modern configuration phase (1.20.2+ / protocol 764+). Entered after the
     * client acknowledges Login Success and left when both sides exchange Finish
     * Configuration. Legacy protocols (e.g. v47) never use this state.
     */
    CONFIGURATION,
    PLAY
}
