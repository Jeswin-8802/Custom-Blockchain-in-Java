package io.mycrypto.controller;

import io.mycrypto.dto.CreateWalletRequestDto;
import io.mycrypto.dto.VerifyAddressRequestDto;
import io.mycrypto.service.ResponseService;
import lombok.extern.slf4j.Slf4j;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.FileNotFoundException;

@Slf4j
@RestController
public class BlockchainController {

    @Autowired
    ResponseService service;

    @PostMapping("create-wallet")
    public ResponseEntity<Object> createNewWallet(@RequestBody CreateWalletRequestDto requestDto) {
        return service.createWallet(requestDto);
    }

    @GetMapping("wallet/{key}")
    public ResponseEntity<Object> findWallet(@PathVariable("key") String key) {
        return service.fetchWalletInfo(key);
    }

    @GetMapping("fetch-transaction")
    public ResponseEntity<Object> fetchTransaction(@RequestParam(name = "id") String id) {
        return service.constructResponseForFetchTransaction(id);
    }

    @GetMapping("fetch-block-content")
    public ResponseEntity<Object> findBlockContent(@RequestParam(name = "block-hash") String hash) {
        return service.constructResponseForFetchBlockContent(hash);
    }

    @GetMapping("fetch-block-content-by-height/{height}")
    public ResponseEntity<Object> fetchBlockContentByHeight(@PathVariable("height") String height) {
        return service.constructResponseForFetchBlockContentByHeight(height);
    }

    @DeleteMapping("delete")
    public ResponseEntity<Object> delete(@RequestParam(name = "db") String db, @RequestParam(name = "key") String key) {
        return service.delete(key, db);
    }

    @GetMapping("create-genesis-block")    // be careful with his API, must be used only once by the admin
    public ResponseEntity<Object> createGenesisBlock(@RequestParam(name = "wallet-name") String walletName) {
        return service.createGenesisBlock(walletName);
    }

    @PostMapping("check-address-validity")
    public ResponseEntity<Object> checkAddressValidity(@RequestBody VerifyAddressRequestDto request) {
        return service.constructResponseForValidateAddress(request);
    }

    @GetMapping("fetch-utxos")
    public ResponseEntity<Object> fetchUTXOs(@RequestParam(name = "dodo-coin-address") String address) {
        return service.fetchUTXOs(address);
    }

    @GetMapping("fetch-all-wallets")
    public ResponseEntity<Object> fetchWallets() {
        return service.fetchAllWallets();
    }

    @GetMapping("make-transaction")
    public ResponseEntity<String> maketransaction() {
        service.makeTransaction();
        return ResponseEntity.ok().build();
    }
}
