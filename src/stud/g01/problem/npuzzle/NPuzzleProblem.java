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
        return true;
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
    }
}
