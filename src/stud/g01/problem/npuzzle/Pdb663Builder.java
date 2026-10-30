package stud.g01.problem.npuzzle;

import java.nio.file.*;
import java.util.Arrays;

/** Explicit offline table builder, not a search runner. No arguments prints the plan only. */
public final class Pdb663Builder {
    private Pdb663Builder() { }

    public static void main(String[] args) throws Exception {
        int[] goal = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0};
        Path output = Paths.get("data", "npuzzle_pdb", PatternDatabase663.cacheName(goal)).toAbsolutePath();
        System.out.println("Offline PDB partition 663: " + Arrays.deepToString(PatternDatabase663.groups()));
        System.out.println("4x4 target: " + Arrays.toString(goal));
        System.out.println("Output (never overwrite): " + output);
        System.out.println("Sequential groups; array payload <=1153 MiB (heap overhead extra); use a dedicated JVM with -Xmx2g.");
        if (args.length == 0) {
            System.out.println("Plan only. To build, supply exactly: --build-663 --confirm-full-build");
            return;
        }
        if (args.length != 2 || !"--build-663".equals(args[0]) || !"--confirm-full-build".equals(args[1])) {
            throw new IllegalArgumentException("Full generation requires exactly --build-663 --confirm-full-build");
        }
        if (Files.exists(output)) throw new FileAlreadyExistsException(output.toString());
        if (Runtime.getRuntime().maxMemory() < 1536L * 1024 * 1024) {
            throw new IllegalStateException("Insufficient JVM heap for full 663 build; restart with -Xmx2g");
        }
        long started = System.nanoTime();
        PatternDatabase663.generate(output, goal, System.out);
        System.out.printf("PDB 663 generated and verified: %s; %,d bytes; %.3fs%n",
                output, Files.size(output), (System.nanoTime() - started) / 1e9);
    }
}
