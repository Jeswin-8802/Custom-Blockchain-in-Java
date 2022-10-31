package io.mycrypto.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.mycrypto.dto.CreateWalletRequestDto;
import io.mycrypto.dto.VerifyAddressRequestDto;
import io.mycrypto.dto.WalletResponseDto;
import io.mycrypto.entity.Block;
import io.mycrypto.entity.Transaction;
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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

@Slf4j
@Service
public class BlockchainService {

    private static final String BLOCKCHAIN_STORAGE_PATH = "C:\\REPO\\Github\\blockchain";
    private static final String GENESIS_BLOCK = "blk000000000001";
    private static final String MAGIC_BYTES = "f9beb4d9"; // help you spot when a new message starts when sending or receiving a continuous stream of data
    private static final String REWARD = "100"; // 100 satoshi
    private static final String DEFAULT_WALLET_NAME = "default";
    private static final String P2PKH_PREFIX = "1"; // P2PKH (address starts with the number “1”); P2SH (address starts with the number “3”); Bech32 (address starts with “bc1”)
    @Autowired
    KeyValueRepository<String, String> rocksDB;

    private String saveBlock(Block blk, String path) {
        String json = constructJSON(blk, GENESIS_BLOCK + ".dat");

        rocksDB.save(blk.getHash(), BLOCKCHAIN_STORAGE_PATH + "\\" + path + ".dat", "Blockchain");

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

    private boolean updateDB(String walletName, String walletInfo, String satoshis, boolean op) {
        rocksDB.delete(walletName, "Wallets");
        String[] info = walletInfo.split("- ");
        log.info("UTXO present ==> {}", info[2]);
        BigInteger toSpend = new BigInteger(info[2]), existing = new BigInteger(satoshis);
        if (op)
            info[2] = existing.add(toSpend).toString();
        else {
            if (existing.max(toSpend).equals(toSpend) && !existing.equals(toSpend))
                return false;
            info[2] = existing.subtract(toSpend).toString();
        }
        log.info("UTXO updated ==> {}", info[2]);
        rocksDB.save(walletName, String.join("- ", info), "Wallets");
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
                    "path-where-block-is-stored": """ + "\"" + (ObjectUtils.isEmpty(path) ? "Unable to find block with hash " + hash : path) + "\"" + """
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
        log.info("\n {}", request.getWalletName());
        rocksDB.save(request.getWalletName(), String.join("- ", value), "Wallets");
        WalletResponseDto response = new WalletResponseDto();
        response.setPublicKey(value[0].replace("\n", "") + "-");
        response.setPrivateKey(value[1].replace("\n", "") + "-");
        response.setUTXO(new BigInteger(value[2]));
        return ResponseEntity.ok(response);
    }

    public ResponseEntity<WalletResponseDto> fetchWalletInfo(String walletName) {
        String result = rocksDB.find(walletName, "Wallets");
        log.info(walletName);
        log.info(result);
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

    public ResponseEntity<Object> createGenesisBlock() {
        Block genesis = new Block();
        genesis.setPreviousHash("0");
        genesis.setHeight(0);
        genesis.setTx(new ArrayList<>());
        genesis.setNumTx(0);
        genesis.setMerkleRoot("0");
        log.info("Hash of genesis block ==> {}", genesis.calculateHash());
        log.info("mining the genesis block...");
        genesis.mineBlock(bitcoinAddressConstructor(getPublicKeyFromWalletInfo(rocksDB.find("default", "Wallets"))));

        try {
            return ResponseEntity.ok(new JSONParser().parse(saveBlock(genesis, GENESIS_BLOCK)));
        } catch (ParseException ignored) {
            log.warn(ignored.toString());
        }
        return null;
    }

    public ResponseEntity<Object> constructResponseForValidateAddress(VerifyAddressRequestDto request) {
        String jsonResponse = """
                {
                    "msg": %s
                }
                """;
        try {
            if (verifyAddress(request.getAddress(), request.getPublicKey()))
                return new ResponseEntity<>(new JSONParser().parse(String.format(jsonResponse, "valid")), HttpStatus.OK);
            else
                return new ResponseEntity<>(new JSONParser().parse(String.format(jsonResponse, "invalid")), HttpStatus.OK);
        } catch (ParseException e) {
            e.printStackTrace();
            ;
        }
        return ResponseEntity.internalServerError().build();
    }

    public String bitcoinAddressConstructor(String publicKey) {
        return P2PKH_PREFIX + Utility.encodeBase58(Utility.hash160(publicKey) + getChecksum(publicKey));
    }

    private String getChecksum(String publicKey) {
        return Arrays.toString(Arrays.copyOfRange(Objects.requireNonNull(Utility.getHashSHA384(Objects.requireNonNull(Utility.getHashSHA384(publicKey)))).getBytes(), 0, 4));
    }

    private String constructJSON(Object obj, String msg) {
        try {
            ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
            String json = ow.writeValueAsString(obj);
            log.info("{} ==> \n{}", msg, json);
            return json;
        } catch (JsonProcessingException ex) {
            log.error("Error occurred while parsing Object(Block) to json \nexception: {}, message: {}, stackTrace: {}", ex.getCause(), ex.getMessage(), ex.getStackTrace());
        }
        return null;
    }

    private String getPublicKeyFromWalletInfo(String walletInfo) {
        return walletInfo.split("\s")[0];
    }

    private String getPrivateKeyFromWalletInfo(String walletInfo) {
        return walletInfo.split("\s")[1];
    }

    public void makeTransaction() {
        Transaction t1 = new Transaction(), t2 = new Transaction();
        String walletAinfo = rocksDB.find("A", "Wallets"), walletBinfo = rocksDB.find("B", "Wallets");
        String walletA = bitcoinAddressConstructor(getPublicKeyFromWalletInfo(walletAinfo));
        log.info("WalletA ==> {}", walletA);
        String walletB = bitcoinAddressConstructor(getPublicKeyFromWalletInfo(walletBinfo));
        log.info("WalletB ==> {}", walletB);

        // sending 10 satoshis from A to B
        // 1) B sends its address to A
        t1.setFrom(walletA);
        t1.setTo(walletB);
        t1.setAmount(new BigDecimal("10.0"));
        t1.setUTXO(new BigDecimal(walletAinfo.split("- ")[2]));
        getPrivateKeyFromWalletInfo(walletAinfo);
        updateDB("A", walletAinfo, "10", false);
        t1.calculateHash();
        String payload = constructJSON(t1, "Transaction");


        // Constructing transaction data [input count (short to binary) i.e. 4 bytes,
    }

    public boolean verifyAddress(String address, String publicKey) {
        // remove prefix
        address = address.substring(1);
        // decode
        byte[] decodedData = Utility.decodeBase58(address);
        String publicKeyHash = Arrays.toString(Arrays.copyOfRange(decodedData, 0, -4));
        String checksum = Arrays.toString(Arrays.copyOfRange(decodedData, -4, -1));
        log.info("PublicKeyHash ==> {}, checksum ==> {}", publicKeyHash, checksum);

        return Utility.hash160(publicKey).equals(publicKeyHash) && getChecksum(publicKey).equals(checksum);
    }
}
