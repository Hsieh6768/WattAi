package stud.g01.solver;

import core.problem.Problem;
import core.problem.State;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.algorithm.heuristic.HeuristicType;
import stud.g01.problem.npuzzle.NPuzzleProblem;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.problem.npuzzle.PuzzleDirection;
import stud.g01.problem.npuzzle.PuzzleMove;
import core.solver.algorithm.searcher.AbstractSearcher;
import core.solver.queue.Frontier;
import core.solver.queue.Node;

import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class IdAStar extends AbstractSearcher {

    private final Predictor predictor; //预测器，对当前状态进行启发式估值
    private int nextBound;
    private boolean incrementalManhattan;

    private enum Direction { FORWARD, BACKWARD }

    private static Direction configuredDirection() {
        String value = System.getProperty("npuzzle.ida.direction", "forward");
        if ("forward".equalsIgnoreCase(value)) {
            return Direction.FORWARD;
        }
        if ("reverse".equalsIgnoreCase(value)) {
            return Direction.BACKWARD;
        }
        throw new IllegalArgumentException(
                "npuzzle.ida.direction must be forward or reverse: " + value);
    }

    /*
    public IdAStar(Frontier frontier) {
        super(frontier);
    }
    */

    public IdAStar(Frontier frontier, Predictor predictor) {
        super(frontier);
        this.predictor = predictor;
    }

    @Override
    public Deque<Node> search(Problem problem) {
        Objects.requireNonNull(problem, "problem");
        frontier.clear();
        explored.clear(); // Never use the global CLOSED set for IDA* pruning.
        nodesGenerated = 0;
        nodesExpanded = 0;

        Direction direction = configuredDirection();
        // Validate before solvability/root evaluation: unsupported reverse requests
        // must fail explicitly, even for solved or unsolvable inputs.
        incrementalManhattan = problem instanceof NPuzzleProblem
                && predictor == PuzzleBoard.predictor(HeuristicType.MANHATTAN);
        if (direction == Direction.BACKWARD && !incrementalManhattan) {
            throw new IllegalArgumentException(
                    "Reverse IDA* requires NPuzzleProblem + MANHATTAN");
        }
        if (!problem.solvable()) {
            return null;
        }

        Problem searchProblem = direction == Direction.BACKWARD
                ? ((NPuzzleProblem) problem).reversed() : problem;
        Node root = searchProblem.root(predictor);
        // Preserve overridden Problem/State behavior and larger boards via the
        // existing object kernel; four-bit encoding is exact for <=16 cells.
        if (incrementalManhattan && searchProblem.getClass() == NPuzzleProblem.class
                && root.getState().getClass() == PuzzleBoard.class) {
            PuzzleBoard goal = (PuzzleBoard) ((NPuzzleProblem) searchProblem)
                    .reversed().root().getState();
            PuzzleBoard initial = (PuzzleBoard) root.getState();
            if (goal.getClass() == PuzzleBoard.class
                    && initial.getSize() == goal.getSize()
                    && initial.getSize() >= 2 && initial.getSize() <= 4) {
                Node solution = new ManhattanDfs((NPuzzleProblem) searchProblem, root).search();
                if (solution == null) {
                    return null;
                }
                return direction == Direction.BACKWARD
                        ? ((NPuzzleProblem) problem).restoreForwardPath(solution)
                        : generatePath(solution);
            }
        }
        int bound = root.evaluation();
        Set<State> onPath = new HashSet<>();
        onPath.add(root.getState());

        while (true) {
            nextBound = Integer.MAX_VALUE;
            Node solution = depthFirst(searchProblem, root, bound, onPath);
            if (solution != null) {
                return direction == Direction.BACKWARD
                        ? ((NPuzzleProblem) problem).restoreForwardPath(solution)
                        : generatePath(solution);
            }
            if (nextBound == Integer.MAX_VALUE) {
                return null; // No successors beyond the current bound.
            }
            bound = nextBound; // Minimum f value that exceeded this iteration's bound.
        }
    }
    private Node depthFirst(Problem problem, Node node, int bound,
                            Set<State> onPath) {
        int f = node.evaluation();
        if (f > bound) {
            nextBound = Math.min(nextBound, f);
            return null;
        }
        if (problem.goal(node.getState())) {
            return node;
        }

        nodesExpanded++;
        // childNodes eagerly constructs *all* legal successors; count them all,
        // including children not visited because a solution was found early.
        java.util.List<Node> children = incrementalManhattan
                ? ((NPuzzleProblem) problem).childNodesWithIncrementalManhattan(node)
                : problem.childNodes(node, predictor);
        nodesGenerated += children.size();
        for (Node child : children) {
            State childState = child.getState();
            if (!onPath.add(childState)) {
                continue; // Do not revisit a state already on this DFS path.
            }
            try {
                Node solution = depthFirst(problem, child, bound, onPath);
                if (solution != null) {
                    return solution;
                }
            } finally {
                onPath.remove(childState); // Restore the path before exploring siblings.
            }
        }
        return null;
    }

    /** Mutable workspace owned by one search call, never used as a State/hash key. */
    private final class ManhattanDfs {
        private final NPuzzleProblem problem;
        private final Node root;
        private final int width;
        private final int[] board;
        private final int[] goalIndex;
        private final PuzzleBoard goalBoard;
        private final long goalKey;
        private final int initialBlank;
        private long[] pathKeys = new long[64];
        private int[] moves = new int[64];
        private int solutionDepth;

        private ManhattanDfs(NPuzzleProblem problem, Node root) {
            this.problem = problem;
            this.root = root;
            PuzzleBoard initial = (PuzzleBoard) root.getState();
            width = initial.getSize();
            board = initial.getBoard(); // Independent copy; original states stay immutable.
            initialBlank = initial.getRow() * width + initial.getCol();
            goalBoard = (PuzzleBoard) problem.reversed().root().getState();
            int[] goal = goalBoard.getBoard();
            goalIndex = new int[goal.length];
            for (int i = 0; i < goal.length; i++) {
                goalIndex[goal[i]] = i;
            }
            goalKey = encode(goal);
            pathKeys[0] = encode(board);
        }

        private long encode(int[] cells) {
            long key = 0;
            for (int i = 0; i < cells.length; i++) {
                key |= (long) cells[i] << (4 * i);
            }
            return key; // Bit 63 and negative keys are ordinary, exact board bits.
        }

        private Node search() {
            int bound = root.evaluation();
            while (true) {
                nextBound = Integer.MAX_VALUE;
                if (depthFirst(initialBlank, 0, root.getHeuristic(), -1, pathKeys[0], bound)) {
                    return rebuildPath(); // DFS has already restored the workspace.
                }
                if (nextBound == Integer.MAX_VALUE) {
                    return null;
                }
                bound = nextBound;
            }
        }

        private boolean depthFirst(int blank, int g, int h, int previousBlank,
                                   long key, int bound) {
            int f = g + h;
            if (f > bound) {
                nextBound = Math.min(nextBound, f);
                return false;
            }
            if (key == goalKey) {
                solutionDepth = g;
                return true;
            }
            nodesExpanded++;
            ensureCapacity(g + 2);
            int row = blank / width;
            int col = blank % width;
            for (int direction = 0; direction < 4; direction++) {
                int next;
                switch (direction) { // UP -> RIGHT -> DOWN -> LEFT, unchanged.
                    case 0: if (row == 0) continue; next = blank - width; break;
                    case 1: if (col == width - 1) continue; next = blank + 1; break;
                    case 2: if (row == width - 1) continue; next = blank + width; break;
                    default: if (col == 0) continue; next = blank - 1; break;
                }
                if (next == previousBlank) {
                    continue; // Exclude the immediate reverse before making a candidate.
                }
                nodesGenerated++; // Candidates actually processed, including path duplicates.
                int tile = board[next];
                long childKey = key ^ ((long) tile << (4 * blank))
                        ^ ((long) tile << (4 * next));
                if (onPath(childKey, g)) {
                    continue;
                }
                int target = goalIndex[tile];
                int childH = h - Math.abs(next / width - target / width)
                        - Math.abs(next % width - target % width)
                        + Math.abs(row - target / width) + Math.abs(col - target % width);
                moves[g] = direction;
                pathKeys[g + 1] = childKey;
                board[blank] = tile;
                board[next] = 0;
                try {
                    if (depthFirst(next, g + 1, childH, blank, childKey, bound)) {
                        return true;
                    }
                } finally {
                    board[next] = tile;
                    board[blank] = 0;
                    pathKeys[g + 1] = 0; // Only indices 0..g are live after popping.
                }
            }
            return false;
        }

        private boolean onPath(long key, int depth) {
            for (int i = 0; i <= depth; i++) {
                if (pathKeys[i] == key) {
                    return true;
                }
            }
            return false;
        }

        private void ensureCapacity(int required) {
            if (required > pathKeys.length) {
                int capacity = Math.max(required, pathKeys.length * 2);
                pathKeys = Arrays.copyOf(pathKeys, capacity);
                moves = Arrays.copyOf(moves, capacity);
            }
        }

        private Node rebuildPath() {
            Node parent = root;
            for (int i = 0; i < solutionDepth; i++) {
                PuzzleDirection direction = PuzzleDirection.FOUR_DIRECTIONS.get(moves[i]);
                PuzzleMove action = new PuzzleMove(direction);
                State state = parent.getState().next(action);
                parent = new Node(state, parent, action,
                        parent.getPathCost() + problem.stepCost(state, action),
                        ((PuzzleBoard) state).manhattanFromParent(
                                (PuzzleBoard) parent.getState(),
                                goalBoard,
                                parent.getHeuristic()));
            }
            return parent;
        }
    }
}
