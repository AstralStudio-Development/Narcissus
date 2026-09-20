package cn.starry.narcissus;

import cn.starry.narcissus.functions.world.WorldTimeService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldTimeServiceTest {

    @Test
    void utc8ClockFacesMapLinearlyToMinecraftDayTime() {
        assertEquals(18000L, WorldTimeService.dayTimeFor(LocalTime.MIDNIGHT));
        assertEquals(0L, WorldTimeService.dayTimeFor(LocalTime.of(6, 0)));
        assertEquals(2000L, WorldTimeService.dayTimeFor(LocalTime.of(8, 0)));
        assertEquals(2500L, WorldTimeService.dayTimeFor(LocalTime.of(8, 30)));
        assertEquals(8000L, WorldTimeService.dayTimeFor(LocalTime.of(14, 0)));
        assertEquals(12000L, WorldTimeService.dayTimeFor(LocalTime.of(18, 0)));
        assertEquals(15000L, WorldTimeService.dayTimeFor(LocalTime.of(21, 0)));

        assertEquals(LocalTime.MIDNIGHT, WorldTimeService.localTimeForDayTime(18000L));
        assertEquals(LocalTime.of(6, 0), WorldTimeService.localTimeForDayTime(0L));
        assertEquals(LocalTime.of(8, 0), WorldTimeService.localTimeForDayTime(2000L));
    }

    @Test
    void currentSnapshotUsesFixedUtc8RatherThanHostTimezone() {
        WorldTimeService service = new WorldTimeService(
                Clock.fixed(Instant.parse("2026-08-06T00:00:00Z"), ZoneOffset.UTC));

        WorldTimeService.Snapshot snapshot = service.currentTime();

        assertEquals(ZoneOffset.ofHours(8), snapshot.utc8DateTime().getOffset());
        assertEquals(LocalTime.of(8, 0), snapshot.utc8DateTime().toLocalTime());
        assertEquals(2000L, snapshot.dayTime());
        assertEquals(snapshot.gameTime(), snapshot.worldClockTime());
        assertEquals(2000L, Math.floorMod(snapshot.worldClockTime(), WorldTimeService.TICKS_PER_DAY));
    }

    @Test
    void totalGameTimeStaysMonotonicAcrossSunriseAndUtc8Midnight() {
        WorldTimeService.Snapshot beforeSunrise = snapshotAt("2026-08-05T21:59:59Z");
        WorldTimeService.Snapshot sunrise = snapshotAt("2026-08-05T22:00:00Z");
        assertTrue(sunrise.gameTime() > beforeSunrise.gameTime());
        assertEquals(23999L, beforeSunrise.dayTime());
        assertEquals(0L, sunrise.dayTime());

        WorldTimeService.Snapshot beforeMidnight = snapshotAt("2026-08-05T15:59:59Z");
        WorldTimeService.Snapshot midnight = snapshotAt("2026-08-05T16:00:00Z");
        assertTrue(midnight.gameTime() > beforeMidnight.gameTime());
        assertEquals(17999L, beforeMidnight.dayTime());
        assertEquals(18000L, midnight.dayTime());
    }

    @Test
    void personalTimeAcceptsClockFacesAndRawTicks() {
        WorldTimeService service = new WorldTimeService(
                Clock.fixed(Instant.parse("2026-08-06T00:00:00Z"), ZoneOffset.UTC));

        assertEquals(OptionalLong.of(18000L), service.parseDayTime("00:00"));
        assertEquals(OptionalLong.of(2000L), service.parseDayTime("8:00"));
        assertEquals(OptionalLong.of(15000L), service.parseDayTime("21:00"));
        assertEquals(OptionalLong.of(23999L), service.parseDayTime("23999"));
        assertEquals(OptionalLong.empty(), service.parseDayTime("24:00"));
        assertEquals(OptionalLong.empty(), service.parseDayTime("08:60"));
        assertEquals(OptionalLong.empty(), service.parseDayTime("24000"));
        assertEquals(OptionalLong.empty(), service.parseDayTime("noon"));

        WorldTimeService.Snapshot snapshot = service.atDayTime(18000L);
        assertEquals(18000L, snapshot.dayTime());
        assertEquals(18000L, Math.floorMod(snapshot.worldClockTime(), WorldTimeService.TICKS_PER_DAY));
        assertEquals(LocalTime.MIDNIGHT, snapshot.utc8DateTime().toLocalTime());
        assertEquals("00:00", WorldTimeService.formatDayTime(snapshot.dayTime()));
    }

    @Test
    void hourBoundaryTriggersOnlyOnceAndReportsNarrowWakeupDelay() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-06T01:59:59.500Z"));
        WorldTimeService service = new WorldTimeService(clock);

        assertFalse(service.shouldBroadcastNow());
        assertEquals(500L, service.millisUntilNextHour());

        clock.advance(Duration.ofMillis(499L));
        assertFalse(service.shouldBroadcastNow());
        assertEquals(1L, service.millisUntilNextHour());

        clock.advance(Duration.ofMillis(1L));
        assertTrue(service.shouldBroadcastNow());
        assertFalse(service.shouldBroadcastNow());
        assertEquals(Duration.ofHours(1L).toMillis(), service.millisUntilNextHour());
    }

    private static WorldTimeService.Snapshot snapshotAt(String instant) {
        return new WorldTimeService(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC)).currentTime();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(this.instant, zone);
        }

        @Override
        public Instant instant() {
            return this.instant;
        }
    }
}
