package stud.g01.problem.npuzzle;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

/**
 * Additive disjoint pattern database for 8- and 15-puzzle.
 *
 * Each abstract state records the blank and the positions of the tiles in one group.
 * Moving an in-group tile costs 1; moving any other tile costs 0.
 * Reverse 0-1 BFS produces additive admissible heuristics. Legacy tables retain
 * the blank; 663 takes a minimum over blank positions after the complete search.
 *
 * 8-puzzle:  4 + 4 groups.
 * 15-puzzle: default 5 + 5 + 5; opt-in 6 + 6 + 3 uses folded v2 tables.
 *
 * In legacy tables and during 663 generation, the lower 4 bits store the blank;
 * successive nibbles store ordered tile positions. Final 663 tables omit the blank.
 */
final class DisjointPatternDatabase {
    private static final int MAGIC = 0x57415044; // WAPD
    private static final int VERSION = 1;
    private static final int UNKNOWN = 255;
    private static final Path CACHE_DIR = Paths.get("data", "npuzzle_pdb");

    // Fast path: almost all calls during one search use exactly the same goal instance.
    private static volatile ActiveCache activeCache;

    private final int size;
    private final String partition;
    private final int[] goalTiles;
    private final Pattern[] patterns;
    private final int[] groupOfTile;
    private final int[] shiftOfTile;

    static DisjointPatternDatabase forGoal(PuzzleBoard goal) {
        String partition = System.getProperty("npuzzle.pdb.partition", "555");
        if (!"555".equals(partition) && !"663".equals(partition)) {
            throw new IllegalArgumentException("npuzzle.pdb.partition must be 555 or 663: " + partition);
        }
        if ("663".equals(partition) && goal.getSize() != 4) {
            throw new IllegalArgumentException("npuzzle.pdb.partition=663 requires a 4x4 board");
        }
        ActiveCache current = activeCache;
        if (current != null && current.goal == goal && current.db.partition.equals(partition)) {
            return current.db;
        }
        synchronized (DisjointPatternDatabase.class) {
            current = activeCache;
            if (current != null && current.goal == goal && current.db.partition.equals(partition)) {
                return current.db;
            }
            int[] tiles = goal.getBoard(); // defensive copy, only on goal switch
            DisjointPatternDatabase db;
            if (current != null
                    && current.db.partition.equals(partition)
                    && current.db.size == goal.getSize()
                    && Arrays.equals(current.db.goalTiles, tiles)) {
                db = current.db;
            } else {
                db = new DisjointPatternDatabase(goal.getSize(), tiles, partition);
            }
            activeCache = new ActiveCache(goal, db); // atomic publication
            return db;
        }
    }

    private static final class ActiveCache {
        final PuzzleBoard goal;
        final DisjointPatternDatabase db;
        ActiveCache(PuzzleBoard goal, DisjointPatternDatabase db) {
            this.goal = goal;
            this.db = db;
        }
    }

    private DisjointPatternDatabase(int size, int[] goalTiles, String partition) {
        if (size != 3 && size != 4) {
            throw new IllegalArgumentException("PDB currently supports only 3x3 and 4x4 boards");
        }
        this.size = size;
        this.partition = partition;
        this.goalTiles = goalTiles.clone();
        validateGoal(this.goalTiles, size * size);
        int[][] groups = "663".equals(partition) ? PatternDatabase663.groups() : (size == 3)
                ? new int[][]{{1, 2, 3, 4}, {5, 6, 7, 8}}
                : new int[][]{{1, 2, 3, 4, 5}, {6, 7, 8, 9, 10}, {11, 12, 13, 14, 15}};
        this.patterns = new Pattern[groups.length];
        this.groupOfTile = new int[size * size];
        this.shiftOfTile = new int[size * size];
        Arrays.fill(groupOfTile, -1);
        for (int p = 0; p < groups.length; p++) {
            for (int j = 0; j < groups[p].length; j++) {
                int tile = groups[p][j];
                groupOfTile[tile] = p;
                shiftOfTile[tile] = (j + ("663".equals(partition) ? 0 : 1)) * 4;
            }
        }

        Path path = CACHE_DIR.resolve(cacheName());
        // Unlike the legacy tables, 663 is NEVER generated as a side effect of a query.
        byte[][] stored = "663".equals(partition)
                ? PatternDatabase663.readRequired(path, this.goalTiles, groups)
                : readCache(path, groups);
        int[][] neighbors = neighbors(size);
        for (int p = 0; p < groups.length; p++) {
            int[] tiles = groups[p];
            byte[] distances = (stored == null)
                    ? createPattern(tiles, neighbors)
                    : stored[p];
            patterns[p] = new Pattern(tiles, distances);
        }
        if (stored == null) {
            writeCache(path);
        }
    }

