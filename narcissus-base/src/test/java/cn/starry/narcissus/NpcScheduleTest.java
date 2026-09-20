package cn.starry.narcissus;

import cn.starry.narcissus.functions.world.NpcSchedule;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NpcScheduleTest
{
    @Test
    void scheduleUsesLeftClosedRightOpenBoundaries()
    {
        assertEquals(NpcSchedule.State.SLEEPING, NpcSchedule.stateAt(LocalTime.MIDNIGHT));
        assertEquals(NpcSchedule.State.SLEEPING, NpcSchedule.stateAt(LocalTime.of(8, 59, 59)));
        assertEquals(NpcSchedule.State.STANDING, NpcSchedule.stateAt(LocalTime.of(9, 0)));
        assertEquals(NpcSchedule.State.STANDING, NpcSchedule.stateAt(LocalTime.of(13, 59, 59)));
        assertEquals(NpcSchedule.State.SITTING, NpcSchedule.stateAt(LocalTime.of(14, 0)));
        assertEquals(NpcSchedule.State.SITTING, NpcSchedule.stateAt(LocalTime.of(16, 59, 59)));
        assertEquals(NpcSchedule.State.STANDING, NpcSchedule.stateAt(LocalTime.of(17, 0)));
        assertEquals(NpcSchedule.State.STANDING, NpcSchedule.stateAt(LocalTime.of(23, 59, 59)));
    }

    @Test
    void statesExposeRequiredPositionsAndOrientation()
    {
        assertState(NpcSchedule.State.SLEEPING, -5.5D, 88.5D, 10.5D, 2);
        assertState(NpcSchedule.State.SITTING, 6.5D, 88.0D, 12.5D, 10);
        assertState(NpcSchedule.State.STANDING, 0.5D, 88.0D, 5.5D, 0);
    }

    private static void assertState(NpcSchedule.State state, double x, double y, double z,
                                    int modernPoseId)
    {
        assertEquals(x, state.x());
        assertEquals(y, state.y());
        assertEquals(z, state.z());
        assertEquals(180.0F, state.yaw());
        assertEquals(0.0F, state.pitch());
        assertEquals(modernPoseId, state.modernPoseId());
    }
}
