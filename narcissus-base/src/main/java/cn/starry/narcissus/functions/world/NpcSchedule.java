package cn.starry.narcissus.functions.world;

import java.time.LocalTime;

/** Defines NPC1's UTC+8 position and pose throughout the day. */
public final class NpcSchedule
{
    private static final LocalTime SLEEP_END = LocalTime.of(9, 0);
    private static final LocalTime SIT_START = LocalTime.of(14, 0);
    private static final LocalTime SIT_END = LocalTime.of(17, 0);

    private NpcSchedule()
    {
    }

    public static State stateAt(LocalTime time)
    {
        if (time == null)
            throw new IllegalArgumentException("Time cannot be null");
        if (time.isBefore(SLEEP_END))
            return State.SLEEPING;
        if (!time.isBefore(SIT_START) && time.isBefore(SIT_END))
            return State.SITTING;
        return State.STANDING;
    }

    public enum State
    {
        STANDING(0.5D, 88.0D, 5.5D, 0, false, 0, 0, 0),
        SLEEPING(-5.5D, 88.5D, 10.5D, 2, false, -6, 88, 10),
        SITTING(6.5D, 88.0D, 12.5D, 10, true, 0, 0, 0);

        private final double x;
        private final double y;
        private final double z;
        private final int modernPoseId;
        private final boolean seatRequired;
        private final int sleepingBlockX;
        private final int sleepingBlockY;
        private final int sleepingBlockZ;

        State(double x, double y, double z, int modernPoseId, boolean seatRequired,
              int sleepingBlockX, int sleepingBlockY, int sleepingBlockZ)
        {
            this.x = x;
            this.y = y;
            this.z = z;
            this.modernPoseId = modernPoseId;
            this.seatRequired = seatRequired;
            this.sleepingBlockX = sleepingBlockX;
            this.sleepingBlockY = sleepingBlockY;
            this.sleepingBlockZ = sleepingBlockZ;
        }

        public double x() { return this.x; }
        public double y() { return this.y; }
        public double z() { return this.z; }
        public float yaw() { return 180.0F; }
        public float pitch() { return 0.0F; }
        public int modernPoseId() { return this.modernPoseId; }
        public boolean requiresSeat() { return this.seatRequired; }
        public boolean isSleeping() { return this == SLEEPING; }
        public int sleepingBlockX() { return this.sleepingBlockX; }
        public int sleepingBlockY() { return this.sleepingBlockY; }
        public int sleepingBlockZ() { return this.sleepingBlockZ; }
    }
}
