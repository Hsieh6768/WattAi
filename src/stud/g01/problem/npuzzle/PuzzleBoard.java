package stud.g01.problem.npuzzle;

import core.problem.Action;
import core.problem.State;
import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.algorithm.heuristic.Predictor;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;

import static core.solver.algorithm.heuristic.HeuristicType.*;

public class PuzzleBoard extends State {
    // 棋盘、大小、空格在棋盘中的位置: (行，列)
    // private final int[][] board;
    private final int[] board;
    private final int size;
    private final int row;
    private final int col;

    public PuzzleBoard(int[] board, int size, int row, int col) {
        /*this.board = new int[size][size];
        for (int i = 0; i < size; i++)
            this.board[i] = board[i].clone();*/
        this.board = board.clone();
        this.size = size;
        this.row = row;
        this.col = col;
    }

    @Override
    public void draw() {
        System.out.println(this);
    }

    /**
     * 当前状态采用action而进入的下一个状态
     *
     * @param action 当前状态下，一个可行的action
     * @return 下一个状态
     */
    @Override
    public State next(Action action) {
        PuzzleDirection dir = ((PuzzleMove)action).getPuzzleDirection();
        int[] offsets = PuzzleDirection.offset(dir);

        int size = getSize();
        // int[][] board = getBoard();
        int[] board = getBoard();
        int col = getCol() + offsets[0];
        int row = getRow() + offsets[1];
        int temp = board[row * size + col];
        board[row * size + col] = board[getRow() * size + getCol()];
        board[getRow() * size + getCol()] = temp;
        return new PuzzleBoard(board, size, row, col);
    }

    @Override
    public Iterable<? extends Action> actions() {
        Collection<PuzzleMove> moves = new ArrayList<>();
        for (PuzzleDirection d : PuzzleDirection.FOUR_DIRECTIONS)
            moves.add(new PuzzleMove(d));
        return moves;
    }

    //枚举映射，存放不同类型的启发函数
    private static final EnumMap<HeuristicType, Predictor> predictors = new EnumMap<>(HeuristicType.class);
    static{
        predictors.put(MISPLACED,
                (state, goal) -> ((PuzzleBoard)state).misplaced((PuzzleBoard)goal));
        predictors.put(MANHATTAN,
                (state, goal) -> ((PuzzleBoard)state).manhattan((PuzzleBoard)goal));
        predictors.put(DISJOINT_PATTERN,
                (state, goal) -> ((PuzzleBoard)state).disjointPattern((PuzzleBoard)goal));
    }

    public static Predictor predictor(HeuristicType type){
        return predictors.get(type);
    }

    // 不在位将牌数
    private int misplaced(PuzzleBoard goal) {
        int misplacedCount = 0;
        int[] goalBoard = goal.getBoard();
        for (int i = 0; i < size * size; i++) {
            if (i == row * size + col)
                continue;
            if (board[i] != goalBoard[i])
                misplacedCount++;
        }
        /*
        for (int i = 0; i < size; i++)
            for (int j = 0; j < size; j++)
                if (board[i * size + j] != goalBoard[i * size + j])
                    misplacedCount++
        */
        return misplacedCount;
    }

    // 两个点之间的曼哈顿距离
    private int manhattan(PuzzleBoard goal) {
        int manhattanCount = 0;
        for (int i = 0; i < size * size; i++) {
            if (i == this.row * this.size + this.col)
                continue;
            int row = i / size;
            int col = i % size;
            int targetRow = (board[i] - 1) / size;
            int targetCol = (board[i] - 1) % size;
            manhattanCount += (Math.abs(row - targetRow) + Math.abs(col - targetCol));
        }
        return manhattanCount;
    }

    // 两个点之间的逆序对数
    private int disjointPattern(PuzzleBoard goal) {
        int disjointCount = 0;
        for (int i = 0; i < size * size - 1; i++) {
            if (i == row * size + col)
                continue;
            for (int j = i + 1; j < size * size; j++)
                if (board[j] > board[i])
                    disjointCount++;
        }
        return disjointCount;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;

        if (obj instanceof PuzzleBoard) {
            PuzzleBoard another = (PuzzleBoard) obj;
            if (this.size == another.getSize())
                //两个PuzzleBoard对象的数组逐个相等，则认为是相同的
                // return Arrays.deepEquals(this.board, another.getBoard());
                return Arrays.equals(this.board, another.board);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(board);
        // return Arrays.deepHashCode(board);
    }

    public int getSize() {
        return size;
    }

    /*public int[][] getBoard() {
        int[][] board_copy = new int[size][size];
        for (int i = 0; i < size; i++)
            board_copy[i] = board[i].clone();
        return board_copy;
    }*/
    public int[] getBoard() {
        return board.clone();
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder();

        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (c > 0) {
                    text.append(' ');
                }

                int value = board[r * size + c];

                // 让 0～9 与 10～15 大致对齐。
                if (value < 10) {
                    text.append(' ');
                }
                text.append(value);
            }

            if (r < size - 1) {
                text.append('\n');
            }
        }

        return text.toString();
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public static void main(String[] args) {
        int[] initial = {1, 2, 3, 4, 0, 6, 7, 5, 8};

        PuzzleBoard parent = new PuzzleBoard(initial, 3, 1, 1);

        // 从同一个父棋盘分别生成两个孩子。
        PuzzleBoard down = (PuzzleBoard) parent.next(
                new PuzzleMove(PuzzleDirection.DOWN));

        PuzzleBoard up = (PuzzleBoard) parent.next(
                new PuzzleMove(PuzzleDirection.UP));

        int[] expectedDown = {1, 2, 3, 4, 5, 6, 7, 0, 8};

        int[] expectedUp = {1, 0, 3, 4, 2, 6, 7, 5, 8};

        System.out.println("原棋盘未改变："
                + Arrays.equals(parent.getBoard(), initial));

        System.out.println("向下移动正确："
                + Arrays.equals(down.getBoard(), expectedDown));

        System.out.println("向上移动正确："
                + Arrays.equals(up.getBoard(), expectedUp));

        System.out.println("生成另一个孩子后，down 仍然正确："
                + Arrays.equals(down.getBoard(), expectedDown));

        // 下移之后再上移，应该恢复原排列。
        PuzzleBoard restored = (PuzzleBoard) down.next(
                new PuzzleMove(PuzzleDirection.UP));

        System.out.println("反向移动恢复原棋盘："
                + Arrays.equals(restored.getBoard(), initial));

        System.out.println("down 的空格坐标正确："
                + (down.getRow() == 2 && down.getCol() == 1));

        System.out.println(Arrays.toString(down.getBoard()));

        java.util.HashSet<PuzzleBoard> seen = new java.util.HashSet<>();

        seen.add(parent);

        PuzzleBoard same = new PuzzleBoard(initial, 3, 1, 1);

        System.out.println(seen.contains(same)); // 应为 true

        seen.add(same);

        System.out.println(seen.size());         // 应为 1
    }
}

