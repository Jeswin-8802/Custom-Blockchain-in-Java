package io.mycrypto.entity;

import io.mycrypto.util.Utility;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.List;


@Slf4j
@Data
@ToString
public class Block {

    String blockOwner; // The hash of the node that mined the block (This is noted down so that the rewards for mining are transferred to this hash)
    String hash; // Block Identifier
    String previousHash; // Represents hash of the previous block
    long height; // The current block height is simply the number of blocks in the blockchain minus one
    long timeStamp; // Timestamp when the block is created
    List<String> tx; // List of hash values of the transactions included in the block
    long numTx; // Number of transactions within the block
    String merkleRoot; // Merkle root represents the hash of the root of merkle tree created as a result of combining hash of children nodes
    long nonce; // Counter which helped achieve the difficulty target
    int difficulty; // Represents difficult target


    public String calculateHash() {
        String calculated_hash = Utility.getHashSHA256(previousHash + height + timeStamp + tx.toString() + numTx + merkleRoot + getNonce() + difficulty);
        if (this.hash == null) {
            setNonce(0);
            setDifficulty(3);
            setTimeStamp(new Date().getTime());
            setHash(calculated_hash);
            return this.hash;
        }
        return calculated_hash;
    }

    public void mineBlock(String hashOfMiner) {
        String target = new String(new char[difficulty]).replace('\0', '0'); //Create a string with difficulty * "0"
        while (!hash.substring(0, difficulty).equals(target)) {
            nonce++;
            hash = calculateHash();
        }
        log.info("Block Mined!!! : " + hash);
        setBlockOwner(hashOfMiner);
    }
}
