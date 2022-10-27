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


    File dbDir;
    RocksDB db;

    private final static String DB_NAME_BLOCKCHAIN = "Blockchain";

    private final static String DB_NAME_TRAMSACTIONS = "Transactions";

    private final static String DB_NAME_NODES = "Nodes";

    private final static String LOCATION_TO_BE_STORED = "/tmp/rocks-db"; // give the right location in your system and set it as you like
    // DB will be stored under: /LOCATION_TO_BE_STORED/DB_NAME

    @PostConstruct
    void initialize() {
        RocksDB.loadLibrary();
        final Options options = new Options();
        options.setCreateIfMissing(true);

        File blockchainDbDir = new File(LOCATION_TO_BE_STORED, DB_NAME_BLOCKCHAIN);
        try {
            Files.createDirectories(blockchainDbDir.getParentFile().toPath());
            Files.createDirectories(blockchainDbDir.getAbsoluteFile().toPath());
            db = RocksDB.open(options, blockchainDbDir.getAbsolutePath());
        } catch (IOException | RocksDBException ex) {
            log.error("Error initializng RocksDB for storing Blockchain, check configurations and permissions, exception: {}, message: {}, stackTrace: {}",
                    ex.getCause(), ex.getMessage(), ex.getStackTrace());
        }
        log.info("RocksDB for storing Blockchain initialized and ready to use");

        File transactionsDbDir = new File(LOCATION_TO_BE_STORED, DB_NAME_TRAMSACTIONS);
        try {
            Files.createDirectories(transactionsDbDir.getParentFile().toPath());
            Files.createDirectories(transactionsDbDir.getAbsoluteFile().toPath());
            db = RocksDB.open(options, transactionsDbDir.getAbsolutePath());
        } catch (IOException | RocksDBException ex) {
            log.error("Error initializng RocksDB for storing Transactions, check configurations and permissions, exception: {}, message: {}, stackTrace: {}",
                    ex.getCause(), ex.getMessage(), ex.getStackTrace());
        }
        log.info("RocksDB for storing Transactions initialized and ready to use");


        File nodesDbDir = new File(LOCATION_TO_BE_STORED, DB_NAME_NODES);
        try {
            Files.createDirectories(nodesDbDir.getParentFile().toPath());
            Files.createDirectories(nodesDbDir.getAbsoluteFile().toPath());
            db = RocksDB.open(options, nodesDbDir.getAbsolutePath());
        } catch (IOException | RocksDBException ex) {
            log.error("Error initializng RocksDB for storing Node Information, check configurations and permissions, exception: {}, message: {}, stackTrace: {}",
                    ex.getCause(), ex.getMessage(), ex.getStackTrace());
        }
        log.info("RocksDB for storing Node Information initialized and ready to use");
    }


    @Override
    public void save(String key, String value) {
        log.info("----SAVE----");
        try {
            db.put(key.getBytes(), value.getBytes());
        } catch (RocksDBException e) {
            log.error("Error saving entry in RocksDB, cause: {}, message: {}", e.getCause(), e.getMessage());
        }
    }

    @Override
    public String find(String key) {
        log.info("----FIND----");
        String result = null;
        try {
            byte[] bytes = db.get(key.getBytes());
            if(bytes == null) return null;
            result = new String(bytes);
        } catch (RocksDBException e) {
            log.error("Error retrieving the entry in RocksDB from key: {}, cause: {}, message: {}", key, e.getCause(), e.getMessage());
        }
        return result;
    }

    @Override
    public void delete(String key) {
        log.info("----DELETE----");
        try {
            db.delete(key.getBytes());
        } catch (RocksDBException e) {
            log.error("Error deleting entry in RocksDB, cause: {}, message: {}", e.getCause(), e.getMessage());
        }
    }
}
