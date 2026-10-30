package stud.g01.problem.npuzzle;

import java.io.*;
import java.nio.file.*;
import java.util.Arrays;
import java.util.zip.CRC32;

/** New 663 storage/generation only. Legacy blank-inclusive v1 tables stay untouched. */
final class PatternDatabase663 {
    private static final int MAGIC = 0x57415032; // WAP2, different from WAPD/v1
    private static final int VERSION = 2;
    private static final int END = 0x454e4432; // END2: only a complete file has this trailer
    private static final int UNKNOWN = 255;

    private PatternDatabase663() { }

    static int[][] groups() {
        return new int[][]{{2, 3, 4}, {1, 5, 6, 9, 10, 13}, {7, 8, 11, 12, 14, 15}};
    }

    static String cacheName(int[] goal) {
        StringBuilder name = new StringBuilder("additive_v2_p663_n4_");
        for (int tile : goal) name.append(Character.forDigit(tile, 16));
        return name.append(".bin").toString();
    }

    static byte[][] readRequired(Path path, int[] goal, int[][] groups) {
        try {
            byte[][] result = read(path, goal, groups);
            System.err.println("PDB 663 loaded: " + path);
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("PDB 663 cache missing or invalid: " + path.toAbsolutePath()
                    + ". No automatic generation or fallback. Run stud.g01.problem.npuzzle.Pdb663Builder"
                    + " offline with --build-663 --confirm-full-build; see the 3C report. Reason: "
                    + e.getMessage(), e);
        }
    }

