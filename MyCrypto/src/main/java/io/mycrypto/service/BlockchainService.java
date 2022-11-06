package io.mycrypto.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.mycrypto.dto.CreateWalletRequestDto;
import io.mycrypto.dto.VerifyAddressRequestDto;
import io.mycrypto.dto.WalletInfoDto;
import io.mycrypto.dto.WalletResponseDto;
import io.mycrypto.entity.Block;
import io.mycrypto.entity.Output;
import io.mycrypto.entity.ScriptPublicKey;
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
import java.util.*;

@Slf4j
@Service
public class BlockchainService {

    private static final String BLOCKCHAIN_STORAGE_PATH = "C:\\REPO\\Github\\blockchain";

    private static final String BLOCK_REWARD = "13.0";
    private static final String MAGIC_BYTES = "f9beb4d9"; // help you spot when a new message starts when sending or receiving a continuous stream of data
    @Autowired
    KeyValueRepository<String, String> rocksDB;

    String saveBlock(Block blk, String blockFileName) {
        String json = null;
        try {
            ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
            json = ow.writeValueAsString(blk);
            blk.setSize(new BigInteger(String.valueOf(json.replace(" ", "").length() - "\"size\":null\\\"weight\\\": null\"".length())));
            blk.setWeight(new BigInteger("4").multiply(blk.getSize()));
            json = ow.writeValueAsString(blk);
            log.info("{} ==> \n{}", blockFileName + ".dat", json);
        } catch (JsonProcessingException ex) {
            log.error("Error occurred while parsing Object(Block) to json \nexception: {}, message: {}, stackTrace: {}", ex.getCause(), ex.getMessage(), ex.getStackTrace());
            return json;
        }

        rocksDB.save(blk.getHash(), BLOCKCHAIN_STORAGE_PATH + "\\" + blockFileName + ".dat", "Blockchain");

        try {
            DataOutputStream out = new DataOutputStream(new FileOutputStream(BLOCKCHAIN_STORAGE_PATH + "\\" + blockFileName + ".dat"));
            out.writeUTF(MAGIC_BYTES + Base64.getEncoder().withoutPadding().encodeToString(json.getBytes()));
            out.close();
        } catch (FileNotFoundException ex) {
            log.error("Error occurred while creating {} at location {} \nexception: {}, message: {}, stackTrace: {}", blockFileName, BLOCKCHAIN_STORAGE_PATH, ex.getCause(), ex.getMessage(), ex.getStackTrace());
        } catch (IOException ex) {
            log.error("Error occurred while creating DataOutputStream \nexception: {}, message: {}, stackTrace: {}", ex.getCause(), ex.getMessage(), ex.getStackTrace());
        }

        return json;
    }

    JSONObject fetchTransaction(String id) throws NullPointerException {
        String json = rocksDB.find(id, "Transactions");
        try {
            if (json != null)
                return (JSONObject) new JSONParser().parse(json);
            throw new NullPointerException();
        } catch (ParseException e) {
            log.error("Error while parsing contemts of {} in DB to JSON", id);
            e.printStackTrace();
        }
        return null;
    }

    JSONObject constructJsonResponse(String key, String message) {
        try {
            return (JSONObject) new JSONParser().parse(String.format("""
                    {
                        "%s": "%s"
                    }
                    """, key, message));
        } catch (ParseException ignore) {
            // ignore
        }
        return null;
    }

    boolean saveTransaction(Transaction tx) {
        String json = null;
        try {
            ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
            json = ow.writeValueAsString(tx);
            tx.setSize(new BigInteger(String.valueOf(json.replace(" ", "").length() - "\"size\":null\"weight\": null".length())));
            tx.setWeight(new BigInteger("4").multiply(tx.getSize()).subtract(new BigInteger(String.valueOf(tx.getInputs().size()))));
            json = ow.writeValueAsString(tx);
            log.info("{} ==> \n{}", tx.getTransactionId(), json);
        } catch (JsonProcessingException ex) {
            log.error("Error occurred while parsing Object(Transaction) to json");
            ex.printStackTrace();
            return false;
        }

        rocksDB.save(tx.getTransactionId(), json, "Transactions");

        return true;
    }

    JSONObject fetchBlockContent(String hash) throws NullPointerException, IOException, ParseException {
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
        JSONObject response =  (JSONObject) new JSONParser().parse(new String(Base64.getDecoder().decode(result.substring(MAGIC_BYTES.length()))));
        response.remove("transactions");
        return response;
    }

    JSONObject fetchBlockContentByHeight(int height) throws FileNotFoundException, ParseException {
        DataInputStream in = new DataInputStream(new FileInputStream(BLOCKCHAIN_STORAGE_PATH + "\\blk" + String.format("%010d", height + 1) + ".dat"));
        boolean eof = false;
        StringBuilder result = new StringBuilder();
        while (!eof) {
            try {
                result.append(in.readUTF());
            } catch (IOException ex) {
                log.warn("End of file reached...");
                eof = true;
            }
        }
        log.info("File content in HEX ==> {}", result);
        return (JSONObject) new JSONParser().parse(new String(Base64.getDecoder().decode(result.substring(MAGIC_BYTES.length()))));
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
        log.info("checksum from dodo-coin-address: {}", Utility.bytesToHex(checksumFromAddress));

        byte[] checksum = new byte[4];
        System.arraycopy(Objects.requireNonNull(Utility.getHashSHA256(Utility.getHashSHA256(Utility.hexToBytes(hash160)))), 0, checksum, 0, 4);
        log.info("checksum from public-key: {}", Utility.bytesToHex(checksum));

        return Arrays.equals(checksumFromAddress, checksum) && hash160.equals(Utility.bytesToHex(hash160FromAddress));
    }
}
