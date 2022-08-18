package com.funguscow.gb;


import org.junit.jupiter.api.Test;

public class HeapTest {

    @Test
    public void testSort() {
        Scheduler.Heap heap = new Scheduler.Heap();
        Scheduler.Task a = new Scheduler.Task(null, null, 0);
        Scheduler.Task b = new Scheduler.Task(null, null, 10);
        Scheduler.Task c = new Scheduler.Task(null, null, 5);
        Scheduler.Task d = new Scheduler.Task(null, null, 3);
        Scheduler.Task e = new Scheduler.Task(null, null, 9);
        heap.add(a);
        heap.add(b);
        heap.add(c);
        heap.add(d);
        heap.add(e);
        Scheduler.Task outA = heap.pop();
        Scheduler.Task outB = heap.pop();
        Scheduler.Task outC = heap.pop();
        Scheduler.Task outD = heap.pop();
        Scheduler.Task outE = heap.pop();
        assert(outA == a);
        assert(outB == d);
        assert(outC == c);
        assert(outD == e);
        assert(outE == b);
    }

    @Test
    public void testRemoval() {
        Scheduler.Heap heap = new Scheduler.Heap();
        Scheduler.Task a = new Scheduler.Task(null, null, 0);
        Scheduler.Task b = new Scheduler.Task(null, null, 10);
        Scheduler.Task c = new Scheduler.Task(null, null, 5);
        Scheduler.Task d = new Scheduler.Task(null, null, 3);
        Scheduler.Task e = new Scheduler.Task(null, null, 9);
        heap.add(a);
        heap.add(b);
        heap.add(c);
        heap.add(d);
        heap.add(e);
        System.out.printf("%d %d %d %d %d\n", a.index, b.index, c.index, d.index, e.index);
        heap.remove(c);
        System.out.printf("%d %d %d %d %d\n", a.index, b.index, c.index, d.index, e.index);
        Scheduler.Task outA = heap.pop();
        System.out.printf("%d %d %d %d %d\n", a.index, b.index, c.index, d.index, e.index);
        Scheduler.Task outB = heap.pop();
        System.out.printf("%d %d %d %d %d\n", a.index, b.index, c.index, d.index, e.index);
        Scheduler.Task outC = heap.pop();
        System.out.printf("%d %d %d %d %d\n", a.index, b.index, c.index, d.index, e.index);
        Scheduler.Task outD = heap.pop();
        System.out.printf("%d %d %d %d %d\n", a.index, b.index, c.index, d.index, e.index);
        assert(outA == a);
        assert(outB == d);
        assert(outC == e);
        assert(outD == b);
    }

    @Test
    public void testSimilar() {
        Scheduler.Heap heap = new Scheduler.Heap();
        Scheduler.Task a = new Scheduler.Task(null, null, 0);
        Scheduler.Task b = new Scheduler.Task(null, null, 10);
        Scheduler.Task c = new Scheduler.Task(null, null, 3);
        Scheduler.Task d = new Scheduler.Task(null, null, 3);
        Scheduler.Task e = new Scheduler.Task(null, null, 9);
        heap.add(a);
        heap.add(b);
        heap.add(c);
        heap.add(d);
        heap.add(e);
        System.out.printf("%d %d %d %d %d\n", a.index, b.index, c.index, d.index, e.index);
        Scheduler.Task outA = heap.pop();
        System.out.printf("%d %d %d %d %d\n", a.index, b.index, c.index, d.index, e.index);
        Scheduler.Task outB = heap.pop();
        System.out.printf("%d %d %d %d %d\n", a.index, b.index, c.index, d.index, e.index);
        Scheduler.Task outC = heap.pop();
        System.out.printf("%d %d %d %d %d\n", a.index, b.index, c.index, d.index, e.index);
        Scheduler.Task outD = heap.pop();
        System.out.printf("%d %d %d %d %d\n", a.index, b.index, c.index, d.index, e.index);
        Scheduler.Task outE = heap.pop();
        System.out.printf("%d %d %d %d %d\n", a.index, b.index, c.index, d.index, e.index);
    }

}
