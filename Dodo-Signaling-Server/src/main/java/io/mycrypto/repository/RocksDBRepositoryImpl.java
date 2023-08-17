package io.mycrypto.repository;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SystemUtils;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static io.mycrypto.repository.DbName.*;

// IMP links
// http://javadox.com/org.rocksdb/rocksdbjni/5.15.10/org/rocksdb/RocksDB.html
// https://javadoc.io/static/org.rocksdb/rocksdbjni/7.8.3/org/rocksdb/RocksDB.html

@Slf4j
@Repository
public class RocksDBRepositoryImpl implements KeyValueRepository<String, String> {
    private final static String LOCATION_TO_STORE_DB;
    private final static String PROJECT_FOLDER_PATH = SystemUtils.USER_DIR;
    private static final String FOLDER_TO_STORE_DB = "RocksDB";

    static {
        LOCATION_TO_STORE_DB = PROJECT_FOLDER_PATH + osAppender() + FOLDER_TO_STORE_DB;
    }

    // --------------------------------------------------------------

    private RocksDB dbPeerStatus, dbIceCandidates, dbPeerAddress;
    // DB will be stored under: /LOCATION_TO_STORE_DB/DB_NAME

    @PostConstruct
    void initialize() {

        RocksDB.loadLibrary();
        final Options options = new Options();
        options.setCreateIfMissing(true);

        createDB(options, PEER_STATUS, "Online Status of Peers");
        createDB(options, ICE_CANDIDATES, "Tracks Ice Candidates of Peers");
        createDB(options, PEER_ADDRESSES, "Wallet addresses held by each peer");
    }

    private void createDB(Options options, DbName dbName, String msg) {
        File base = new File(LOCATION_TO_STORE_DB);
        if (!base.isDirectory()) {
            if (base.mkdir())
                log.info("Creating directory \\RocksDB\\ ...");
            else
                log.error("Unable to create dir \\RocksDB\\ ...");
        }

        File dbDir = new File(LOCATION_TO_STORE_DB, dbName.toString());
        try {
            Files.createDirectories(dbDir.getParentFile().toPath());
            Files.createDirectories(dbDir.getAbsoluteFile().toPath());
            RocksDB db = RocksDB.open(options, dbDir.getAbsolutePath());
            switch (dbName) {
                case PEER_STATUS -> dbPeerStatus = db;
                case ICE_CANDIDATES -> dbIceCandidates = db;
                case PEER_ADDRESSES -> dbPeerAddress = db;
            }
        } catch (IOException | RocksDBException ex) {
            log.error("Error initializing RocksDB for storing {}, check configurations and permissions, exception: {}, message: {}, stackTrace: {}",
                    msg, ex.getCause(), ex.getMessage(), ex.getStackTrace());
        }
        log.info("RocksDB for storing {} initialized and ready to use", msg);
    }


    @Override
    public synchronized void save(String key, String value, DbName db) {
        log.info("----SAVE----      KEY: {}     VALUE: {}     DB: {}", key, value.length() > 25 ? value.substring(0, 25) + " ......." : value, db);
        try {
            switch (db) {
                case PEER_STATUS -> dbPeerStatus.put(key.getBytes(), value.getBytes());
                case ICE_CANDIDATES -> dbIceCandidates.put(key.getBytes(), value.getBytes());
                case PEER_ADDRESSES -> dbPeerAddress.put(key.getBytes(), value.getBytes());
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
                case PEER_STATUS -> bytes = dbPeerStatus.get(key.getBytes());
                case ICE_CANDIDATES -> bytes = dbIceCandidates.get(key.getBytes());
                case PEER_ADDRESSES -> bytes = dbPeerAddress.get(key.getBytes());
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
    public synchronized boolean delete(String key, DbName db) {
        log.info("----DELETE----      KEY: {}     DB: {}", key, db);
        try {
            switch (db) {
                case PEER_STATUS -> dbPeerStatus.delete(key.getBytes());
                case ICE_CANDIDATES -> dbIceCandidates.delete(key.getBytes());
                case PEER_ADDRESSES -> dbPeerAddress.delete(key.getBytes());
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
            case PEER_STATUS -> itr = dbPeerStatus.newIterator();
            case ICE_CANDIDATES -> itr = dbIceCandidates.newIterator();
            case PEER_ADDRESSES -> itr = dbPeerAddress.newIterator();
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
            case PEER_STATUS -> {
                return dbPeerStatus.getLatestSequenceNumber();
            }
            case ICE_CANDIDATES -> {
                return dbIceCandidates.getLatestSequenceNumber();
            }
            case PEER_ADDRESSES -> {
                return dbPeerAddress.getLatestSequenceNumber();
            }
            default -> log.error("Please enter valid DB name");
        }
        return 0L;
    }

    private static String osAppender() {
        return SystemUtils.IS_OS_LINUX || SystemUtils.IS_OS_MAC ? "/" : "\\";
    }
}
