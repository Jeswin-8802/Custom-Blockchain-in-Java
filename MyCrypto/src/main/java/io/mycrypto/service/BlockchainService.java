package io.mycrypto.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.mycrypto.dto.WalletInfoDto;
import io.mycrypto.dto.WalletUTXODto;
import io.mycrypto.entity.Block;
import io.mycrypto.entity.Output;
import io.mycrypto.entity.Transaction;
import io.mycrypto.repository.KeyValueRepository;
import io.mycrypto.util.Utility;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SystemUtils;
import org.bitcoinj.core.Base58;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

@Slf4j
@Service
public class BlockchainService {

    private static final String BLOCK_REWARD = "13.0";
    private static final String MAGIC_BYTES = "f9beb4d9"; // help you spot when a new message starts when sending or receiving a continuous stream of data
    @Autowired
    KeyValueRepository<String, String> rocksDB;

    private static final String OUTER_RESOURCE_FOLDER = "RESOURCES";
    private static final String FOLDER_TO_STORE_BLOCKS = "blockchain";
    private static final String BLOCKCHAIN_STORAGE_PATH;
    static {
        BLOCKCHAIN_STORAGE_PATH = SystemUtils.USER_DIR + Utility.osAppender() + OUTER_RESOURCE_FOLDER + Utility.osAppender() + FOLDER_TO_STORE_BLOCKS + Utility.osAppender();
    }

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

        rocksDB.save(blk.getHash(), BLOCKCHAIN_STORAGE_PATH + blockFileName + ".dat", "Blockchain");

        try {
            DataOutputStream out = new DataOutputStream(new FileOutputStream(BLOCKCHAIN_STORAGE_PATH + blockFileName + ".dat"));
            out.writeUTF(MAGIC_BYTES + Base64.getEncoder().withoutPadding().encodeToString(json.getBytes()));
            out.close();
        } catch (FileNotFoundException e) {
            log.error("Error occurred while creating {} at location {} ", blockFileName, BLOCKCHAIN_STORAGE_PATH);
            e.printStackTrace();
        } catch (IOException e) {
            log.error("Error occurred while creating DataOutputStream");
            e.printStackTrace();
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
            log.error("Error while parsing contents of {} in DB to JSON", id);
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


        // get toAddress of Outputs and the subsequent VOUT
        Map<String, Long> map = new HashMap<>();

        for (Output out: tx.getOutputs())
            map.put(out.getScriptPubKey().getAddress(), out.getN());

        // save transaction information to AccountsDB if transaction has your wallet address

        // fetch list of wallet info to obtain all wallet addresses
        Map<String, String> info = rocksDB.getList("Wallets");
        if (info == null) {
            log.error("No content found in Wallets DB, Looks like you haven't created a wallet yet...");
            return false;
        }
        for (Map.Entry<String, String> i : info.entrySet()) {
            WalletInfoDto temp = null;
            try {
                temp = new ObjectMapper().readValue(i.getValue(), WalletInfoDto.class);
            } catch (JsonProcessingException e) {
                log.error("Error occurred while trying to parse data from Wallets DB to that of type <WalletInfoDto>...");
                e.printStackTrace();
                return false;
            }
            if (temp.getAddress().equals(tx.getTo())) {
                addTransactionToAccounts(temp.getAddress(), tx.getTransactionId(), map.get(temp.getAddress()));
                return true;
            }
        }

        return true;
    }

    void addTransactionToAccounts(String address, String txId, long vout) {
        String existingTransactions = rocksDB.find(address, "Accounts");
        rocksDB.save(address, (existingTransactions.equals("EMPTY") ? "" : existingTransactions + " ") + txId + "," + vout, "Accounts");
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
        JSONObject response = (JSONObject) new JSONParser().parse(new String(Base64.getDecoder().decode(result.substring(MAGIC_BYTES.length()))));
        response.remove("transactions");
        return response;
    }

    JSONObject fetchBlockContentByHeight(int height) throws FileNotFoundException, ParseException {
        DataInputStream in = new DataInputStream(new FileInputStream(BLOCKCHAIN_STORAGE_PATH + "blk" + String.format("%010d", height + 1) + ".dat"));
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

    boolean verifyAddress(String address, String hash160) {
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

    List<WalletUTXODto> retrieveUTXOFromWallet(String[] transactions) throws JsonProcessingException {
        List<WalletUTXODto> result = new ArrayList<>();
        for (String s : transactions) {
            String[] temp = s.split(",");
            String transaction = rocksDB.find(temp[0], "Transactions");
            if (transaction == null) {
                log.error("Could not find transaction {} obtained from Account DB in Transactions DB", temp[0]);
                return null;
            }
            BigDecimal amount =  new ObjectMapper().readValue(transaction, Transaction.class).getOutputs().get(Integer.parseInt(temp[1])).getAmount();
            result.add(WalletUTXODto.builder()
                            .transactionId(temp[0])
                            .vout(Long.parseLong(temp[1]))
                            .amount(amount)
                    .build()
            );
        }
        return result;
    }
}
