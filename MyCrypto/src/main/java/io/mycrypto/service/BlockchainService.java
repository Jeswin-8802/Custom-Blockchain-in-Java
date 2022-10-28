package io.mycrypto.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.mycrypto.dto.CreateWalletRequestDto;
import io.mycrypto.dto.WalletResponseDto;
import io.mycrypto.entity.Block;
import io.mycrypto.repository.KeyValueRepository;
import io.mycrypto.util.Utility;
import lombok.extern.slf4j.Slf4j;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.io.*;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
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



    public ResponseEntity<Object> createGenesisBlock() {
        Block genesis = new Block();
        genesis.setPreviousHash("0");
        genesis.setHeight(0);
        genesis.setTx(new ArrayList<>());
        genesis.setNumTx(0);
        genesis.setMerkleRoot("0");
        log.info("Hash of genesis block ==> {}", genesis.calculateHash());
        log.info("mining the genesis block...");
        genesis.mineBlock();
        try {
            if (!rewardMiningEffort())
                return new ResponseEntity<>(new JSONParser().parse("""
                        {
                            "error": "unable to give rewards for mining"
                        }
                        """), HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (ParseException e) {
            log.error("error occurred when trying to reward mining effort\n {}", e.getStackTrace());
        }

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

        try {
            return ResponseEntity.ok(new JSONParser().parse(json));
        } catch (ParseException ignored) {
            log.warn(ignored.toString());
        }
        return null;
    }

    private boolean rewardMiningEffort() {
        log.info("Crediting {} satoshis to your default wallet. Good Job!", REWARD);
        String walletInfo = rocksDB.find(DEFAULT_WALLET_NAME, "Wallets");
        if (ObjectUtils.isEmpty(walletInfo))
            return false;
        rocksDB.delete(DEFAULT_WALLET_NAME, "Wallets");
        String info[] = walletInfo.split("- ");
        info[2] = (new BigInteger(info[2]).add(new BigInteger(String.valueOf(REWARD)))).toString();
        log.info("UTXO ==> {}", info[2]);
        rocksDB.save(DEFAULT_WALLET_NAME, String.join("- ", info), "Wallets");
        return true;
    }

    public JSONObject fetchBlockContent(String hash) {
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
            try {
                return (JSONObject) new JSONParser().parse(new String(Base64.getDecoder().decode(result.substring(MAGIC_BYTES.length()))));
            } catch (ParseException ex) {
                log.error("Error occurred while parsing String to JSON \nexception: {}, message: {}, stackTrace: {}", ex.getCause(), ex.getMessage(), ex.getStackTrace());
            }
        } catch (FileNotFoundException ex) {
            log.error("Error occurred while creating {} at location {} \nexception: {}, message: {}, stackTrace: {}", GENESIS_BLOCK, BLOCKCHAIN_STORAGE_PATH, ex.getCause(), ex.getMessage(), ex.getStackTrace());
        } catch (IOException ex) {
            log.error("Error occurred while creating DataOutputStream \nexception: {}, message: {}, stackTrace: {}", ex.getCause(), ex.getMessage(), ex.getStackTrace());
        }
        return null;
    }

    public ResponseEntity<Object> fetchBlockPath(String hash) {
        String path = rocksDB.find(hash, "Blockchain");
        String response = """
                {
                    "path-where-block-is-stored": """ + "\"" + (ObjectUtils.isEmpty(path) ? "Unable to find block with hash " + hash :  path) + "\"" + """
                }
                """;
        try {
            JSONParser parser = new JSONParser();
            return ResponseEntity.ok(parser.parse(response));
        } catch (ParseException ex) {
            log.error("Error occurred while parsing String to JSON \nexception: {}, message: {}, stackTrace: {}", ex.getCause(), ex.getMessage(), ex.getStackTrace());
        }
        return null;
    }

    public ResponseEntity<WalletResponseDto> createWallet(CreateWalletRequestDto request) {
        String[] value = Objects.requireNonNull(Utility.generateKeyPairToFile()).split("- ");
        rocksDB.save(request.getWalletName(), String.join("- ", value), "Wallets");
        WalletResponseDto response = new WalletResponseDto();
        response.setPublicKey(value[0] + "-");
        response.setPrivateKey(value[1] + "-");
        response.setUTXO(new BigInteger(value[2]));
        return ResponseEntity.ok(response);
    }

    public ResponseEntity<WalletResponseDto> fetchWalletInfo(String walletName) {
        String result = rocksDB.find(walletName, "Wallets");
        if (result == null) return ResponseEntity.noContent().build();
        String[] value = result.split("- ");
        WalletResponseDto response = new WalletResponseDto();
        response.setPublicKey(value[0] + "-");
        response.setPrivateKey(value[1] + "-");
        response.setUTXO(new BigInteger(value[2]));
        return ResponseEntity.ok(response);
    }

    public ResponseEntity<Object> delete(String key, String db) {
        if (!rocksDB.delete(key, db))
            return ResponseEntity.badRequest().build();
        return ResponseEntity.ok().build();
    }
}
