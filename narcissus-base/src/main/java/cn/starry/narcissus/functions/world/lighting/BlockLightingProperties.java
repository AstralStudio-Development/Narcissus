package cn.starry.narcissus.functions.world.lighting;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable, dependency-free lighting properties extracted from an official server registry.
 * State ids are the exact protocol global ids for modern versions and {@code id << 4 | meta}
 * for the complete 4096-entry legacy v47 table.
 */
public final class BlockLightingProperties
{
    public static final int DOWN = 0;
    public static final int UP = 1;
    public static final int NORTH = 2;
    public static final int SOUTH = 3;
    public static final int WEST = 4;
    public static final int EAST = 5;

    private static final int MAGIC = 0x4E4C4731;
    private static final int FORMAT = 1;
    private static final int[] OPPOSITE = {UP, DOWN, SOUTH, NORTH, EAST, WEST};
    private static final double EPSILON = 1.0E-9D;
    private static final Map<String, BlockLightingProperties> CACHE = new HashMap<>();

    private final String versionDir;
    private final String sourceVersion;
    private final String sourceHash;
    private final byte[] emission;
    private final byte[] dampening;
    private final byte[] flags;
    private final int[][] faceShapeIds;
    private final FaceShape[] shapes;
    /** 0=unknown, 1=open, 2=occluded. Shape union is symmetric. */
    private final byte[][] occlusionCache;

    private BlockLightingProperties(String versionDir, String sourceVersion, String sourceHash,
                                    byte[] emission, byte[] dampening, byte[] flags,
                                    int[][] faceShapeIds, FaceShape[] shapes)
    {
        this.versionDir = versionDir;
        this.sourceVersion = sourceVersion;
        this.sourceHash = sourceHash;
        this.emission = emission;
        this.dampening = dampening;
        this.flags = flags;
        this.faceShapeIds = faceShapeIds;
        this.shapes = shapes;
        this.occlusionCache = new byte[shapes.length][shapes.length];
    }

    public static synchronized BlockLightingProperties load(String versionDir, String expectedVersion,
                                                            int expectedStateCount)
    {
        BlockLightingProperties cached = CACHE.get(versionDir);
        if (cached != null)
        {
            if (!expectedVersion.equals(cached.sourceVersion) || expectedStateCount != cached.stateCount())
                throw new IllegalStateException("Cached lighting profile mismatch for " + versionDir);
            return cached;
        }
        String path = "/registry/" + versionDir + "/lighting.bin";
        try (InputStream resource = BlockLightingProperties.class.getResourceAsStream(path))
        {
            if (resource == null)
                throw new IllegalStateException("Required lighting fixture is missing: " + path);
            try (DataInputStream input = new DataInputStream(new BufferedInputStream(resource)))
            {
                int magic = input.readInt();
                int format = input.readInt();
                if (magic != MAGIC || format != FORMAT)
                    throw new IllegalStateException("Unsupported lighting fixture header for " + path);
                String sourceVersion = input.readUTF();
                String sourceHash = input.readUTF();
                if (!expectedVersion.equals(sourceVersion))
                    throw new IllegalStateException("Lighting fixture version mismatch for " + versionDir
                            + ": expected " + expectedVersion + ", got " + sourceVersion);

                int stateCount = input.readInt();
                int shapeCount = input.readInt();
                if (stateCount != expectedStateCount)
                    throw new IllegalStateException("Lighting fixture state count mismatch for " + versionDir
                            + ": expected " + expectedStateCount + ", got " + stateCount);
                if (shapeCount < 1 || shapeCount > 65536)
                    throw new IllegalStateException("Invalid lighting shape count " + shapeCount + " in " + path);

                FaceShape[] shapes = new FaceShape[shapeCount];
                for (int shapeId = 0; shapeId < shapeCount; shapeId++)
                {
                    int rectangleCount = input.readInt();
                    if (rectangleCount < 0 || rectangleCount > 4096)
                        throw new IllegalStateException("Invalid rectangle count in " + path + ": " + rectangleCount);
                    double[] rectangles = new double[rectangleCount * 4];
                    for (int coordinate = 0; coordinate < rectangles.length; coordinate++)
                    {
                        double value = Double.longBitsToDouble(input.readLong());
                        if (!Double.isFinite(value) || value < -EPSILON || value > 1.0D + EPSILON)
                            throw new IllegalStateException("Invalid face coordinate in " + path + ": " + value);
                        rectangles[coordinate] = clamp(value);
                    }
                    shapes[shapeId] = new FaceShape(rectangles);
                }

                byte[] emission = new byte[stateCount];
                byte[] dampening = new byte[stateCount];
                byte[] flags = new byte[stateCount];
                int[][] faceShapeIds = new int[stateCount][6];
                for (int stateId = 0; stateId < stateCount; stateId++)
                {
                    int lightEmission = input.readUnsignedByte();
                    int lightDampening = input.readUnsignedByte();
                    int stateFlags = input.readUnsignedByte();
                    if (lightEmission > 15 || lightDampening > 15 || (stateFlags & ~3) != 0)
                        throw new IllegalStateException("Invalid lighting property for state " + stateId
                                + " in " + path);
                    emission[stateId] = (byte) lightEmission;
                    dampening[stateId] = (byte) lightDampening;
                    flags[stateId] = (byte) stateFlags;
                    for (int direction = 0; direction < 6; direction++)
                    {
                        int shapeId = input.readInt();
                        if (shapeId < 0 || shapeId >= shapeCount)
                            throw new IllegalStateException("Invalid face shape id for state " + stateId
                                    + " in " + path + ": " + shapeId);
                        faceShapeIds[stateId][direction] = shapeId;
                    }
                }
                if (input.read() != -1)
                    throw new IllegalStateException("Trailing data in lighting fixture " + path);
                BlockLightingProperties loaded = new BlockLightingProperties(versionDir,
                        sourceVersion, sourceHash, emission, dampening, flags, faceShapeIds, shapes);
                CACHE.put(versionDir, loaded);
                return loaded;
            }
        }
        catch (IOException ex)
        {
            throw new IllegalStateException("Unable to load required lighting fixture " + path, ex);
        }
    }

