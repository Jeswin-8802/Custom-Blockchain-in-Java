package io.mycrypto.entity;

import io.mycrypto.repository.KeyValueRepository;
import io.mycrypto.repository.RocksDBRepositoryImpl;
import io.mycrypto.util.Utility;
import lombok.Data;
import lombok.ToString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigInteger;
import java.util.List;


@Data
@ToString
public class Block {

    private static final Logger log = LogManager.getLogger(Block.class);

    private static final long REWARD = 100; // 100 satoshi

    private static final String DEFAULT_WALLET_NAME = "default";

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
        String calculated_hash = Utility.getHash(previousHash + height + timeStamp + tx.toString() + numTx + merkleRoot + getNonce() + difficulty);
        if (this.hash == null) {
            setHash(calculated_hash);
            return this.hash;
        }
        return calculated_hash;
    }

    public void mineBlock(KeyValueRepository db) {
        String target = new String(new char[difficulty]).replace('\0', '0'); //Create a string with difficulty * "0"
        while(!hash.substring(0, difficulty).equals(target)) {
            nonce ++;
            hash = calculateHash();
        }
        log.info("Block Mined!!! : " + hash);

        log.info("Crediting {} satoshis to your default wallet. Good Job!", REWARD);
        String walletInfo = (String) db.find(DEFAULT_WALLET_NAME, "Wallets");
        db.delete(DEFAULT_WALLET_NAME, "Wallets");
        String info[] = walletInfo.split("- ");
        info[2] = (new BigInteger(info[2]).add(new BigInteger(String.valueOf(REWARD)))).toString();
        log.info("UTXO ==> {}", info[2]);
        db.save(DEFAULT_WALLET_NAME, String.join("- ", info), "Wallets");
    }
}
