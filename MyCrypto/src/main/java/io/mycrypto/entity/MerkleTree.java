package io.mycrypto.entity;

import io.mycrypto.repository.KeyValueRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MerkleTree {

    private final static long TRANSACTION_POOL_SIZE_LIMIT = 5; // num of transactions
    @Autowired
    KeyValueRepository<String, String> rocksDb;
    String merkleRoot; // hash
    Node parent;

    public MerkleTree() {

    }

    private class Node {
        String hash;
        Transaction tx;
    }
}
