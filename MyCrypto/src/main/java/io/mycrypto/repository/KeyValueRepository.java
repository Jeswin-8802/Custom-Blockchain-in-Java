package io.mycrypto.repository;

public interface KeyValueRepository<K, V> {

    void save(K key, V value, String db);

    V find(K key, String db);

    //to be used only when orphan blocks are detected (will only be applicable locally)
    boolean delete(K key, String db);
}
