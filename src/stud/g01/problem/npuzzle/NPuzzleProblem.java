package stud.g01.problem.npuzzle;

import core.problem.Action;
import core.problem.Problem;
import core.problem.State;
import core.solver.queue.Node;

import java.util.Deque;

public class NPuzzleProblem extends Problem {
    public NPuzzleProblem(State initialState, State goal) {
        super(initialState, goal, 0);
    }

    public NPuzzleProblem(State initialState, State goal, int size) {
        super(initialState, goal, size);
    }

    @Override
    public boolean solvable() {
        return solvabilityParity((PuzzleBoard) initialState)
                == solvabilityParity((PuzzleBoard) goal);
    }

    // 可解性由棋盘排列的不变量决定，需分别计算初始状态和目标状态。
    private static int solvabilityParity(PuzzleBoard state) {
        int[] board = state.getBoard();
        int width = state.getSize();
        int inversions = 0;
        int blankRow = 0;

        // 按行展开，忽略空格 0，计算逆序对数量。
        for (int i = 0; i < board.length; i++) {
            if (board[i] == 0) {
                blankRow = i / width;
                continue;
            }
            for (int j = i + 1; j < board.length; j++) {
                if (board[j] != 0 && board[i] > board[j]) {
                    inversions++;
                }
            }
        }

        // 奇数边长（例如 3x3）：逆序数的奇偶性保持不变。
        if (width % 2 == 1) {
            return inversions % 2;
        }

        // 偶数边长（例如 4x4）：再考虑空格距最底行的距离（从 0 计）。
        int distanceFromBottom = width - 1 - blankRow;
        return (inversions + distanceFromBottom) % 2;
    }

    // 代价都是 1
    @Override
    public int stepCost(State state, Action action) {
        return action.stepCost();
    }

    // 检查空格所在索引是否越界
    @Override
    public boolean applicable(State state, Action action) {
        int[] offsets = PuzzleDirection.offset(((PuzzleMove)action).getPuzzleDirection());
        int row = ((PuzzleBoard)state).getRow() + offsets[1];
        int col = ((PuzzleBoard)state).getCol() + offsets[0];
        return row >= 0 && row < size && col >= 0 && col < size;
    }

    @Override
    public void showSolution(Deque<Node> path) {
        System.out.println("step 0：初始棋盘");
        System.out.println(initialState);

        int step = 1;
        for (Node node : path) {
            System.out.println();
            System.out.println("step " + step++ + "：" + node.getAction());
            System.out.println(node.getState());
        }
    }

    // test
    public static void main(String[] args) {
        int[] initialData = {1, 2, 3, 4, 0, 6, 7, 5, 8};
        int[] goalData = {1, 2, 3, 4, 5, 6, 7, 8, 0};

        PuzzleBoard initial =
                new PuzzleBoard(initialData, 3, 1, 1);
        PuzzleBoard goal =
                new PuzzleBoard(goalData, 3, 2, 2);

        NPuzzleProblem problem = new NPuzzleProblem(initial, goal, 3);

        Node root = problem.root();
        var children = problem.childNodes(root);

        System.out.println("孩子数量为4：" + (children.size() == 4));

        for (Node child : children) {
            PuzzleBoard childBoard = (PuzzleBoard) child.getState();
            int blankIndex = childBoard.getRow() * childBoard.getSize()
                    + childBoard.getCol();

            System.out.println("空格坐标确实指向0："
                    + (childBoard.getBoard()[blankIndex] == 0));

            System.out.println("动作：" + child.getAction());
            System.out.println(childBoard);
            System.out.println("g为1：" + (child.getPathCost() == 1));
            System.out.println("父节点正确：" + (child.getParent() == root));
            System.out.println("空格坐标："
                    + childBoard.getRow() + "," + childBoard.getCol());
            System.out.println();
        }

        System.out.println("原棋盘未改变："
                + java.util.Arrays.equals(
                initial.getBoard(), initialData));

        // 判断可解性所需时间：预热后重复调用，避免单次计时误差。
        System.out.println("\n=== 可解性判定耗时测试 ===");
        NPuzzleProblem impossible3 = new NPuzzleProblem(
                new PuzzleBoard(new int[]{1, 2, 3, 4, 5, 6, 8, 7, 0}, 3, 2, 2),
                goal, 3);
        benchmarkSolvable("8-Puzzle (3x3)", problem, impossible3);

        PuzzleBoard goal4 = new PuzzleBoard(
                new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0},
                4, 3, 3);
        NPuzzleProblem possible4 = new NPuzzleProblem(
                new PuzzleBoard(
                        new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 0, 13, 14, 15, 12},
                        4, 2, 3),
                goal4, 4);
        NPuzzleProblem impossible4 = new NPuzzleProblem(
                new PuzzleBoard(
                        new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 15, 14, 0},
                        4, 3, 3),
                goal4, 4);
        benchmarkSolvable("15-Puzzle (4x4)", possible4, impossible4);
    }

    private static void benchmarkSolvable(
            String label, NPuzzleProblem possible, NPuzzleProblem impossible) {
        final int warmup = 20_000;
        final int iterations = 200_000;

        // 预热 JIT；保留计数，避免预热调用被编译器当作无用代码。
        int warmupSolvableCount = 0;
        for (int i = 0; i < warmup; i++) {
            if ((i % 2 == 0 ? possible : impossible).solvable()) {
                warmupSolvableCount++;
            }
        }
        if (warmupSolvableCount != warmup / 2) {
            throw new AssertionError(label + " 的预热校验异常");
        }

        long start = System.nanoTime();
        int solvableCount = 0;
        for (int i = 0; i < iterations; i++) {
            if ((i % 2 == 0 ? possible : impossible).solvable()) {
                solvableCount++;
            }
        }
        long elapsed = System.nanoTime() - start;

        if (solvableCount != iterations / 2) {
            throw new AssertionError(label + " 的可解性判断结果异常");
        }
        System.out.printf(java.util.Locale.ROOT,
                "%s: %d 次，总耗时 %.3f ms，平均 %.1f ns/次 (%.4f us/次)%n",
                label, iterations, elapsed / 1_000_000.0,
                elapsed / (double) iterations,
                elapsed / (iterations * 1_000.0));
    }
}
