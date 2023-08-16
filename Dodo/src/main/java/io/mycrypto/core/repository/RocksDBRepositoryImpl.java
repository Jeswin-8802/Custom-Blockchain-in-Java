package io.mycrypto.core.repository;

import io.mycrypto.core.util.Utility;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SystemUtils;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static io.mycrypto.core.repository.DbName.*;

// IMP links
// http://javadox.com/org.rocksdb/rocksdbjni/5.15.10/org/rocksdb/RocksDB.html
// https://javadoc.io/static/org.rocksdb/rocksdbjni/7.8.3/org/rocksdb/RocksDB.html

@Slf4j
@Repository
public class RocksDBRepositoryImpl implements KeyValueRepository<String, String> {
    private final static String LOCATION_TO_STORE_DB;
    private final static String PROJECT_FOLDER_PATH;
    private static final String OUTER_RESOURCE_FOLDER = "RESOURCES";
    private static final String FOLDER_TO_STORE_DB = "RocksDB";
    private static final String PROJECT_FOLDER = "Dodo";

    static {
        // 4 backslashes. Java compiler turns it into \\, which regex turns into a single \
        String[] path = SystemUtils.USER_DIR.split(SystemUtils.IS_OS_WINDOWS ? "\\\\" : "/");
        if (path[path.length - 1].equals(PROJECT_FOLDER))
            path = Arrays.copyOfRange(path, 0, path.length - 1);

        PROJECT_FOLDER_PATH = String.join(Utility.osAppender(), path);

        LOCATION_TO_STORE_DB = PROJECT_FOLDER_PATH + Utility.osAppender() + OUTER_RESOURCE_FOLDER + Utility.osAppender() + FOLDER_TO_STORE_DB + Utility.osAppender();
    }
    // --------------------------------------------------------------

    RocksDB dbBlockchain, dbTransactions, dbTransactionsPool, dbNodes, dbWallets, dbAccounts, dbWebrtc;
    // DB will be stored under: /LOCATION_TO_STORE_DB/DB_NAME

    @PostConstruct
    void initialize() {

        RocksDB.loadLibrary();
        final Options options = new Options();
        options.setCreateIfMissing(true);

        File resources = new File(PROJECT_FOLDER_PATH + Utility.osAppender() + OUTER_RESOURCE_FOLDER);
        if (resources.isDirectory())
            log.info(String.format("The directory \\%s\\ found...", OUTER_RESOURCE_FOLDER));
        else {
            if (resources.mkdir())
                log.info(String.format("The directory \\%s\\ is created...", OUTER_RESOURCE_FOLDER));
            else
                log.info(String.format("Unable to create directory \\%s\\ ...", OUTER_RESOURCE_FOLDER));
        }

        createDB(options, BLOCKCHAIN, "Blockchain");
        createDB(options, TRANSACTIONS, "Transactions"); // transaction sent by you as well as that reachieved by you
        createDB(options, TRANSACTIONS_POOL, "Transactions Pool"); // all transactions
        createDB(options, NODES, "Node Information");
        createDB(options, WALLETS, "Wallet Information");
        createDB(options, ACCOUNTS, "Coins/UTXOs for Wallet");
        createDB(options, WEBRTC, "Store responses from remote servers and peers");
    }

    private void createDB(Options options, DbName dbName, String msg) {
        File base = new File(LOCATION_TO_STORE_DB);
        if (base.isDirectory())
            log.info("The directory \\RocksDB\\ found...");
        else {
            if (base.mkdir())
                log.info("directory \\RocksDB\\ created...");
            else
                log.error("Unable to create dir \\RocksDB\\ ...");
        }

        File dbDir = new File(LOCATION_TO_STORE_DB, dbName.toString());
        try {
            Files.createDirectories(dbDir.getParentFile().toPath());
            Files.createDirectories(dbDir.getAbsoluteFile().toPath());
            RocksDB db = RocksDB.open(options, dbDir.getAbsolutePath());
            switch (dbName) {
                case BLOCKCHAIN -> dbBlockchain = db;
                case TRANSACTIONS -> dbTransactions = db;
                case TRANSACTIONS_POOL -> dbTransactionsPool = db;
                case NODES -> dbNodes = db;
                case WALLETS -> dbWallets = db;
                case ACCOUNTS -> dbAccounts = db;
                case WEBRTC -> dbWebrtc = db;
            }
        } catch (IOException | RocksDBException ex) {
            log.error("Error initializing RocksDB for storing {}, check configurations and permissions, exception: {}, message: {}, stackTrace: {}",
                    msg, ex.getCause(), ex.getMessage(), ex.getStackTrace());
        }
        log.info("RocksDB for storing {} initialized and ready to use", msg);
    }


