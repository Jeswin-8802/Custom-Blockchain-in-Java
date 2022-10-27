package io.mycrypto.repository;

public interface KeyValueRepository<K, V> {

    void save(K key, V value);

    V find(K key);

    //to be used only when orphan blocks are detected (will only be applicable locally)
    void delete(K key);
}
