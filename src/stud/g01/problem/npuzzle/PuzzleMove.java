package stud.g01.problem.npuzzle;

import core.problem.Action;

public class PuzzleMove extends Action {
    private final PuzzleDirection puzzleDirection;

    public PuzzleMove(PuzzleDirection puzzleDirection) {
        this.puzzleDirection = puzzleDirection;
    }

    public PuzzleDirection getPuzzleDirection() {
        return puzzleDirection;
    }

    @Override
    public void draw() {
        System.out.println(this);
    }

    @Override
    public int stepCost() {
        return PuzzleDirection.cost();
    }

    @Override
    public String toString() {
        return puzzleDirection.name();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;

        if (obj instanceof PuzzleMove) {
            PuzzleMove another = (PuzzleMove) obj;
            //两个Node对象的状态相同，则认为是相同的
            return this.puzzleDirection.equals(another.puzzleDirection);
        }
        return false;
    }
}
