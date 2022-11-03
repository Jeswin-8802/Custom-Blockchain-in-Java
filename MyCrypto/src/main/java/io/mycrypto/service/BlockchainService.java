package io.mycrypto.service;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.mycrypto.dto.CreateWalletRequestDto;
import io.mycrypto.dto.VerifyAddressRequestDto;
import io.mycrypto.dto.WalletInfoDto;
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
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.bitcoinj.core.Base58;

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
    @Autowired
    KeyValueRepository<String, String> rocksDB;

    private String saveBlock(Block blk, String blockFileName) {
        String json = constructJSON(blk, blockFileName + ".dat");

        rocksDB.save(blk.getHash(), BLOCKCHAIN_STORAGE_PATH + "\\" + blockFileName + ".dat", "Blockchain");

        try {
            DataOutputStream out = new DataOutputStream(new FileOutputStream(BLOCKCHAIN_STORAGE_PATH + "\\" + blockFileName + ".dat"));
            out.writeUTF(MAGIC_BYTES + Base64.getEncoder().withoutPadding().encodeToString(Objects.requireNonNull(json).getBytes()));
            out.close();
        } catch (FileNotFoundException ex) {
            log.error("Error occurred while creating {} at location {} \nexception: {}, message: {}, stackTrace: {}", blockFileName, BLOCKCHAIN_STORAGE_PATH, ex.getCause(), ex.getMessage(), ex.getStackTrace());
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

    public ResponseEntity<Object> constructResponseForFetchBlockContent(String hash) {
        String jsonResponse = """
                {
                    "error-msg": "%s"
                }
                """;
        try {
            return ResponseEntity.ok(fetchBlockContent(hash));
        } catch (NullPointerException e) {
            log.error("Wrong hash provided. The fetch from DB method returns NULL");
            e.printStackTrace();
            return ResponseEntity.noContent().build();
        } catch(IOException e) {
            log.error("Error occurred while referring to new file PATH..");
            e.printStackTrace();
            try {
                return ResponseEntity.internalServerError().body(new JSONParser().parse(String.format(jsonResponse, "File path referred to in DB is wrong or the file does not exist in that location")));
            } catch (ParseException ignored) {
                // ignored
            }
        } catch (ParseException e) {
            log.error("Error occurred while parsing contents of block file to JSON");
            e.printStackTrace();
            try {
                return ResponseEntity.internalServerError().body(new JSONParser().parse(String.format(jsonResponse, "Error while parsing Block data")));
            } catch (ParseException ignored) {
                // ignored
            }
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
        String value = Utility.generateKeyPairToFile();
        log.info("\n {}", request.getWalletName());
        rocksDB.save(request.getWalletName(), value, "Wallets");
        WalletInfoDto info = null;
        try {
            info = new ObjectMapper().readValue(value, WalletInfoDto.class);
            log.info("Wallet Contents: {}", new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(info));
        } catch (JsonProcessingException e) {
            log.error("Error while parsing json to WalletInfoDto..");
            e.printStackTrace();
        }

        WalletResponseDto response = new WalletResponseDto();

        assert info != null;
        response.setPublicKey(info.getPublicKey());
        response.setPrivateKey(info.getPublicKey());
        response.setHash160(info.getHash160());
        response.setBalance(new BigDecimal("0.0"));
        response.setAddress(info.getAddress());
        return ResponseEntity.ok(response);
    }

    public ResponseEntity<WalletResponseDto> fetchWalletInfo(String walletName) {
        String result = rocksDB.find(walletName, "Wallets");
        log.info("Name of wallet: {}", walletName);
        if (result == null) return ResponseEntity.noContent().build();
        WalletInfoDto info = null;
        try {
            info = new ObjectMapper().readValue(result, WalletInfoDto.class);
            log.info("Wallet Contents: {}", new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(info));
        } catch (JsonProcessingException e) {
            log.error("Error while parsing json to WalletInfoDto..");
            e.printStackTrace();
        }

        WalletResponseDto response = new WalletResponseDto();
        assert info != null;
        response.setPublicKey(info.getPublicKey());
        response.setPrivateKey(info.getPublicKey());
        response.setHash160(info.getHash160());
        response.setBalance(new BigDecimal("0.0"));
        response.setAddress(info.getAddress());
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
        WalletInfoDto info = null;
        try {
            info = new ObjectMapper().readValue(rocksDB.find("default", "Wallets"), WalletInfoDto.class);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        assert info != null;
        genesis.mineBlock(info.getAddress());

        try {
            return ResponseEntity.ok(new JSONParser().parse(saveBlock(genesis, GENESIS_BLOCK)));
        } catch (ParseException e) {
            log.error("Error while constructing response for createGenesisBlock()..");
            e.printStackTrace();
        }
        return null;
    }

    public ResponseEntity<Object> constructResponseForValidateAddress(VerifyAddressRequestDto request) {
        String jsonResponse = """
                {
                    "msg": "%s"
                }
                """;
        try {
            if (verifyAddress(request.getAddress(), request.getHash160()))
                return ResponseEntity.ok(new JSONParser().parse(String.format(jsonResponse, "valid")));
            else
                return ResponseEntity.ok(new JSONParser().parse(String.format(jsonResponse, "invalid")));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return ResponseEntity.internalServerError().build();
    }

    public JSONObject fetchBlockContent(String hash) throws NullPointerException, IOException, ParseException {
        String path = rocksDB.find(hash, "Blockchain");
        log.debug("File PATH for {} ==> {}", hash, path);
        DataInputStream in = new DataInputStream(new FileInputStream(path));
        boolean eof = false;
        StringBuilder result = new StringBuilder();
        while (!eof) {
            try {
                result.append(in.readUTF());
            } catch (EOFException ex) {
                log.warn("End of file reached...");
                eof = true;
            }
        }
        log.info("File content in HEX ==> {}", result);
        return (JSONObject) new JSONParser().parse(new String(Base64.getDecoder().decode(result.substring(MAGIC_BYTES.length()))));
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

    public void makeTransaction() {
        Transaction t1 = new Transaction(), t2 = new Transaction();
        JSONObject walletAinfo = null, walletBinfo = null;
        try{
            walletAinfo = (JSONObject) new JSONParser().parse(rocksDB.find("A", "Wallets"));
            walletBinfo = (JSONObject) new JSONParser().parse(rocksDB.find("B", "Wallets"));
        } catch (ParseException e) {
            e.printStackTrace();
        }

        assert walletAinfo != null;
        String AddressOfA = (String) walletAinfo.get("chicken-coin-address");
        log.info("AddressOfA ==> {}", AddressOfA);
        String AddressOfB = (String) walletBinfo.get("chicken-coin-address");
        log.info("AddressOfB ==> {}", AddressOfB);

        // sending 10 satoshis from A to B
        // 1) B sends its address to A
        


        // Constructing transaction data [input count (short to binary) i.e. 4 bytes,
    }

    public boolean verifyAddress(String address, String hash160) {
        String inputs = """
                {
                    "address": "%s",
                    "hash160": "%s"
                }
                """;
        log.info("Inputs:\n{}", Utility.beautify(String.format(inputs, address, hash160)));

        // decode
        byte[] decodedData = Base58.decode(address);
        log.info("Base58 decoded (address) : {}", Utility.bytesToHex(decodedData));

        byte[] hash160FromAddress = new byte[decodedData.length - 5];
        byte[] checksumFromAddress = new byte[4];
        System.arraycopy(decodedData, 1, hash160FromAddress, 0, decodedData.length - 5);
        log.info("Hash160(public-key) from address: {}", Utility.bytesToHex(hash160FromAddress));
        System.arraycopy(decodedData, decodedData.length - 4, checksumFromAddress, 0, 4);
        log.info("checksum from chicken-coin-address: {}", Utility.bytesToHex(checksumFromAddress));

        byte[] checksum = new byte[4];
        System.arraycopy(Objects.requireNonNull(Utility.getHashSHA256(Utility.getHashSHA256(Utility.hexToBytes(hash160)))), 0, checksum, 0, 4);
        log.info("checksum from public-key: {}", Utility.bytesToHex(checksum));

        return Arrays.equals(checksumFromAddress, checksum) && hash160.equals(Utility.bytesToHex(hash160FromAddress));
    }
}
