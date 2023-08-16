package io.mycrypto.repository;

import java.util.Map;

public interface KeyValueRepository<K, V> {

    void save(K key, V value, DbName db);

    V find(K key, DbName db);

    //to be used only when orphan blocks are detected (will only be applicable locally)
    boolean delete(K key, DbName db);

    Map<String, String> getList(DbName db);

    long getCount(DbName db);
}
