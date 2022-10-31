package io.mycrypto.repository;

import lombok.extern.slf4j.Slf4j;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@Slf4j
@Repository
public class RocksDBRepositoryImpl implements KeyValueRepository<String, String> {
    private final static String DB_NAME_BLOCKCHAIN = "Blockchain";
    private final static String DB_NAME_TRANSACTIONS = "Transactions";
    private final static String DB_NAME_TRANSACTIONS_POOL = "Transactions-Pool";

    private static final String DB_NAME_TRANSACTIONS_POOL_INDEX = "Transactions-Pool-Index";
    private final static String DB_NAME_NODES = "Nodes"; // Public IP Addresses ==> Hash(PublicKey)
    private final static String DB_NAME_WALLETS = "Wallets"; // Wallet-Name ==> "PublicKey PrivateKey UTXO"
    private final static String LOCATION_TO_BE_STORED = "C:\\REPO\\Github\\rock-db"; // give the right location in your system and set it as you like

    RocksDB dbBlockchain, dbTransactions, dbTransactionsPool, dbTransactionsPoolIndex, dbNodes, dbWallets;
    // DB will be stored under: /LOCATION_TO_BE_STORED/DB_NAME

    @PostConstruct
    void initialize() {
        RocksDB.loadLibrary();
        final Options options = new Options();
        options.setCreateIfMissing(true);

        createDB(options, DB_NAME_BLOCKCHAIN, "Blockchain");

        createDB(options, DB_NAME_TRANSACTIONS, "Transactions"); // transaction sent by you as well as that reachieved by you

        createDB(options, DB_NAME_TRANSACTIONS_POOL, "Transactions Pool"); // all transactions

        createDB(options, DB_NAME_TRANSACTIONS_POOL_INDEX, "Transactions Pool Index");

        createDB(options, DB_NAME_NODES, "Node Information");

        createDB(options, DB_NAME_WALLETS, "Wallet Information");
    }

    private void createDB(Options options, String dbName, String msg) {
        File dbDir = new File(LOCATION_TO_BE_STORED, dbName);
        try {
            Files.createDirectories(dbDir.getParentFile().toPath());
            Files.createDirectories(dbDir.getAbsoluteFile().toPath());
            RocksDB db = RocksDB.open(options, dbDir.getAbsolutePath());
            switch (dbName) {
                case DB_NAME_BLOCKCHAIN -> dbBlockchain = db;
                case DB_NAME_TRANSACTIONS -> dbTransactions = db;
                case DB_NAME_TRANSACTIONS_POOL -> dbTransactionsPool = db;
                case DB_NAME_TRANSACTIONS_POOL_INDEX -> dbTransactionsPoolIndex = db;
                case DB_NAME_NODES -> dbNodes = db;
                case DB_NAME_WALLETS -> dbWallets = db;
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
                case DB_NAME_TRANSACTIONS_POOL_INDEX -> dbTransactionsPoolIndex.put(key.getBytes(), value.getBytes());
                case DB_NAME_NODES -> dbNodes.put(key.getBytes(), value.getBytes());
                case DB_NAME_WALLETS -> dbWallets.put(key.getBytes(), value.getBytes());
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
                case DB_NAME_TRANSACTIONS_POOL_INDEX -> bytes = dbTransactionsPoolIndex.get(key.getBytes());
                case DB_NAME_NODES -> bytes = dbNodes.get(key.getBytes());
                case DB_NAME_WALLETS -> bytes = dbWallets.get(key.getBytes());
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
                case DB_NAME_TRANSACTIONS_POOL_INDEX -> dbTransactionsPoolIndex.delete(key.getBytes());
                case DB_NAME_NODES -> dbNodes.delete(key.getBytes());
                case DB_NAME_WALLETS -> dbWallets.delete(key.getBytes());
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
}
