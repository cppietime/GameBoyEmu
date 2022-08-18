package com.funguscow.gb;

import java.util.ArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class Scheduler {

    public interface UncheckedBiConsumer<T, S> {
        void accept(T t, S s) throws Exception;
    }

    /**
     * Represents a task scheduled to be run
     */
    public static class Task {
        public UncheckedBiConsumer<Long, Object> callback;
        public Object argument;
        public long targetTime;
        public int index;

        /**
         * Index is automatically set to -1, indicating not yet scheduled or already expired
         *
         * Callback takes as arguments the time at which the task fires, and the argument
         * attached to this task, which may be null
         *
         * @param callback Function callback called upon execution
         * @param argument Argument object passed to callback
         * @param targetTime Time in cycles at which to execute the task
         */
        public Task(UncheckedBiConsumer<Long, Object> callback, Object argument, long targetTime) {
            this.callback = callback;
            this.argument = argument;
            this.targetTime = targetTime;
            this.index = -1;
        }

        /**
         * Executes this task
         */
        public void execute() throws Exception {
            callback.accept(targetTime, argument);
        }
    }

    /**
     * Priority queue of pending tasks
     */
    public static class Heap {

        public static Task lookout = null;

        public ArrayList<Task> heap = new ArrayList<>();

        /**
         * Swap the tasks at a and b, updating their indices
         * @param a Index of task
         * @param b Index of task
         */
        private void swap(int a, int b) {
            Task ta = heap.get(a);
            Task tb = heap.get(b);
            ta.index = b;
            tb.index = a;
            heap.set(a, tb);
            heap.set(b, ta);
        }

        private void siftUp(Task task) {
            int parent = (task.index - 1) >> 1;
            while (task.index > 0 && task.targetTime < heap.get(parent).targetTime) {
                swap(task.index, parent);
                parent = (task.index - 1) >> 1;
            }
        }

        private void siftDown(Task task) {
            int i = task.index;
            int l, r;
            do {
                if (i != task.index) {
                    swap(i, task.index);
                }
                l = (i << 1) + 1;
                r = l + 1;
                if (l < heap.size() && heap.get(l).targetTime < task.targetTime) {
                    i = l;
                }
                if (r < heap.size() && heap.get(r).targetTime < heap.get(i).targetTime) {
                    i = r;
                }
            } while (i != task.index);
        }

        /**
         * Add a yet unscheduled task to the heap in its proper spot and set its index
         * @param task Task to add
         * @return task
         */
        public Task add(Task task) {
            if (task == null || task.index >= 0) {
                return task;
            }
            task.index = heap.size();
            heap.add(task);
            siftUp(task);
            return task;
        }

        /**
         * Remove the task at a specified index and maintain the heap invariant
         * @param index Index of task to remove
         */
        public void remove(int index) {
            if (index < 0) {
                return;
            }
            Task interest = heap.get(heap.size() - 1);
            swap(interest.index, index);
            heap.get(heap.size() - 1).index = -1;
            heap.remove(heap.size() - 1);
            if (index > 0 && interest.targetTime < heap.get((index - 1) >> 1).targetTime) {
                siftUp(interest);
            } else if (interest.index > -1) {
                siftDown(interest);
            }
        }

        /**
         * Remove the provided task and maintain the heap invariant
         * Because each task holds its index, this runs in O(log(n))
         * @param task Task to remove
         */
        public void remove(Task task) {
            remove(task.index);
            task.index = -1;
        }

        /**
         * Get the head element of the heap
         * @return Heap's head
         */
        public Task head() {
            return heap.get(0);
        }

        /**
         *
         * @return True when the underlying array is empty
         */
        public boolean isEmpty() {
            return heap.isEmpty();
        }

        /**
         * Remove and return the least element currently on the heap
         * @return The previously least element
         */
        public Task pop() {
            Task t = head();
            remove(0);
            return t;
        }
    }

    private long cyclesExecuted;
    private final Heap heap = new Heap();

    /**
     * Execute all ready tasks
     * @param cycles Number of cycles since the last time it was called
     */
    public void update(int cycles) throws Exception {
        cyclesExecuted += cycles;
        while (!heap.isEmpty() && heap.head().targetTime <= cyclesExecuted) {
            Task task = heap.pop();
            task.execute();
        }
    }

    /**
     * Skip ahead to the next task
     * Use this for HALTing
     * @param currentCycles Current timestamp to start skipping from
     * @return The new timestamp after skipping
     */
    public long skip(long currentCycles) throws Exception {
        if (heap.isEmpty()) {
            return currentCycles;
        }
        Task task = heap.pop();
        task.execute();
        cyclesExecuted = task.targetTime;
        while (!heap.isEmpty() && heap.head().targetTime <= cyclesExecuted) {
            task = heap.pop();
            task.execute();
        }
        return cyclesExecuted;
    }

    /**
     *
     * @param task Schedule this task
     * @return task
     */
    public Task add(Task task) {
        return heap.add(task);
    }

    /**
     *
     * @param task Cancel this task
     */
    public void cancel(Task task) {
        if (task != null) {
            heap.remove(task);
        }
    }

    /**
     *
     * @return The number of executed cycles this scheduler has kept track of
     */
    public long getCyclesExecuted() {
        return cyclesExecuted;
    }

    /**
     * Totally wipe out all scheduled events
     */
    public void clear() {
        heap.heap.forEach(t -> t.index = -1);
        heap.heap.clear();
    }

    /**
     *
     * @return true iff the underlying task heap is empty
     */
    public boolean isEmpty() {
        return heap.isEmpty();
    }
}
