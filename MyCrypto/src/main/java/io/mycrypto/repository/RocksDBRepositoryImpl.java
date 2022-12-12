package io.mycrypto.repository;

import io.mycrypto.util.Utility;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SystemUtils;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Repository
public class RocksDBRepositoryImpl implements KeyValueRepository<String, String> {
    private final static String DB_NAME_BLOCKCHAIN = "Blockchain"; // Block-Hash ==> block path
    private final static String DB_NAME_TRANSACTIONS = "Transactions"; // Transaction-Hash ==> Transaction Data
    private final static String DB_NAME_TRANSACTIONS_POOL = "Transactions-Pool"; //   "            "
    private final static String DB_NAME_NODES = "Nodes"; // Wallet Address ==> IP Address
    private final static String DB_NAME_WALLETS = "Wallets"; // Wallet-Name ==> "PubKey PrvKey ..."
    private final static String DB_NAME_ACCOUNT = "Accounts"; // Wallet Address ==> "TransactionId1,VOUT TransactionId2,VOUT ..."
    private final static String LOCATION_TO_STORE_DB; // set the location as you like

    private static final String OUTER_RESOURCE_FOLDER = "RESOURCES";
    private static final String FOLDER_TO_STORE_DB = "RocksDB";

    static {
        LOCATION_TO_STORE_DB = SystemUtils.USER_DIR + Utility.osAppender() + OUTER_RESOURCE_FOLDER + Utility.osAppender() + FOLDER_TO_STORE_DB + Utility.osAppender();
    }

    RocksDB dbBlockchain, dbTransactions, dbTransactionsPool, dbNodes, dbWallets, dbAccount;
    // DB will be stored under: /LOCATION_TO_BE_STORED/DB_NAME

    @PostConstruct
    void initialize() {
        RocksDB.loadLibrary();
        final Options options = new Options();
        options.setCreateIfMissing(true);

        createDB(options, DB_NAME_BLOCKCHAIN, "Blockchain");

        createDB(options, DB_NAME_TRANSACTIONS, "Transactions"); // transaction sent by you as well as that reachieved by you

        createDB(options, DB_NAME_TRANSACTIONS_POOL, "Transactions Pool"); // all transactions

        createDB(options, DB_NAME_NODES, "Node Information");

        createDB(options, DB_NAME_WALLETS, "Wallet Information");

        createDB(options, DB_NAME_ACCOUNT, "Coins/UTXOs for Wallet");
    }

    private void createDB(Options options, String dbName, String msg) {
        File base = new File(LOCATION_TO_STORE_DB);
        if (base.isDirectory())
            log.info("The directory \"RocksDB\" found...");
        else {
            if (base.mkdir())
                log.info("directory \"RocksDB\" created...");
            else
                log.error("Unable to create dir \"RocksDB\"");
        }

        File dbDir = new File(LOCATION_TO_STORE_DB, dbName);
        try {
            Files.createDirectories(dbDir.getParentFile().toPath());
            Files.createDirectories(dbDir.getAbsoluteFile().toPath());
            RocksDB db = RocksDB.open(options, dbDir.getAbsolutePath());
            switch (dbName) {
                case DB_NAME_BLOCKCHAIN -> dbBlockchain = db;
                case DB_NAME_TRANSACTIONS -> dbTransactions = db;
                case DB_NAME_TRANSACTIONS_POOL -> dbTransactionsPool = db;
                case DB_NAME_NODES -> dbNodes = db;
                case DB_NAME_WALLETS -> dbWallets = db;
                case DB_NAME_ACCOUNT -> dbAccount = db;
            }
        } catch (IOException | RocksDBException ex) {
            log.error("Error initializing RocksDB for storing {}, check configurations and permissions, exception: {}, message: {}, stackTrace: {}",
                    msg, ex.getCause(), ex.getMessage(), ex.getStackTrace());
        }
        log.info("RocksDB for storing {} initialized and ready to use", msg);
    }


