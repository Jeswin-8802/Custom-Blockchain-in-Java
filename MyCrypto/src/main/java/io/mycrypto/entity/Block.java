package io.mycrypto.entity;

import java.util.List;

public class Block {

    public String hash; // Block Identifier
    private String previousHash; // Represents hash of the previous block
    private long height; // The current block height is simply the number of blocks in the blockchain minus one
    private long timeStamp; // Timestamp when the block is created
    private List<String> tx; // List of hash values of the transactions included in the block
    private long numTx; // Number of transactions within the block
    private String merkleRoot; // Merkle root represents the hash of the root of merkle tree created as a result of combining hash of children nodes
    private long nonce; // Counter which helped achieve the difficulty target
    private double difficulty; // Represents difficult target

}
