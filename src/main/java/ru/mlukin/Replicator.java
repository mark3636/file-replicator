package ru.mlukin;

public interface Replicator {
    /**
     * Starts directory replication
     */
    void start();

    /**
     * Stops directory replication
     */
    void stop();
}
