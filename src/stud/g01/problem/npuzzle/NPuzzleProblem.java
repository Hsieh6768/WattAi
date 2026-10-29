package stud.g01.problem.npuzzle;

import core.problem.Action;
import core.problem.Problem;
import core.problem.State;
import core.solver.queue.Node;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class NPuzzleProblem extends Problem {
    public NPuzzleProblem(State initialState, State goal) {
        super(initialState, goal, 0);
    }

    public NPuzzleProblem(State initialState, State goal, int size) {
        super(initialState, goal, size);
    }

    /** Generates the same legal children, using the actual goal for Manhattan deltas. */
    public List<Node> childNodesWithIncrementalManhattan(Node parent) {
        List<Node> children = new ArrayList<>();
        PuzzleBoard parentBoard = (PuzzleBoard) parent.getState();
        PuzzleBoard goalBoard = (PuzzleBoard) goal;
        for (Node child : childNodes(parent)) {
            PuzzleBoard childBoard = (PuzzleBoard) child.getState();
            children.add(child.setHeuristic(childBoard.manhattanFromParent(
                    parentBoard, goalBoard, parent.getHeuristic())));
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
