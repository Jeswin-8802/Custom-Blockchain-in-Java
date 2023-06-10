package io.mycrypto.service.block;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.mycrypto.dto.WalletInfoDto;
import io.mycrypto.entity.Block;
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
import org.springframework.stereotype.Service;

import java.io.*;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
public class BlockService {
    private static final String MAGIC_BYTES = "f9beb4d9"; // help you spot when a new message starts when sending or receiving a continuous stream of data
    private static final String OUTER_RESOURCE_FOLDER = "RESOURCES";
    private static final String FOLDER_TO_STORE_BLOCKS = "blockchain";
    private static final String BLOCKCHAIN_STORAGE_PATH;

    static {
        BLOCKCHAIN_STORAGE_PATH = SystemUtils.USER_DIR + Utility.osAppender() + OUTER_RESOURCE_FOLDER + Utility.osAppender() + FOLDER_TO_STORE_BLOCKS + Utility.osAppender();
    }

    @Autowired
    KeyValueRepository<String, String> rocksDB;
    @Autowired
    TransactionService transactionService;

    public Block mineBlock(String walletName) throws MyCustomException {
        // get transactions from Transactions Pool
        List<Transaction> transactions = transactionService.retrieveAndDeleteTransactionsFromTransactionsPool(); // also checks for if there exists enough transactions within the Transactions Pool to create a Block (throws exception if requirements are not met)

        Block block = new Block();

        // setting previous block hash
        Block previousBlock;
        long previousBlockHeight = 0;
        try {
            previousBlockHeight = rocksDB.getCount("Blockchain") - 1;
            previousBlock = new ObjectMapper().readValue(fetchBlockContentByHeight((int) previousBlockHeight).toJSONString(), Block.class);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new MyCustomException(String.format("Did not find file storing Block Information for Block with height: %s", previousBlockHeight));
        } catch (ParseException e) {
            e.printStackTrace();
            throw new MyCustomException("Error while parsing contents of previous Block from File to JSON");
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            throw new MyCustomException("Error while parsing contents of previous Block from JsonString to <Block.class>");
        }
        block.setPreviousHash(previousBlock.getHash());
        block.setHeight(previousBlockHeight + 1);

        log.info("Sequence Number: {}        Previous Block Height: {}", previousBlockHeight, previousBlock.getHeight());

        // fetching wallet info to get dodo-coin address
        WalletInfoDto info;
        try {
            String walletInfo = rocksDB.find(Strings.isEmpty(walletName) ? "default" : walletName, "Wallets");
            if (Strings.isEmpty(walletInfo)) {
                if (Strings.isEmpty(walletName)) {
                    log.error("Wallet >> default << NOT FOUND...");
                    throw new MyCustomException("Could not find wallet to send block reward to. Please create a wallet called default");
                }
                throw new MyCustomException("Wallet not found");
            }
            info = new ObjectMapper().readValue(walletInfo, WalletInfoDto.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            e.printStackTrace();
            throw new MyCustomException("Encountered a parsing error for WalletInfo...");
        } catch (MyCustomException e) {
            log.error(e.getErrorMessage());
            throw e;
        }

        // creating coinbase transaction
        Transaction coinbase = transactionService.constructCoinbaseTransaction(info, transactions);
        transactions.add(0, coinbase);
        block.setTransactions(transactions);

        List<String> transactionIds = new ArrayList<>();
        for (Transaction tx: transactions)
            transactionIds.add(tx.getTransactionId());

        block.setTransactionIds(transactionIds);
        block.setMerkleRoot(Utility.constructMerkleTree(new ArrayList<>(transactionIds)));
        block.setNumTx(transactionIds.size());
        log.info("Hash of genesis block ==> {}", block.calculateHash());
        log.info("mining the genesis block...");

        block.mineBlock(info.getAddress());

        return block;
    }

    /**
     * Mines genesis blocks
     *
     * @param walletName Name of the Wallet to which the block reward gets credited to;
     *                   The Block owner in short (<i>Will be referred to by the wallet address which can be obtained from the name</i>)
     * @return A Block Object that contains all the block information of the mined block
     */
    public Block mineGenesisBlock(String walletName) throws MyCustomException {
        Block genesis = new Block();
        genesis.setPreviousHash("");
        genesis.setHeight(0);

        // fetching wallet info to get dodo-coin address
        WalletInfoDto info;
        try {
            String walletInfo = rocksDB.find(Strings.isEmpty(walletName) ? "default" : walletName, "Wallets");
            if (Strings.isEmpty(walletInfo))
                throw new MyCustomException("Wallet not found");
            info = new ObjectMapper().readValue(walletInfo, WalletInfoDto.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.error("Wallet >> default << NOT FOUND...");
            e.printStackTrace();
            throw new MyCustomException("Could not find wallet to send block reward to. Please create a wallet called default");
        } catch (MyCustomException e) {
            log.error(e.getErrorMessage());
            throw e;
        }

        // creating coinbase transaction
        Transaction coinbase = transactionService.constructCoinbaseTransactionForGenesisBlock(info);

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

    /**
     * @param blk           Block Info in the format <Block.class>
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
     * Fetches Block Information by its Hash value
     *
     * @param hash The hash of the Block
     * @return JSONObject
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
     * Fetches Block Information by its Height
     *
     * @param height The height of the Block
     * @return JSONObject
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
}
