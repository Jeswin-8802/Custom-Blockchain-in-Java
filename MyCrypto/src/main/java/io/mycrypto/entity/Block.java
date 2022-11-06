package io.mycrypto.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.mycrypto.util.Utility;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.util.Date;
import java.util.List;


@Slf4j
@Data
@ToString
public class Block {
    @JsonProperty("block-owner")
    String blockOwner; // The hash of the node that mined the block (This is noted down so that the rewards for mining are transferred to this hash)
    @JsonProperty("hash")
    String hash; // Block Identifier
    @JsonProperty("previous-block-hash")
    String previousHash; // Represents hash of the previous block
    @JsonProperty("height")
    long height; // The current block height is simply the number of blocks in the blockchain minus one
    @JsonProperty("time-stamp")
    long timeStamp; // Timestamp when the block is created
    @JsonProperty("number-of-transactions")
    long numTx; // Number of transactions within the block
    @JsonProperty("tx")
    List<String> transactionIds; // List of hash values of the transactions included in the block

    @JsonProperty("transactions")
    List<Transaction> transactions;
    @JsonProperty("merkle-root")
    String merkleRoot; // Merkle root represents the hash of the root of merkle tree created as a result of combining hash of children nodes
    @JsonProperty("nonce")
    long nonce; // Counter which helped achieve the difficulty target
    @JsonProperty("difficulty")
    int difficulty; // Represents difficult target
    @JsonProperty("size")
    BigInteger size; // block size in bytes (will be set after mining; won't be included in calculating the hash)
    @JsonProperty("weight")
    BigInteger weight;

    public String calculateHash() {
        String calculated_hash = Utility.getHashSHA256(previousHash + height + timeStamp + transactionIds.toString() + transactions + numTx + merkleRoot + getNonce() + difficulty);
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