    @Override
    public void save(String key, String value, String db) {
        log.info("----SAVE----");
        try {
            switch (db) {
                case DB_NAME_BLOCKCHAIN -> dbBlockchain.put(key.getBytes(), value.getBytes());
                case DB_NAME_TRANSACTIONS -> dbTransactions.put(key.getBytes(), value.getBytes());
                case DB_NAME_TRANSACTIONS_POOL -> dbTransactionsPool.put(key.getBytes(), value.getBytes());
                case DB_NAME_NODES -> dbNodes.put(key.getBytes(), value.getBytes());
                case DB_NAME_WALLETS -> dbWallets.put(key.getBytes(), value.getBytes());
                case DB_NAME_ACCOUNT -> dbAccount.put(key.getBytes(), value.getBytes());
                default -> log.error("Please enter valid DB name");
            }
        } catch (RocksDBException e) {
            log.error("Error saving entry in RocksDB, cause: {}, message: {}", e.getCause(), e.getMessage());
        }
    }

    @Override
    public String find(String key, String db) {
        log.info("----FIND----");
        String result = null;
        try {
            byte[] bytes = null;
            switch (db) {
                case DB_NAME_BLOCKCHAIN -> bytes = dbBlockchain.get(key.getBytes());
                case DB_NAME_TRANSACTIONS -> bytes = dbTransactions.get(key.getBytes());
                case DB_NAME_TRANSACTIONS_POOL -> bytes = dbTransactionsPool.get(key.getBytes());
                case DB_NAME_NODES -> bytes = dbNodes.get(key.getBytes());
                case DB_NAME_WALLETS -> bytes = dbWallets.get(key.getBytes());
                case DB_NAME_ACCOUNT -> bytes = dbAccount.get(key.getBytes());
                default -> log.error("Please enter valid DB name");
            }
            if (bytes == null) return null;
            result = new String(bytes);
        } catch (RocksDBException e) {
            log.error("Error retrieving the entry in RocksDB from key: {}, cause: {}, message: {}", key, e.getCause(), e.getMessage());
        }
        return result;
    }

    @Override
    public boolean delete(String key, String db) {
        log.info("----DELETE----");
        try {
            switch (db) {
                case DB_NAME_BLOCKCHAIN -> dbBlockchain.delete(key.getBytes());
                case DB_NAME_TRANSACTIONS -> dbTransactions.delete(key.getBytes());
                case DB_NAME_TRANSACTIONS_POOL -> dbTransactionsPool.delete(key.getBytes());
                case DB_NAME_NODES -> dbNodes.delete(key.getBytes());
                case DB_NAME_WALLETS -> dbWallets.delete(key.getBytes());
                case DB_NAME_ACCOUNT -> dbAccount.delete(key.getBytes());
                default -> {
                    log.error("Please enter valid DB name");
                    return false;
                }
            }
        } catch (RocksDBException e) {
            log.error("Error deleting entry in RocksDB, cause: {}, message: {}", e.getCause(), e.getMessage());
        }
        return true;
    }

    @Override
    public Map<String, String> getList(String db) {
        Map<String, String> result = new HashMap<>();

        RocksIterator itr = null;
        switch (db) {
            case DB_NAME_BLOCKCHAIN -> itr = dbBlockchain.newIterator();
            case DB_NAME_TRANSACTIONS -> itr = dbTransactions.newIterator();
            case DB_NAME_TRANSACTIONS_POOL -> itr = dbTransactionsPool.newIterator();
            case DB_NAME_NODES -> itr = dbNodes.newIterator();
            case DB_NAME_WALLETS -> itr = dbWallets.newIterator();
            case DB_NAME_ACCOUNT -> itr = dbAccount.newIterator();
            default -> log.error("Please enter valid DB name");
        }

        assert itr != null;
        itr.seekToFirst();
        while (itr.isValid()) {
            result.put(new String(itr.key()), new String(itr.value()));
            log.info("Key: {}, Value: {}", new String(itr.key()), new String(itr.value()));
            itr.next();
        }
        return result;
    }
}
