package io.mycrypto.service.block;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.mycrypto.dto.WalletInfoDto;
import io.mycrypto.entity.Block;
import io.mycrypto.entity.Output;
import io.mycrypto.entity.ScriptPublicKey;
import io.mycrypto.entity.Transaction;
import io.mycrypto.exception.MyCustomException;
import io.mycrypto.repository.KeyValueRepository;
import io.mycrypto.service.transaction.TransactionService;
import io.mycrypto.util.Utility;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.util.Strings;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
public class BlockService {
    @Autowired
    KeyValueRepository<String, String> rocksDB;

    @Autowired
    TransactionService transactionService;

    private static final String MAGIC_BYTES = "f9beb4d9"; // help you spot when a new message starts when sending or receiving a continuous stream of data
    private static final String OUTER_RESOURCE_FOLDER = "RESOURCES";
    private static final String FOLDER_TO_STORE_BLOCKS = "blockchain";
    private static final String BLOCKCHAIN_STORAGE_PATH;
    static {
        BLOCKCHAIN_STORAGE_PATH = SystemUtils.USER_DIR + Utility.osAppender() + OUTER_RESOURCE_FOLDER + Utility.osAppender() + FOLDER_TO_STORE_BLOCKS + Utility.osAppender();
    }

    /**
     *
     * @param blk Block Info in the format <Block.class>
     * @param blockFileName The path on your system here the block is stored as a .dat file in hex
     * @return JSON String of the block info
     */
    public String saveBlock(Block blk, String blockFileName) {
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

    /**
     *
     * @param hash
     * @return JSONObject
     * @throws NullPointerException
     * @throws IOException
     * @throws ParseException
     */
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
        JSONObject response = (JSONObject) new JSONParser().parse(new String(Base64.getDecoder().decode(result.substring(MAGIC_BYTES.length()))));
        response.remove("transactions");
        return response;
    }

    /**
     *
     * @param height
     * @return JSONObject
     * @throws FileNotFoundException
     * @throws ParseException
     */
    public JSONObject fetchBlockContentByHeight(int height) throws FileNotFoundException, ParseException {
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

    public Block mineGenesisBlock(String walletName) throws MyCustomException {
        Block genesis = new Block();
        genesis.setPreviousHash("0");
        genesis.setHeight(0);

        // fetching wallet info to get dodo-coin address
        WalletInfoDto info = null;
        try {
            info = new ObjectMapper().readValue(rocksDB.find(Strings.isEmpty(walletName) ? "default" : walletName, "Wallets"), WalletInfoDto.class);;
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.error("Wallet >> default << NOT FOUND...");
            e.printStackTrace();
            throw new MyCustomException("Could not find wallet to send block reward to. Please create a wallet called default");
        }

        // creating coinbase transaction
        Transaction coinbase = transactionService.constructCoinbaseTransaction(info);

        genesis.setTransactions(List.of(coinbase));
        List<String> transactionIds = new ArrayList<>();
        transactionIds.add(coinbase.getTransactionId());
        genesis.setTransactionIds(transactionIds);
        genesis.setMerkleRoot(Utility.constructMerkleTree(new ArrayList<>(transactionIds)));
        genesis.setNumTx(transactionIds.size());
        log.info("Hash of genesis block ==> {}", genesis.calculateHash());
        log.info("mining the genesis block...");

        genesis.mineBlock(info.getAddress());

        return genesis;
    }
}
