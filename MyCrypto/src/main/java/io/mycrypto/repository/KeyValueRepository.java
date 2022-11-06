package io.mycrypto.repository;

import java.util.Map;

public interface KeyValueRepository<K, V> {

    void save(K key, V value, String db);

    V find(K key, String db);

    //to be used only when orphan blocks are detected (will only be applicable locally)
    boolean delete(K key, String db);

    Map<String, String> getList(String db);
}
