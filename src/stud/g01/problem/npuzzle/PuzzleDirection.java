package stud.g01.problem.npuzzle;

import java.util.EnumMap;
import java.util.List;

public enum PuzzleDirection {
    UP('↑'),  //上
    RIGHT('→'),  //右
    DOWN('↓'),  //下
    LEFT('←');  //左

    private final char symbol;

    /**
     * 构造函数
     * @param symbol 枚举项的代表符号--箭头
     */
    PuzzleDirection(char symbol){
        this.symbol = symbol;
    }

    public char symbol(){
        return symbol;
    }


    /**
     * 移动方向（4个方向）。
     */
    public static final List<PuzzleDirection> FOUR_DIRECTIONS = List.of(PuzzleDirection.UP, PuzzleDirection.RIGHT, PuzzleDirection.DOWN, PuzzleDirection.LEFT);

    public static final int SCALE = 1;       //单元格的边长
    /**
     * 不同方向的耗散值
     */
    public static int cost(){
        return SCALE;
    }

    //各个方向移动的坐标位移量
    private static final EnumMap<PuzzleDirection, int[]> DIRECTION_OFFSET = new EnumMap<>(PuzzleDirection.class);
    static{
        //列号（或横坐标）增加量；行号（或纵坐标）增加量
        DIRECTION_OFFSET.put(UP, new int[]{0, -1});
        DIRECTION_OFFSET.put(RIGHT, new int[]{1, 0});
        DIRECTION_OFFSET.put(DOWN, new int[]{0, 1});
        DIRECTION_OFFSET.put(LEFT, new int[]{-1, 0});
    }

    public static int[] offset(PuzzleDirection dir){
        return DIRECTION_OFFSET.get(dir);
    }
}
