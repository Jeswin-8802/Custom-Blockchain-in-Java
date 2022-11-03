package io.mycrypto.controller;

import io.mycrypto.dto.CreateWalletRequestDto;
import io.mycrypto.dto.VerifyAddressRequestDto;
import io.mycrypto.dto.WalletResponseDto;
import io.mycrypto.service.BlockchainService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
public class BlockchainController {

    @Autowired
    BlockchainService service;

    @PostMapping("create-wallet")
    public ResponseEntity<WalletResponseDto> createNewWallet(@RequestBody CreateWalletRequestDto requestDto) {
        return service.createWallet(requestDto);
    }

    @GetMapping("wallet/{key}")
    public ResponseEntity<WalletResponseDto> findWallet(@PathVariable("key") String key) {
        return service.fetchWalletInfo(key);
    }

    @GetMapping("blockpath/{key}")
    public ResponseEntity<Object> findBlockPath(@PathVariable("key") String key) {
        return service.fetchBlockPath(key);
    }

    @GetMapping("blockcontent")
    public ResponseEntity<Object> findBlockContent(@RequestParam(name = "block-hash") String hash) {
        return service.constructResponseForFetchBlockContent(hash);
    }

    @DeleteMapping("{key}")
    public ResponseEntity<Object> delete(@RequestParam(name = "db") String db, @PathVariable("key") String key) {
        return service.delete(key, db);
    }

    @GetMapping("create-genesis-block")    // be careful with his API, must be used only once by the admin
    public ResponseEntity<Object> createGenesisBlock() {
        return service.createGenesisBlock();
    }

    @PostMapping("check-address-validity")
    public ResponseEntity<Object> checkAddressValidity(@RequestBody VerifyAddressRequestDto request) {
        return service.constructResponseForValidateAddress(request);
    }

    @GetMapping("make-transaction")
    public ResponseEntity<String> maketransaction() {
        service.makeTransaction();
        return ResponseEntity.ok().build();
    }
}
