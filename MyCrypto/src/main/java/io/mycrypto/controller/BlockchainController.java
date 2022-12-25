package io.mycrypto.controller;

import io.mycrypto.dto.CreateWalletRequestDto;
import io.mycrypto.dto.MakeTransactionDto;
import io.mycrypto.dto.VerifyAddressRequestDto;
import io.mycrypto.service.ResponseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
     * @param requestDto Holds the Wallet Name and the name of the key (the key itself will be stored locally in the RESOURCES folder)
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

    /**
     * Select UTXOs for a transaction that adds up to a given amount by most efficient algorithm to the least efficient algorithm;
     * <i> The options for the different types of algorithms are further described in the function used in the service layer </i>;
     * <b> Note: This endpoint is only to view the available UTXOs to make the said transaction and does not make the transaction </b>
     *
     * @param amount     The amount you need to transact
     * @param algorithm  The type of algorithm to use (allowed options: "most efficient", "largest closest", "smallest closest")
     * @param walletName The name of the wallet from which we want to make the transaction
     * @return HTTP response
     */
    @GetMapping("optimized-utxo-fetch")
    public ResponseEntity<Object> fetchUTXOsForTransaction(@RequestParam("amount") String amount, @RequestParam("algorithm") Integer algorithm, @RequestParam("wallet-name") String walletName, @RequestParam("transaction-fee") String transactionFee) {
        return service.fetchUTXOsForTransaction(amount, algorithm, walletName, transactionFee);
    }

    @PostMapping("make-transaction")
    public ResponseEntity<Object> maketransaction(@RequestBody MakeTransactionDto requestDto) {
        return service.makeTransaction(requestDto);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Deletes any value from any of the 6 DBs by its key
     *
     * @param db  Name of the DB; Must be the same as the allowed names of the 6 DBs; Refer the properties file
     * @param key RocksDB stores data in the form of key value pairs, therefore to get or delete you use a key
     * @return HTTP response
     */
    @DeleteMapping("delete")
    public ResponseEntity<Object> delete(@RequestParam(name = "db") String db, @RequestParam(name = "key") String key) {
        return service.delete(key, db);
    }
}
