package cn.starry.narcissus.functions.world;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.OptionalLong;

/** Maps fixed UTC+8 civil time onto Minecraft's 24000-tick day. */
public final class WorldTimeService
{
    public static final ZoneOffset UTC_PLUS_EIGHT = ZoneOffset.ofHours(8);
    public static final long TICKS_PER_DAY = 24000L;
    private static final long MIDNIGHT_DAY_TIME = 18000L;
    private static final long NANOS_PER_DAY = Duration.ofDays(1).toNanos();

    private final Clock clock;
    private long lastBroadcastHour;

    public WorldTimeService()
    {
        this(Clock.systemUTC());
    }

    public WorldTimeService(Clock clock)
    {
        if (clock == null)
            throw new IllegalArgumentException("Clock cannot be null");
        this.clock = clock;
        this.lastBroadcastHour = hourKey(this.nowUtc8());
    }

    /** Returns the current real-world UTC+8 time as a frozen Minecraft clock state. */
    public Snapshot currentTime()
    {
        OffsetDateTime now = this.nowUtc8();
        long localTicks = now.toLocalTime().toNanoOfDay() * TICKS_PER_DAY / NANOS_PER_DAY;
        long totalTicks = now.toLocalDate().toEpochDay() * TICKS_PER_DAY
                + localTicks + MIDNIGHT_DAY_TIME;
        return new Snapshot(totalTicks, totalTicks, Math.floorMod(totalTicks, TICKS_PER_DAY), now);
    }

    /** Creates a per-player test snapshot while preserving the current game-time counter. */
    public Snapshot atDayTime(long requestedDayTime)
    {
        long dayTime = Math.floorMod(requestedDayTime, TICKS_PER_DAY);
        Snapshot current = this.currentTime();
        long dayBase = Math.floorDiv(current.worldClockTime(), TICKS_PER_DAY) * TICKS_PER_DAY;
        OffsetDateTime displayTime = current.utc8DateTime().with(localTimeForDayTime(dayTime));
        return new Snapshot(current.gameTime(), dayBase + dayTime, dayTime, displayTime);
    }

    /** Parses either HH:mm UTC+8 civil time or a raw Minecraft tick in [0, 23999]. */
    public OptionalLong parseDayTime(String input)
    {
        if (input == null)
            return OptionalLong.empty();
        String value = input.trim();
        if (value.isEmpty())
            return OptionalLong.empty();

        int colon = value.indexOf(':');
        if (colon >= 0)
        {
            if (colon == 0 || colon != value.lastIndexOf(':') || colon == value.length() - 1)
                return OptionalLong.empty();
            try
            {
                int hour = Integer.parseInt(value.substring(0, colon));
                int minute = Integer.parseInt(value.substring(colon + 1));
                if (hour < 0 || hour > 23 || minute < 0 || minute > 59)
                    return OptionalLong.empty();
                return OptionalLong.of(dayTimeFor(LocalTime.of(hour, minute)));
            }
            catch (NumberFormatException ignored)
            {
                return OptionalLong.empty();
            }
        }

        try
        {
            long ticks = Long.parseLong(value);
            return ticks >= 0 && ticks < TICKS_PER_DAY
                    ? OptionalLong.of(ticks)
                    : OptionalLong.empty();
        }
        catch (NumberFormatException ignored)
        {
            return OptionalLong.empty();
        }
    }

    /** Converts a UTC+8 clock face to the equivalent Minecraft day-time tick. */
    public static long dayTimeFor(LocalTime time)
    {
        if (time == null)
            throw new IllegalArgumentException("Time cannot be null");
        long localTicks = time.toNanoOfDay() * TICKS_PER_DAY / NANOS_PER_DAY;
        return Math.floorMod(localTicks + MIDNIGHT_DAY_TIME, TICKS_PER_DAY);
    }

    /** Converts a Minecraft day-time tick back to its UTC+8 clock-face time. */
    public static LocalTime localTimeForDayTime(long dayTime)
    {
        long localTicks = Math.floorMod(dayTime - MIDNIGHT_DAY_TIME, TICKS_PER_DAY);
        long nanos = localTicks * NANOS_PER_DAY / TICKS_PER_DAY;
        return LocalTime.ofNanoOfDay(nanos);
    }

    public static String formatDayTime(long dayTime)
    {
        LocalTime time = localTimeForDayTime(dayTime);
        return String.format(Locale.ROOT, "%02d:%02d", time.getHour(), time.getMinute());
    }

    /** Returns true once when the observed UTC+8 civil hour changes. */
    public synchronized boolean shouldBroadcastNow()
    {
        long currentHour = hourKey(this.nowUtc8());
        if (currentHour == this.lastBroadcastHour)
            return false;
        this.lastBroadcastHour = currentHour;
        return true;
    }

    /** Milliseconds until the next exact UTC+8 hour boundary, never less than one. */
    public long millisUntilNextHour()
    {
        OffsetDateTime now = this.nowUtc8();
        OffsetDateTime nextHour = now.truncatedTo(ChronoUnit.HOURS).plusHours(1L);
        return Math.max(1L, Duration.between(now, nextHour).toMillis());
    }

    private OffsetDateTime nowUtc8()
    {
        return OffsetDateTime.ofInstant(this.clock.instant(), UTC_PLUS_EIGHT);
    }

    private static long hourKey(OffsetDateTime time)
    {
        return time.toLocalDate().toEpochDay() * 24L + time.getHour();
    }

    /** Protocol-neutral values used by legacy, 1.21.11 and 26.2 time packets. */
    public record Snapshot(long gameTime, long worldClockTime, long dayTime,
                           OffsetDateTime utc8DateTime)
    {
    }
}