    /** Strict, bounded allocation reader. Complete goal and ordered groups are in the header. */
    static byte[][] read(Path path, int[] goal, int[][] groups) throws IOException {
        long expectedLength = 88 + 4; // fixed header including 16 goal tiles, then END2
        for (int[] group : groups) expectedLength += 16 + 4L * group.length + tableLength(group.length);
        if (Files.size(path) != expectedLength) throw new IOException("Incorrect cache file length");
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            require(in.readInt() == MAGIC, "magic");
            require(in.readInt() == VERSION, "version");
            require(in.readInt() == 4, "board size");
            require(in.readInt() == 663, "partition");
            require(in.readInt() == 16, "goal length");
            for (int tile : goal) require(in.readInt() == tile, "goal permutation");
            require(in.readInt() == groups.length, "group count");
            byte[][] result = new byte[groups.length][];
            for (int i = 0; i < groups.length; i++) {
                int[] group = groups[i];
                require(in.readInt() == group.length, "group length");
                for (int tile : group) require(in.readInt() == tile, "ordered group tiles");
                require(in.readInt() == tableLength(group.length), "table length");
                byte[] table = new byte[tableLength(group.length)];
                in.readFully(table);
                CRC32 crc = new CRC32(); crc.update(table);
                require(in.readLong() == crc.getValue(), "table checksum");
                validateTable(table, group, goal);
                result[i] = table;
            }
            require(in.readInt() == END && in.read() == -1, "completion trailer / trailing bytes");
            return result;
        }
    }

    static void writeHeader(DataOutputStream out, int[] goal, int[][] groups) throws IOException {
        validateGoal(goal);
        out.writeInt(MAGIC); out.writeInt(VERSION); out.writeInt(4); out.writeInt(663);
        out.writeInt(goal.length);
        for (int tile : goal) out.writeInt(tile);
        out.writeInt(groups.length);
    }

    static void writeTable(DataOutputStream out, int[] group, byte[] table, int[] goal) throws IOException {
        validateTable(table, group, goal);
        out.writeInt(group.length);
        for (int tile : group) out.writeInt(tile);
        out.writeInt(table.length); out.write(table);
        CRC32 crc = new CRC32(); crc.update(table); out.writeLong(crc.getValue());
    }

    /** Reject missing legal entries, bogus collision entries, and nonzero goal distance. */
    private static void validateTable(byte[] table, int[] group, int[] goal) throws IOException {
        require(table.length == tableLength(group.length), "table array length");
        int entries = 0;
        for (int key = 0; key < table.length; key++) {
            int used = 0;
            boolean legal = true;
            for (int j = 0; j < group.length; j++) {
                int bit = 1 << ((key >>> (4 * j)) & 15);
                if ((used & bit) != 0) { legal = false; break; }
                used |= bit;
            }
            boolean known = Byte.toUnsignedInt(table[key]) != UNKNOWN;
            if (legal != known) throw new IOException("Invalid PDB 663: incomplete table or colliding tile positions at key " + key);
            if (known) entries++;
        }
        require(entries == permutations(group.length), "reachable entry count");
        require(table[goalKey(goal, group) >>> 4] == 0, "goal distance");
    }

    /**
     * From the real target (including blank), run reverse 0-1 BFS.
     * Key: blank in bits 0..3, ordered tile positions in bits 4..27.
     * Output key: key >>> 4; minimum over ALL legal blank positions, not just first discovery.
     */
    static byte[] buildPattern(int[] goal, int[] group, PrintStream progress) {
        validateGoal(goal);
        validateGroup(group);
        int stateCount = permutations(group.length + 1);
        byte[] distances = new byte[1 << (4 * (group.length + 1))];
        Arrays.fill(distances, (byte) UNKNOWN);
        byte[] folded = new byte[tableLength(group.length)];
        Arrays.fill(folded, (byte) UNKNOWN);
        // At first discovery from a settled distance d, a vertex gets d or d+1.
        // It can improve at most once (d+1 -> d): <=2 entries per legal state.
        // Segments are retained and reused; no large array-copy growth peaks.
        LongDeque queue = new LongDeque(Math.multiplyExact(2, stateCount));
        int start = goalKey(goal, group);
        distances[start] = 0; queue.addFirst(start);
        int[][] neighbors = neighbors();
        long settled = 0, stale = 0, enqueued = 1;
        int maxQueue = 1, maxCost = 0;
        long started = System.nanoTime();
        while (!queue.isEmpty()) {
            long entry = queue.removeFirst();
            int key = (int) entry; // all 28 key bits survive; cost is in bits 32..39
            int cost = (int) (entry >>> 32);
            if (cost != Byte.toUnsignedInt(distances[key])) { stale++; continue; }
            settled++;
            maxCost = Math.max(maxCost, cost);
            int tileKey = key >>> 4;
            if (cost < Byte.toUnsignedInt(folded[tileKey])) folded[tileKey] = (byte) cost;
            int blank = key & 15;
            for (int nextBlank : neighbors[blank]) {
                int nextKey = (key & ~15) | nextBlank;
                int weight = 0;
                for (int j = 0; j < group.length; j++) {
                    int shift = (j + 1) * 4;
                    if (((key >>> shift) & 15) == nextBlank) {
                        nextKey = (nextKey & ~(15 << shift)) | (blank << shift);
                        weight = 1; break;
                    }
                }
                int nextCost = cost + weight;
                if (nextCost < Byte.toUnsignedInt(distances[nextKey])) {
                    if (nextCost >= UNKNOWN) throw new IllegalStateException("PDB distance exceeds byte capacity");
                    distances[nextKey] = (byte) nextCost;
                    long nextEntry = ((long) nextCost << 32) | (nextKey & 0xffffffffL);
                    if (weight == 0) queue.addFirst(nextEntry); else queue.addLast(nextEntry);
                    enqueued++;
                }
            }
            maxQueue = Math.max(maxQueue, queue.count);
            if (settled % 5_000_000 == 0) {
                if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("PDB build interrupted");
                if (progress != null) progress.printf("  %s: %,d / %,d settled; queue %,d%n",
                        Arrays.toString(group), settled, stateCount, queue.count);
            }
        }
        if (settled != stateCount) throw new IllegalStateException("Incomplete abstract traversal: " + settled);
        if (progress != null) progress.printf(
                "  %s complete: states=%,d enqueued=%,d stale=%,d maxQueue=%,d maxCost=%d time=%.3fs%n",
                Arrays.toString(group), settled, enqueued, stale, maxQueue, maxCost,
                (System.nanoTime() - started) / 1e9);
        return folded; // distances and queue become unreachable when this method returns
    }

    /** Builds one table at a time and streams it out, so previous tables are not retained. */
    static void generate(Path output, int[] goal, PrintStream progress) throws IOException {
        if (Files.exists(output)) throw new FileAlreadyExistsException(output.toString());
        Path parent = output.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, "pdb663-build-", ".tmp");
        try {
            int[][] groups = groups();
            try (FileOutputStream file = new FileOutputStream(temp.toFile());
                 DataOutputStream out = new DataOutputStream(new BufferedOutputStream(file))) {
                writeHeader(out, goal, groups);
                for (int[] group : groups) writeGeneratedGroup(out, goal, group, progress);
                out.writeInt(END); out.flush(); file.getFD().sync();
            }
            read(temp, goal, groups); // checksum, completeness, goal and exact EOF before publication
            publish(temp, output.toAbsolutePath());
        } finally {
            Files.deleteIfExists(temp); // only this invocation's own scratch file
        }
    }

    private static void writeGeneratedGroup(DataOutputStream out, int[] goal, int[] group,
                                           PrintStream progress) throws IOException {
        byte[] table = buildPattern(goal, group, progress);
        writeTable(out, group, table, goal);
    }

    /**
     * Same-directory hard link is atomic no-clobber publication of the complete synced file.
     * Unlike ATOMIC_MOVE, it cannot replace an existing destination during a race.
     * If the filesystem cannot create hard links, fail; never copy a partial file into place.
     */
    static void publish(Path temp, Path output) throws IOException {
        Files.createLink(output, temp);
    }

    static int permutations(int count) {
        if (count < 1 || count > 7) throw new IllegalArgumentException("Expected 1..7 positions");
        int n = 1;
        for (int i = 0; i < count; i++) n = Math.multiplyExact(n, 16 - i);
        return n;
    }

    private static int tableLength(int tiles) {
        if (tiles < 1 || tiles > 6) throw new IllegalArgumentException("Expected 1..6 tiles");
        return 1 << (4 * tiles);
    }

    private static int goalKey(int[] goal, int[] group) {
        int key = 0;
        for (int p = 0; p < goal.length; p++) {
            if (goal[p] == 0) key |= p;
            for (int j = 0; j < group.length; j++) {
                if (goal[p] == group[j]) key |= p << (4 * (j + 1));
            }
        }
        return key;
    }

    private static void validateGoal(int[] goal) {
        if (goal.length != 16) throw new IllegalArgumentException("663 requires 16 goal tiles");
        int mask = 0;
        for (int tile : goal) {
            if (tile < 0 || tile > 15 || (mask & (1 << tile)) != 0)
                throw new IllegalArgumentException("Invalid goal permutation");
            mask |= 1 << tile;
        }
    }

    private static void validateGroup(int[] group) {
        tableLength(group.length);
        int mask = 0;
        for (int tile : group) {
            if (tile < 1 || tile > 15 || (mask & (1 << tile)) != 0)
                throw new IllegalArgumentException("Invalid group tiles");
            mask |= 1 << tile;
        }
    }

    private static int[][] neighbors() {
        int[][] result = new int[16][];
        for (int p = 0; p < 16; p++) {
            int[] a = new int[4]; int n = 0;
            if (p / 4 > 0) a[n++] = p - 4;
            if (p % 4 < 3) a[n++] = p + 1;
            if (p / 4 < 3) a[n++] = p + 4;
            if (p % 4 > 0) a[n++] = p - 1;
            result[p] = Arrays.copyOf(a, n);
        }
        return result;
    }

    private static void require(boolean condition, String message) throws IOException {
        if (!condition) throw new IOException("Invalid PDB 663: " + message);
    }

    /**
     * Bounded ring of lazy 128 KiB primitive segments; no per-state objects or resizing.
     * Stay below G1's humongous threshold even with 1 MiB regions: a 1 MiB payload
     * plus its array header would otherwise occupy TWO whole regions per segment.
     */
    static final class LongDeque {
        private static final int BITS = 14;
        private static final int BLOCK = 1 << BITS;
        private static final int MASK = BLOCK - 1;
        private final long[][] segments;
        private final int capacity;
        private int head, count;

        LongDeque(int maxEntries) {
            int blocks = Math.toIntExact(((long) maxEntries + BLOCK - 1) / BLOCK);
            segments = new long[blocks][];
            capacity = Math.multiplyExact(blocks, BLOCK);
        }

        boolean isEmpty() { return count == 0; }
        void addFirst(long value) {
            reserve();
            head = head == 0 ? capacity - 1 : head - 1;
            put(head, value); count++;
        }
        void addLast(long value) {
            reserve();
            int index = head + count;
            if (index >= capacity) index -= capacity;
            put(index, value); count++;
        }
        long removeFirst() {
            if (count == 0) throw new IllegalStateException("Empty PDB queue");
            long value = segments[head >>> BITS][head & MASK];
            if (++head == capacity) head = 0;
            count--; return value;
        }
        private void reserve() {
            if (count == capacity) throw new IllegalStateException("PDB queue exceeded proven 2*state bound");
        }
        private void put(int index, long value) {
            int block = index >>> BITS;
            if (segments[block] == null) segments[block] = new long[BLOCK];
            segments[block][index & MASK] = value;
        }
    }
}