    @Override
    public void save(String key, String value, DbName db) {
        log.info("----SAVE----      KEY: {}     VALUE: {}     DB: {}", key, value.length() > 25 ? value.substring(0, 25) + " ......." : value, db);
        try {
            switch (db) {
                case BLOCKCHAIN -> dbBlockchain.put(key.getBytes(), value.getBytes());
                case TRANSACTIONS -> dbTransactions.put(key.getBytes(), value.getBytes());
                case TRANSACTIONS_POOL -> dbTransactionsPool.put(key.getBytes(), value.getBytes());
                case NODES -> dbNodes.put(key.getBytes(), value.getBytes());
                case WALLETS -> dbWallets.put(key.getBytes(), value.getBytes());
                case ACCOUNTS -> dbAccounts.put(key.getBytes(), value.getBytes());
                case WEBRTC -> dbWebrtc.put(key.getBytes(), value.getBytes());
                default -> log.error("Please enter valid DB name");
            }
        } catch (RocksDBException e) {
            log.error("Error saving entry in RocksDB, cause: {}, message: {}", e.getCause(), e.getMessage());
        }
    }

    @Override
    public String find(String key, DbName db) {
        log.info("----FIND----      KEY: {}     DB: {}", key, db);
        String result = null;
        try {
            byte[] bytes = null;
            switch (db) {
                case BLOCKCHAIN -> bytes = dbBlockchain.get(key.getBytes());
                case TRANSACTIONS -> bytes = dbTransactions.get(key.getBytes());
                case TRANSACTIONS_POOL -> bytes = dbTransactionsPool.get(key.getBytes());
                case NODES -> bytes = dbNodes.get(key.getBytes());
                case WALLETS -> bytes = dbWallets.get(key.getBytes());
                case ACCOUNTS -> bytes = dbAccounts.get(key.getBytes());
                case WEBRTC -> bytes = dbWebrtc.get(key.getBytes());
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
    public boolean delete(String key, DbName db) {
        log.info("----DELETE----      KEY: {}     DB: {}", key, db);
        try {
            switch (db) {
                case BLOCKCHAIN -> dbBlockchain.delete(key.getBytes());
                case TRANSACTIONS -> dbTransactions.delete(key.getBytes());
                case TRANSACTIONS_POOL -> dbTransactionsPool.delete(key.getBytes());
                case NODES -> dbNodes.delete(key.getBytes());
                case WALLETS -> dbWallets.delete(key.getBytes());
                case ACCOUNTS -> dbAccounts.delete(key.getBytes());
                case WEBRTC -> dbWebrtc.delete(key.getBytes());
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
    public Map<String, String> getList(DbName db) {
        log.info("----GET LIST----      DB: {}", db);
        Map<String, String> result = new HashMap<>();

        RocksIterator itr = null;
        switch (db) {
            case BLOCKCHAIN -> itr = dbBlockchain.newIterator();
            case TRANSACTIONS -> itr = dbTransactions.newIterator();
            case TRANSACTIONS_POOL -> itr = dbTransactionsPool.newIterator();
            case NODES -> itr = dbNodes.newIterator();
            case WALLETS -> itr = dbWallets.newIterator();
            case ACCOUNTS -> itr = dbAccounts.newIterator();
            case WEBRTC -> itr = dbWebrtc.newIterator();
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

    @Override
    public long getCount(DbName db) {
        switch (db) {
            case BLOCKCHAIN -> {
                return dbBlockchain.getLatestSequenceNumber();
            }
            case TRANSACTIONS -> {
                return dbTransactions.getLatestSequenceNumber();
            }
            case TRANSACTIONS_POOL -> {
                return dbTransactionsPool.getLatestSequenceNumber();
            }
            case NODES -> {
                return dbNodes.getLatestSequenceNumber();
            }
            case WALLETS -> {
                return dbWallets.getLatestSequenceNumber();
            }
            case ACCOUNTS -> {
                return dbAccounts.getLatestSequenceNumber();
            }
            case WEBRTC -> {
                return dbWebrtc.getLatestSequenceNumber();
            }
            default -> log.error("Please enter valid DB name");
        }
        return 0L;
    }
}