    /** No allocations during normal per-node heuristic evaluation. */
    int estimate(int[] board) {
        if (board.length != size * size) {
            throw new IllegalArgumentException("Mismatched puzzle dimensions");
        }
        int k0 = 0, k1 = 0, k2 = 0;
        for (int pos = 0; pos < board.length; pos++) {
            int tile = board[pos];
            if (tile == 0) {
                if (!"663".equals(partition)) {
                    k0 |= pos;
                    k1 |= pos;
                    k2 |= pos;
                }
            } else {
                int part = groupOfTile[tile];
                int bits = pos << shiftOfTile[tile];
                switch (part) {
                    case 0: k0 |= bits; break;
                    case 1: k1 |= bits; break;
                    case 2: k2 |= bits; break;
                    default: throw new IllegalArgumentException("Invalid tile " + tile);
                }
            }
        }
        int value = distance(patterns[0], k0) + distance(patterns[1], k1);
        if (patterns.length == 3) value += distance(patterns[2], k2);
        return value;
    }

    private static int distance(Pattern pattern, int key) {
        int result = Byte.toUnsignedInt(pattern.distances[key]);
        if (result == UNKNOWN) {
            throw new IllegalArgumentException("Invalid/unreachable abstract puzzle state");
        }
        return result;
    }

    private byte[] createPattern(int[] groupTiles, int[][] neighbors) {
        if (groupTiles.length > 5) {
            throw new IllegalArgumentException("Legacy 24-bit generator supports at most five tiles");
        }
        int length = 1 << (4 * (groupTiles.length + 1));
        byte[] dist = new byte[length];
        Arrays.fill(dist, (byte) UNKNOWN);
        int goalKey = 0;
        for (int pos = 0; pos < goalTiles.length; pos++) {
            int tile = goalTiles[pos];
            if (tile == 0) {
                goalKey |= pos;
            } else {
                for (int j = 0; j < groupTiles.length; j++) {
                    if (tile == groupTiles[j]) {
                        goalKey |= pos << ((j + 1) * 4);
                        break;
                    }
                }
            }
        }

        // Pack [cost:8 bits][state key:24 bits] to detect obsolete queue entries.
        IntDeque queue = new IntDeque();
        dist[goalKey] = 0;
        queue.addFirst(goalKey);
        while (!queue.isEmpty()) {
            int item = queue.removeFirst();
            int key = item & 0xFFFFFF;
            int d = item >>> 24;
            if (d != Byte.toUnsignedInt(dist[key])) continue;
            int blank = key & 15;
            for (int nextBlank : neighbors[blank]) {
                int nextKey = (key & ~15) | nextBlank;
                int cost = 0;
                for (int j = 0; j < groupTiles.length; j++) {
                    int shift = (j + 1) * 4;
                    if (((key >>> shift) & 15) == nextBlank) {
                        nextKey = (nextKey & ~(15 << shift)) | (blank << shift);
                        cost = 1;
                        break;
                    }
                }
                int newCost = d + cost;
                if (newCost < Byte.toUnsignedInt(dist[nextKey])) {
                    if (newCost >= UNKNOWN) {
                        throw new IllegalStateException("PDB distance exceeds byte capacity");
                    }
                    dist[nextKey] = (byte) newCost;
                    int nextItem = (newCost << 24) | nextKey;
                    if (cost == 0) queue.addFirst(nextItem);
                    else queue.addLast(nextItem);
                }
            }
        }
        return dist;
    }

