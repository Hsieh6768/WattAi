package stud.g01.runner;

import core.problem.Problem;
import core.runner.EngineFeeder;
import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.queue.EvaluationType;
import core.solver.queue.Frontier;
import core.solver.queue.Node;

import stud.g01.problem.npuzzle.NPuzzleProblem;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.queue.PqFrontier;

import java.util.ArrayList;
//Fix Me   //Fix Me
public class PuzzleFeeder extends EngineFeeder {
    @Override
    public ArrayList<Problem> getProblems(ArrayList<String> problemLines) {
        /* 读入各个问题，每行是一个独立的数字推盘实例 */
        ArrayList<Problem> problems = new ArrayList<>();
        int lineNo = 0;
        while (lineNo < problemLines.size()){
            //读入问题实例
            NPuzzleProblem problem = getNPuzzle(problemLines.get(lineNo));
            //添加到问题列表
            problems.add(problem);
            lineNo++;
        } //读入问题结束

        return problems;
    }

    /**
     * 生成数字推盘问题的一个实例
     */
    private NPuzzleProblem getNPuzzle(String problemLine) {
        String[] cells = problemLine.trim().split("\\s+");
        //问题规模，每行的第一个数字
        int size = Integer.parseInt(cells[0]);
        //读入初始状态，从第二个数字开始
        PuzzleBoard initialState = getBoard(cells, 1, size);
        //读入目标状态，紧接在初始状态之后
        PuzzleBoard goal = getBoard(cells, 1 + size * size, size);

        //生成数字推盘问题的实例
        return new NPuzzleProblem(initialState, goal, size);
    }

    /**
     * 从指定起始位置读入一张棋盘，并记录空格坐标
     */
    private PuzzleBoard getBoard(String[] cells, int start, int size) {
        int[] board = new int[size * size];
        int row = 0;
        int col = 0;
        for (int i = 0; i < size * size; i++){
            board[i] = Integer.parseInt(cells[start + i]);
            if (board[i] == 0){
                row = i / size;
                col = i % size;
            }
        }
        return new PuzzleBoard(board, size, row, col);
    }

    @Override
    public Frontier getFrontier(EvaluationType type) {
        return new PqFrontier(Node.evaluator(type));
    }

    @Override
    public Predictor getPredictor(HeuristicType type) {
        return PuzzleBoard.predictor(type);
    }
}
