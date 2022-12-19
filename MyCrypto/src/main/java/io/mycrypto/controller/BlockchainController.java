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

    // ---------BLOCK---------------------------------------------------------------------------------------------------

    /**
     * Fetches the Block Information of a block by its hash
     *
     * @param hash Block Hash
     * @return HTTP response
     */
    @GetMapping("fetch-block-content")
    public ResponseEntity<Object> findBlockContent(@RequestParam(name = "block-hash") String hash) {
        return service.constructResponseForFetchBlockContent(hash);
    }

    /**
     * Fetches the Block Information of a block by its height
     *
     * @param height Block height .i.e. its count
     * @return HTTP response
     */
    @GetMapping("fetch-block-content-by-height/{height}")
    public ResponseEntity<Object> fetchBlockContentByHeight(@PathVariable("height") String height) {
        return service.constructResponseForFetchBlockContentByHeight(height);
    }

    /**
     * Creates a Genesis Block; Will create it if the directory where the blocks are stored is empty; Avoid this operation if you are not the first node starting the chain
     *
     * @param walletName Name of the Wallet; The wallet to which the block will be associated to
     * @return HTTP response
     */
    @GetMapping("create-genesis-block")    // be careful with his API, must be used only once by the admin
    public ResponseEntity<Object> createGenesisBlock(@RequestParam(name = "wallet-name") String walletName) {
        return service.createGenesisBlock(walletName);
    }

    // ---------WALLET--------------------------------------------------------------------------------------------------

    /**
     * Creates a new wallet
     *
     * @param requestDto Holds the Wallet Name and the name of the key you want to store it as locally
     * @return HTTP response
     */
    @PostMapping("create-wallet")
    public ResponseEntity<Object> createNewWallet(@RequestBody CreateWalletRequestDto requestDto) {
        return service.createWallet(requestDto);
    }

    /**
     * Fetches Wallet information
     *
     * @param name name of the Wallet
     * @return HTTP response
     */
    @GetMapping("wallet/{name}")
    public ResponseEntity<Object> fetchWallet(@PathVariable("name") String name) {
        return service.fetchWalletInfo(name);
    }

    /**
     * Fetches all the wallets you own
     *
     * @return HTTP response
     */
    @GetMapping("fetch-all-wallets")
    public ResponseEntity<Object> fetchAllWallets() {
        return service.fetchAllWallets();
    }

    /**
     * Checks the Validity of the Wallet's Address
     *
     * @param request holds the address of the wallet and hash of the pvt key
     * @return HTTP response
     */
    @PostMapping("check-address-validity")
    public ResponseEntity<Object> checkAddressValidity(@RequestBody VerifyAddressRequestDto request) {
        return service.constructResponseForValidateAddress(request);
    }

    // ---------TRANSACTION---------------------------------------------------------------------------------------------

    /**
     * Fetches Transaction Information
     *
     * @param id Transaction ID/hash
     * @return HTTP response
     */
    @GetMapping("fetch-transaction")
    public ResponseEntity<Object> fetchTransaction(@RequestParam(name = "id") String id) {
        return service.constructResponseForFetchTransaction(id);
    }

    /**
     * Fetches all UTXOs in a Wallet; Essentially returns the dodo coins in the wallet
     *
     * @param address Wallet Address
     * @return HTTP response
     */
    @GetMapping("fetch-utxos")
    public ResponseEntity<Object> fetchUTXOs(@RequestParam(name = "dodo-coin-address") String address) {
        return service.fetchUTXOs(address);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Deletes any value from the 6 DBs by its key
     *
     * @param db Name of the DB; Must be the same as the allowed names of the 6 DBs; Refer the properties file
     * @param key RocksDB stores data in the form of key value pairs, therefore to get or delete you need ust the key
     * @return HTTP response
     */
    @DeleteMapping("delete")
    public ResponseEntity<Object> delete(@RequestParam(name = "db") String db, @RequestParam(name = "key") String key) {
        return service.delete(key, db);
    }

    @GetMapping("make-transaction")
    public ResponseEntity<String> maketransaction() {
        service.makeTransaction();
        return ResponseEntity.ok().build();
    }
}
