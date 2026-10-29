package stud.g01.solver;

import core.problem.Problem;
import core.problem.State;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.algorithm.heuristic.HeuristicType;
import stud.g01.problem.npuzzle.NPuzzleProblem;
import stud.g01.problem.npuzzle.PuzzleBoard;
import core.solver.algorithm.searcher.AbstractSearcher;
import core.solver.queue.Frontier;
import core.solver.queue.Node;

import java.util.Deque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class IdAStar extends AbstractSearcher {

    private final Predictor predictor; //预测器，对当前状态进行启发式估值
    private int nextBound;
    private boolean incrementalManhattan;

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

        if (!problem.solvable()) {
            return null;
        }

        // Custom predictors and other problems retain their original evaluation path.
        incrementalManhattan = problem instanceof NPuzzleProblem
                && predictor == PuzzleBoard.predictor(HeuristicType.MANHATTAN);
        Node root = problem.root(predictor);
        int bound = root.evaluation();
        Set<State> onPath = new HashSet<>();
        onPath.add(root.getState());

        while (true) {
            nextBound = Integer.MAX_VALUE;
            Node solution = depthFirst(problem, root, bound, onPath);
            if (solution != null) {
                return generatePath(solution);
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
}