    public int stateCount()
    {
        return this.emission.length;
    }

    public int emission(int stateId)
    {
        return this.emission[checkedState(stateId)] & 0xFF;
    }

    public int dampening(int stateId)
    {
        return this.dampening[checkedState(stateId)] & 0xFF;
    }

    public boolean canOcclude(int stateId)
    {
        return (this.flags[checkedState(stateId)] & 1) != 0;
    }

    public boolean usesShapeForLightOcclusion(int stateId)
    {
        return (this.flags[checkedState(stateId)] & 2) != 0;
    }

    /** Reproduces Shapes.faceShapeOccludes for the source face and target opposite face. */
    public boolean occludes(int sourceStateId, int targetStateId, int direction)
    {
        int source = checkedState(sourceStateId);
        int target = checkedState(targetStateId);
        if (direction < 0 || direction >= 6)
            throw new IllegalArgumentException("Invalid direction " + direction);
        if (!usesShapeForLightOcclusion(source) && !usesShapeForLightOcclusion(target))
            return false;

        int first = this.faceShapeIds[source][direction];
        int second = this.faceShapeIds[target][OPPOSITE[direction]];
        int low = Math.min(first, second);
        int high = Math.max(first, second);
        byte cached = this.occlusionCache[low][high];
        if (cached != 0)
            return cached == 2;
        boolean result = coversUnitSquare(this.shapes[first], this.shapes[second]);
        this.occlusionCache[low][high] = (byte) (result ? 2 : 1);
        return result;
    }

    public String versionDir()
    {
        return this.versionDir;
    }

    public String sourceVersion()
    {
        return this.sourceVersion;
    }

    public String sourceHash()
    {
        return this.sourceHash;
    }

    private int checkedState(int stateId)
    {
        if (stateId < 0 || stateId >= this.emission.length)
            return 0;
        return stateId;
    }

    private static boolean coversUnitSquare(FaceShape first, FaceShape second)
    {
        if (first.rectangles.length == 0 && second.rectangles.length == 0)
            return false;

        List<Double> boundaries = new ArrayList<>();
        boundaries.add(0.0D);
        boundaries.add(1.0D);
        addXBoundaries(boundaries, first.rectangles);
        addXBoundaries(boundaries, second.rectangles);
        double[] xs = boundaries.stream().mapToDouble(Double::doubleValue).sorted().distinct().toArray();
        List<double[]> intervals = new ArrayList<>();
        for (int strip = 0; strip + 1 < xs.length; strip++)
        {
            double minX = xs[strip];
            double maxX = xs[strip + 1];
            if (maxX - minX <= EPSILON)
                continue;
            double middleX = (minX + maxX) * 0.5D;
            intervals.clear();
            addYIntervals(intervals, first.rectangles, middleX);
            addYIntervals(intervals, second.rectangles, middleX);
            if (!coversUnitInterval(intervals))
                return false;
        }
        return true;
    }

    private static void addXBoundaries(List<Double> boundaries, double[] rectangles)
    {
        for (int i = 0; i < rectangles.length; i += 4)
        {
            boundaries.add(rectangles[i]);
            boundaries.add(rectangles[i + 2]);
        }
    }

    private static void addYIntervals(List<double[]> intervals, double[] rectangles, double x)
    {
        for (int i = 0; i < rectangles.length; i += 4)
        {
            if (x > rectangles[i] - EPSILON && x < rectangles[i + 2] + EPSILON)
                intervals.add(new double[]{rectangles[i + 1], rectangles[i + 3]});
        }
    }

    private static boolean coversUnitInterval(List<double[]> intervals)
    {
        if (intervals.isEmpty())
            return false;
        intervals.sort((left, right) -> {
            int compare = Double.compare(left[0], right[0]);
            return compare != 0 ? compare : Double.compare(left[1], right[1]);
        });
        double covered = 0.0D;
        for (double[] interval : intervals)
        {
            if (interval[0] > covered + EPSILON)
                return false;
            covered = Math.max(covered, interval[1]);
            if (covered >= 1.0D - EPSILON)
                return true;
        }
        return false;
    }

    private static double clamp(double value)
    {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static final class FaceShape
    {
        private final double[] rectangles;

        private FaceShape(double[] rectangles)
        {
            this.rectangles = Arrays.copyOf(rectangles, rectangles.length);
        }
    }
}
