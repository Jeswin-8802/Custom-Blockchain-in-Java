package io.mycrypto.controller;

import io.mycrypto.dto.CreateWalletRequestDto;
import io.mycrypto.repository.KeyValueRepository;
import io.mycrypto.service.BlockchainService;
import io.mycrypto.util.RSAKeyPairGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@Slf4j
@RestController
public class BlockchainController {

    @Autowired
    KeyValueRepository<String, String> rocksDB;

    @Autowired
    BlockchainService service;

    @PostMapping("createWallet/")
    public ResponseEntity<String> createNewWallet(@RequestBody CreateWalletRequestDto requestDto) {
        new RSAKeyPairGenerator().generateKeyPairToFile(requestDto.getWalletName());
        return ResponseEntity.ok("Created");
    }

    @GetMapping("wallet/{key}")
    public ResponseEntity<String> findWallet(@PathVariable("key") String key) {
        String result = rocksDB.find(key, "Wallets");
        if (result == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(result);
    }

    @GetMapping("blockpath/{key}")
    public ResponseEntity<String> findBlockPath(@PathVariable("key") String key) {
        String result = service.fetchBlockPath(key);
        if (result == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(result);
    }

    @GetMapping("blockcontent/{key}")
    public ResponseEntity<String> findBlockContent(@PathVariable("key") String key) throws IOException {
        return ResponseEntity.ok(service.fetchBlockContent(key));
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<String> delete(@PathVariable("key") String key) {
        rocksDB.delete(key, "Wallets");
        return ResponseEntity.ok(key);
    }

    @GetMapping("/create-genesis-block")    // be careful with his API, must be used only once by the admin
    public ResponseEntity<String> createGenesisBlock() {
        return ResponseEntity.ok(service.createGenesisBlock());
    }
}
