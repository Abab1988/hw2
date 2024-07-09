package org.example.collection;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
/**
 Скопировано из материалов лекции
 */
public class BoundedQueue<T> implements Queue<T> {

    ReentrantLock enqLock, deqLock;
    Condition notEmptyCondition, notFullCondition;
    AtomicInteger size;
    volatile Node<T> head, tail;
    final int capacity;


    public BoundedQueue(final int capacity) {
        this.capacity = capacity;
        head = new Node<>(null);
        tail = head;
        size = new AtomicInteger(0);
        enqLock = new ReentrantLock();
        notFullCondition = enqLock.newCondition();
        deqLock = new ReentrantLock();
        notEmptyCondition = deqLock.newCondition();
    }

    @Override
    public void enq(T item) {
        Node<T> e = new Node<>(item);
        enqLock.lock();
        boolean needSignal = false;
        try {
            while (size.get() == capacity) {
                try {
                    notFullCondition.await();
                } catch (InterruptedException ex) {
                    //
                }
            }
            tail.next = e;
            tail = e;
            if (size.getAndIncrement() == 0) {
                needSignal = true;
            }

        } finally {
            enqLock.unlock();
        }

        if (needSignal) {
            deqLock.lock();
            try {
                notEmptyCondition.signalAll();
            } finally {
                deqLock.unlock();
            }
        }

    }

    @Override
    public T deq() {
        T result;
        deqLock.lock();
        boolean needSignal = false;
        try {
            while (head.next == null) {
                try {
                    notEmptyCondition.await();
                } catch (InterruptedException e) {
                    //
                }
            }
            result = head.next.value;
            head = head.next;
            if (size.getAndDecrement() == capacity) {
                needSignal = true;
            }

        } finally {
            deqLock.unlock();
        }

        if (needSignal) {
            enqLock.lock();
            try {
                notFullCondition.signalAll();
            } finally {
                enqLock.unlock();
            }
        }
        return result;
    }


    private static class Node<T> {
        T value;
        volatile Node<T> next;

        public Node(T value) {
            this.value = value;
            this.next = null;
        }
    }
}
