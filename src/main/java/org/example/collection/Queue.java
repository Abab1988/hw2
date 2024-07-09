package org.example.collection;

public interface Queue<T> {
    void enq(T item);

    T deq();
}
