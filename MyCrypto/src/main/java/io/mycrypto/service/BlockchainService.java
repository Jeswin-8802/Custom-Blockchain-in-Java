package io.mycrypto.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.mycrypto.entity.Block;
import io.mycrypto.repository.KeyValueRepository;
import io.mycrypto.util.Utility;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.*;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Objects;

@Slf4j
@Service
public class BlockchainService {

    @Autowired
    KeyValueRepository<String, String> rocksDB;

    private static final String BLOCKCHAIN_STORAGE_PATH = "C:\\REPO\\Github\\blockchain";

    private static final String GENESIS_BLOCK = "blk000000000001";

    private static final String MAGIC_BYTES = "f9beb4d9"; // help you spot when a new message starts when sending or receiving a continuous stream of data


    private static final long REWARD = 100; // 100 satoshi

    private static final String DEFAULT_WALLET_NAME = "default";


    public String createGenesisBlock() {
        Block genesis = new Block();
        genesis.setPreviousHash("0");
        genesis.setHeight(0);
        genesis.setTx(new ArrayList<>());
        genesis.setNumTx(0);
        genesis.setMerkleRoot("0");
        log.info("Hash of genesis block ==> {}", genesis.calculateHash());
        log.info("mining the genesis block...");
        genesis.mineBlock();
        rewardMiningEffort();

        String json = null;

        try {
            ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
            json = ow.writeValueAsString(genesis);
            log.info("{} ==> \n{}", GENESIS_BLOCK + ".dat", json);
        } catch (JsonProcessingException ex) {
            log.error("Error occurred while parsing Object(Block) to json \nexception: {}, message: {}, stackTrace: {}", ex.getCause(), ex.getMessage(), ex.getStackTrace());
        }

        rocksDB.save(genesis.getHash(), BLOCKCHAIN_STORAGE_PATH + "\\" + GENESIS_BLOCK + ".dat", "Blockchain");

        try {
            DataOutputStream out = new DataOutputStream(new FileOutputStream(BLOCKCHAIN_STORAGE_PATH + "\\" + GENESIS_BLOCK + ".dat"));
            out.writeUTF(MAGIC_BYTES + Base64.getEncoder().withoutPadding().encodeToString(Objects.requireNonNull(json).getBytes()));
            out.close();
        } catch (FileNotFoundException ex) {
            log.error("Error occurred while creating {} at location {} \nexception: {}, message: {}, stackTrace: {}", GENESIS_BLOCK, BLOCKCHAIN_STORAGE_PATH, ex.getCause(), ex.getMessage(), ex.getStackTrace());
        } catch (IOException ex) {
            log.error("Error occurred while creating DataOutputStream \nexception: {}, message: {}, stackTrace: {}", ex.getCause(), ex.getMessage(), ex.getStackTrace());
        }

        return json;
    }

    private void rewardMiningEffort() {
        log.info("Crediting {} satoshis to your default wallet. Good Job!", REWARD);
        String walletInfo = rocksDB.find(DEFAULT_WALLET_NAME, "Wallets");
        rocksDB.delete(DEFAULT_WALLET_NAME, "Wallets");
        String info[] = walletInfo.split("- ");
        info[2] = (new BigInteger(info[2]).add(new BigInteger(String.valueOf(REWARD)))).toString();
        log.info("UTXO ==> {}", info[2]);
        rocksDB.save(DEFAULT_WALLET_NAME, String.join("- ", info), "Wallets");
    }

    public String fetchBlockContent(String hash) {
        String path = rocksDB.find(hash, "Blockchain");
        try {
            DataInputStream in = new DataInputStream(new FileInputStream(path));
            boolean eof = false;
            String result = "";
            while (!eof) {
                try {
                    result += in.readUTF();
                } catch (EOFException ex) {
                    log.warn("End of file reached...");
                    eof = true;
                }
            }
            log.info("File content in HEX ==> {}", result);
            return new String(Base64.getDecoder().decode(result.substring(MAGIC_BYTES.length())));
        } catch (FileNotFoundException ex) {
            log.error("Error occurred while creating {} at location {} \nexception: {}, message: {}, stackTrace: {}", GENESIS_BLOCK, BLOCKCHAIN_STORAGE_PATH, ex.getCause(), ex.getMessage(), ex.getStackTrace());
        } catch (IOException ex) {
            log.error("Error occurred while creating DataOutputStream \nexception: {}, message: {}, stackTrace: {}", ex.getCause(), ex.getMessage(), ex.getStackTrace());
        }
        return null;
    }

    public String fetchBlockPath(String hash) {
        return rocksDB.find(hash, "Blockchain");
    }
}
