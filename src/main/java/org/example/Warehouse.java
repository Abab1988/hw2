package org.example;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.example.collection.BoundedQueue;
import org.example.collection.Queue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Log4j2
@Getter
public class Warehouse extends Thread {

    private final int truckCount = 10;

    private int fabricStorageIndex = 0;

    private final List<Block> storage = new ArrayList<>();

    // Для реализации паттерна Producer-Consumer используем очередь фиксированной длины, так как число грузовиков известно заранее
    private final Queue<Truck> trucks = new BoundedQueue<>(truckCount);

    private final Lock lock = new ReentrantLock();

    public Warehouse(String name) {
        super(name);
    }

    public Warehouse(String name, Collection<Block> initialStorage) {
        this(name);
        storage.addAll(initialStorage);
    }

    @Override
    public void run() {
        Truck truck;
        while (!currentThread().isInterrupted()) {
            truck = getNextArrivedTruck();
            if (truck == null) {
                try {
                    sleep(100);
                } catch (InterruptedException e) {
                    if (currentThread().isInterrupted()) {

                        break;
                    }
                }
                continue;
            }
            if (truck.getBlocks().isEmpty()) {
                loadTruck(truck);
            } else {
                unloadTruck(truck);
            }
            // Отпускаем грузовик после погрузки/разгрузки
            synchronized (truck) {
                truck.notifyAll();
            }
        }
        log.info("Warehouse thread interrupted");

    }

    private void loadTruck(Truck truck) {
        log.info("Loading truck {}", truck.getName());
        Collection<Block> blocksToLoad = getFreeBlocks(truck.getCapacity());
        try {
            sleep(10L * blocksToLoad.size());
        } catch (InterruptedException e) {
            log.error("Interrupted while loading truck", e);
        }
        truck.getBlocks().addAll(blocksToLoad);
        log.info("Truck loaded {}", truck.getName());
    }

    private Collection<Block> getFreeBlocks(int maxItems) {
        List<Block> blocks = new ArrayList<>();
        // Блокирую всю коллекцию на время погрузки
        lock.lock();
        try {
            int left = fabricStorageIndex;
            int right = fabricStorageIndex + maxItems;
            for (int i = left; i < right; i++) {
                blocks.add(storage.get(i));
                // Сдвигаю указатель на очередной блок
                fabricStorageIndex++;
            }
        } finally {
            lock.unlock();
        }
        return blocks;
    }

    private synchronized void returnBlocksToStorage(List<Block> returnedBlocks) {
        // Блокирую всю коллекцию на время разгрузки
        storage.addAll(returnedBlocks);
    }

    private void unloadTruck(Truck truck) {
        log.info("Unloading truck {}", truck.getName());
        List<Block> arrivedBlocks = truck.getBlocks();
        try {
            sleep(10L * arrivedBlocks.size());
        } catch (InterruptedException e) {
            log.error("Interrupted while unloading truck", e);
        }
        returnBlocksToStorage(arrivedBlocks);
        truck.getBlocks().clear();
        log.info("Truck unloaded {}", truck.getName());
    }

    private Truck getNextArrivedTruck() {
        // Берем очередной грузовик из очереди для погрузки/разгрузки
        return trucks.deq();
    }

    public void arrive(Truck truck) {
        try {
            trucks.enq(truck);
            synchronized (truck) {
                // ждем пока погрузят/разгрузят блоки
                truck.wait();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException();
        }
    }
}
