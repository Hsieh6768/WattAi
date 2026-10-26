package stud.g01.queue;

import core.problem.State;
import core.solver.queue.Frontier;
import core.solver.queue.Node;

import java.util.PriorityQueue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Objects;

public class PqFrontier extends PriorityQueue<Node> implements Frontier {
    private final ArrayList<Node> heap = new ArrayList<>();
    private final HashMap<State, Integer> indexMap = new HashMap<>();
    private final Comparator<Node> evaluator;

    public PqFrontier(Comparator<Node> evaluator) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
    }

    @Override
    public boolean contains(Node node) {
        return node != null && indexMap.containsKey(node.getState());
    }

    @Override
    public boolean offer(Node node) {
        Objects.requireNonNull(node, "node");
        State state = Objects.requireNonNull(node.getState(), "node.state");

        Integer oldIndex = indexMap.get(state);

        if (oldIndex != null) {
            Node oldNode = heap.get(oldIndex);

            // 相同状态只保留 g 更小的路径
            if (node.getPathCost() >= oldNode.getPathCost()) {
                return false;
            }

            // 用更优节点替换原节点，重新调整堆
            heap.set(oldIndex, node);
            fixHeap(oldIndex);
            return true;
        }

        // 全新状态，插入堆尾后向上调整
        int last = heap.size();
        heap.add(node);
        indexMap.put(state, last);
        siftUp(last);

        return true;
    }

    @Override
    public Node poll() {
        if (heap.isEmpty()) {
            return null;
        }

        // 堆顶是评价值最小的节点
        Node best = heap.get(0);
        indexMap.remove(best.getState());

        // 用最后一个节点填补堆顶
        Node last = heap.remove(heap.size() - 1);

        if (!heap.isEmpty()) {
            heap.set(0, last);
            indexMap.put(last.getState(), 0);
            siftDown(0);
        }

        return best;
    }

    @Override
    public int size() {
        return heap.size();
    }

    @Override
    public boolean isEmpty() {
        return heap.isEmpty();
    }

    @Override
    public void clear() {
        heap.clear();
        indexMap.clear();
    }

    /**
     * 替换节点后恢复最小堆性质。
     */
    private void fixHeap(int i) {
        if (i > 0 &&
                evaluator.compare(
                        heap.get(i),
                        heap.get((i - 1) / 2)) < 0) {
            siftUp(i);
        } else {
            siftDown(i);
        }
    }

    /**
     * 节点向上移动，直到满足堆序。
     */
    private void siftUp(int i) {
        Node moving = heap.get(i);

        while (i > 0) {
            int parent = (i - 1) / 2;
            Node parentNode = heap.get(parent);

            if (evaluator.compare(moving, parentNode) >= 0) {
                break;
            }

            heap.set(i, parentNode);
            indexMap.put(parentNode.getState(), i);
            i = parent;
        }

        heap.set(i, moving);
        indexMap.put(moving.getState(), i);
    }

    /**
     * 节点向下移动，直到满足堆序。
     */
    private void siftDown(int i) {
        Node moving = heap.get(i);
        int n = heap.size();

        while (2 * i + 1 < n) {
            int child = 2 * i + 1;
            int right = child + 1;

            // 找出两个子节点中优先级更高的那个
            if (right < n &&
                    evaluator.compare(
                            heap.get(right),
                            heap.get(child)) < 0) {
                child = right;
            }

            Node childNode = heap.get(child);

            if (evaluator.compare(moving, childNode) <= 0) {
                break;
            }

            heap.set(i, childNode);
            indexMap.put(childNode.getState(), i);
            i = child;
        }

        heap.set(i, moving);
        indexMap.put(moving.getState(), i);
    }
}
