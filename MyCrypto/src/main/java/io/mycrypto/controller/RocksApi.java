package io.mycrypto.controller;

import io.mycrypto.repository.KeyValueRepository;
import io.mycrypto.repository.RocksDBRepositoryImpl;
import io.mycrypto.util.RSAKeyPairGenerator;
import io.mycrypto.util.Utility;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
public class RocksApi {

    private final KeyValueRepository<String, String> rocksDB;

    public RocksApi(KeyValueRepository<String, String> rocksDB) {
        this.rocksDB = rocksDB;
    }

    @PostMapping("/{key}")
    public ResponseEntity<String> save(@PathVariable("key") String key, @RequestBody String value) {
        log.info("RocksApi.save");
        new RSAKeyPairGenerator().generateKeyPairToFile("PUBLIC_KEY_NAME", "PRIVATE_KEY_NAME", rocksDB);
        Utility.createGenesisBlock(rocksDB);
        return ResponseEntity.ok(value);
    }

    @GetMapping("/{key}")
    public ResponseEntity<String> find(@PathVariable("key") String key) {
        log.info("RocksApi.find");
        String result = rocksDB.find(key, "Wallets");
        if (result == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<String> delete(@PathVariable("key") String key) {
        log.info("RocksApi.delete");
        rocksDB.delete(key, "Wallets");
        return ResponseEntity.ok(key);
    }
}
