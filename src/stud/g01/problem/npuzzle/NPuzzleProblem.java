package stud.g01.problem.npuzzle;

import core.problem.Action;
import core.problem.Problem;
import core.problem.State;
import core.solver.queue.Node;
import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.algorithm.heuristic.Predictor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.ToIntFunction;

public class NPuzzleProblem extends Problem {
    public NPuzzleProblem(State initialState, State goal) {
        super(initialState, goal, 0);
    }

    public NPuzzleProblem(State initialState, State goal, int size) {
        super(initialState, goal, size);
    }

    /** A separate search problem; the original endpoints are never reassigned. */
    public NPuzzleProblem reversed() {
        return new NPuzzleProblem(goal, initialState, size);
    }

    /**
     * Replays the reversed/inverted G-to-S actions from the original S.
     * Like AbstractSearcher.generatePath, the deque excludes the root node.
     * All returned costs and Manhattan estimates refer to the original problem.
     */
    public Deque<Node> restoreForwardPath(Node reverseSolution) {
        Predictor manhattan = PuzzleBoard.predictor(HeuristicType.MANHATTAN);
        Deque<Node> path = new ArrayDeque<>();
        Node parent = root(manhattan);
        for (Node reverse = reverseSolution; reverse.getParent() != null;
             reverse = reverse.getParent()) {
            PuzzleDirection direction = ((PuzzleMove) reverse.getAction()).getPuzzleDirection();
            PuzzleMove action = new PuzzleMove(switch (direction) {
                case UP -> PuzzleDirection.DOWN;
                case RIGHT -> PuzzleDirection.LEFT;
                case DOWN -> PuzzleDirection.UP;
                case LEFT -> PuzzleDirection.RIGHT;
            });
            State state = parent.getState().next(action);
            int pathCost = parent.getPathCost() + stepCost(state, action);
            Node child = new Node(state, parent, action, pathCost,
                    manhattan.heuristics(state, goal));
            path.addLast(child);
            parent = child;
        }
        return path;
    }

    /** Bind the real goal once; queries read the caller's board without copying it. */
    public ToIntFunction<int[]> disjointPatternEstimator() {
        DisjointPatternDatabase database = DisjointPatternDatabase.forGoal((PuzzleBoard) goal);
        return database::estimate;
    }

    /** IDA* Manhattan children, excluding the immediate return to the parent state. */
    public List<Node> childNodesWithIncrementalManhattan(Node parent) {
        List<Node> children = new ArrayList<>();
        PuzzleBoard parentBoard = (PuzzleBoard) parent.getState();
        PuzzleBoard goalBoard = (PuzzleBoard) goal;
        for (Node child : childNodesWithoutImmediateReverse(parent)) {
            PuzzleBoard childBoard = (PuzzleBoard) child.getState();
            children.add(child.setHeuristic(childBoard.manhattanFromParent(
                    parentBoard, goalBoard, parent.getHeuristic())));
        }
        return children;
    }

    private List<Node> childNodesWithoutImmediateReverse(Node parent) {
        if (parent.getParent() == null) {
            return childNodes(parent); // The root keeps every legal move.
        }
        List<Node> children = new ArrayList<>();
        PuzzleBoard board = (PuzzleBoard) parent.getState();
        PuzzleBoard previous = (PuzzleBoard) parent.getParent().getState();
        for (Action action : board.actions()) {
            if (!applicable(board, action)) {
                continue;
            }
            int[] offsets = PuzzleDirection.offset(((PuzzleMove) action).getPuzzleDirection());
            if (board.getRow() + offsets[1] == previous.getRow()
                    && board.getCol() + offsets[0] == previous.getCol()) {
                continue; // Prune before next() copies the board or a Node is created.
            }
            State state = board.next(action);
            int pathCost = parent.getPathCost() + stepCost(state, action);
            children.add(new Node(state, parent, action, pathCost));
        }
        return children;
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

        // 计算逆序数，忽略空格 0
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

        // 奇数宽度：只看逆序数
        if (width % 2 == 1) {
            return inversions % 2;
        }

        // 偶数宽度：逆序数 + 空格距底行距离
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
}
