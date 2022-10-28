package io.mycrypto.entity;

import io.mycrypto.repository.KeyValueRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MerkleTree {

    @Autowired
    KeyValueRepository<String, String> rocksDb;

    private final static long TRANSACTION_POOL_SIZE_LIMIT = 5; // ~ 5 KB

    String merkleRoot; // hash
    Node parent;
    private class Node {
        String hash;
        Transaction tx;
    }

    public MerkleTree() {

    }
}
