package stud.g01.solver;

import core.problem.Problem;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.algorithm.searcher.AbstractSearcher;
import core.solver.queue.Frontier;
import core.solver.queue.Node;

import java.util.Deque;

public class IdAStar extends AbstractSearcher {

    private final Predictor predictor; //预测器，对当前状态进行启发式估值

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
        return null;
    }
}