    private static int[][] neighbors(int size) {
        int[][] result = new int[size * size][];
        for (int i = 0; i < result.length; i++) {
            int r = i / size, c = i % size;
            int[] tmp = new int[4];
            int n = 0;
            if (r > 0) tmp[n++] = i - size;
            if (c > 0) tmp[n++] = i - 1;
            if (r < size - 1) tmp[n++] = i + size;
            if (c < size - 1) tmp[n++] = i + 1;
            result[i] = Arrays.copyOf(tmp, n);
        }
        return result;
    }

    private static void validateGoal(int[] goal, int n) {
        if (goal.length != n) throw new IllegalArgumentException("Invalid goal size");
        boolean[] seen = new boolean[n];
        for (int tile : goal) {
            if (tile < 0 || tile >= n || seen[tile]) {
                throw new IllegalArgumentException("Invalid goal permutation");
            }
            seen[tile] = true;
        }
    }

    private String cacheName() {
        if ("663".equals(partition)) return PatternDatabase663.cacheName(goalTiles);
        StringBuilder builder = new StringBuilder("additive_v" + VERSION + "_n" + size + "_");
        for (int tile : goalTiles) builder.append(Character.forDigit(tile, 16));
        return builder.append(".bin").toString();
    }

    private byte[][] readCache(Path path, int[][] groups) {
        if (!Files.isRegularFile(path)) return null;
        try (DataInputStream in = new DataInputStream(Files.newInputStream(path))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION || in.readInt() != size
                    || in.readInt() != groups.length) return null;
            byte[][] result = new byte[groups.length][];
            for (int i = 0; i < groups.length; i++) {
                if (in.readInt() != groups[i].length) return null;
                for (int tile : groups[i]) if (in.readInt() != tile) return null;
                int len = in.readInt();
                if (len != (1 << (4 * (groups[i].length + 1)))) return null;
                result[i] = new byte[len];
                in.readFully(result[i]);
            }
            if (in.read() != -1) return null;
            System.err.println("PDB loaded: " + path);
            return result;
        } catch (IOException ignored) {
            return null; // Rebuild incomplete/incompatible cache.
        }
    }

    private void writeCache(Path path) {
        Path temp = null;
        try {
            Files.createDirectories(CACHE_DIR);
            temp = Files.createTempFile(CACHE_DIR, "pdb-build-", ".tmp");
            try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(temp))) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeInt(size);
                out.writeInt(patterns.length);
                for (Pattern pattern : patterns) {
                    out.writeInt(pattern.tiles.length);
                    for (int tile : pattern.tiles) out.writeInt(tile);
                    out.writeInt(pattern.distances.length);
                    out.write(pattern.distances);
                }
            }
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            System.err.println("PDB generated and saved: " + path);
        } catch (IOException e) {
            System.err.println("PDB cache write skipped: " + e.getMessage());
        } finally {
            if (temp != null) {
                try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
            }
        }
    }

    private static final class Pattern {
        final int[] tiles;
        final byte[] distances;
        Pattern(int[] tiles, byte[] distances) {
            this.tiles = tiles;
            this.distances = distances;
        }
    }

    private static final class IntDeque {
        private int[] data = new int[65536];
        private int head = 0;
        private int count = 0;

        boolean isEmpty() { return count == 0; }
        void addFirst(int value) {
            if (count == data.length) grow();
            head = (head - 1 + data.length) % data.length;
            data[head] = value;
            count++;
        }
        void addLast(int value) {
            if (count == data.length) grow();
            data[(head + count) % data.length] = value;
            count++;
        }
        int removeFirst() {
            int value = data[head];
            head = (head + 1) % data.length;
            count--;
            return value;
        }
        private void grow() {
            int[] next = new int[data.length * 2];
            int right = Math.min(count, data.length - head);
            System.arraycopy(data, head, next, 0, right);
            if (count > right) System.arraycopy(data, 0, next, right, count - right);
            data = next;
            head = 0;
        }
    }
}
