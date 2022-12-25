package io.mycrypto.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mycrypto.dto.*;
import io.mycrypto.entity.Block;
import io.mycrypto.exception.MyCustomException;
import io.mycrypto.repository.KeyValueRepository;
import io.mycrypto.service.block.BlockService;
import io.mycrypto.service.transaction.TransactionService;
import io.mycrypto.service.wallet.WalletService;
import io.mycrypto.util.Utility;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.util.Strings;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

@Service
@Slf4j
public class ResponseService {
    private static final String OUTER_RESOURCE_FOLDER = "RESOURCES";
    private static final String FOLDER_TO_STORE_BLOCKS = "blockchain";
    private static final String BLOCKCHAIN_STORAGE_PATH;

    static {
        BLOCKCHAIN_STORAGE_PATH = SystemUtils.USER_DIR + Utility.osAppender() + OUTER_RESOURCE_FOLDER + Utility.osAppender() + FOLDER_TO_STORE_BLOCKS + Utility.osAppender();
    }

    @Autowired
    BlockService blockService;
    @Autowired
    WalletService walletService;
    @Autowired
    TransactionService transactionService;
    @Autowired
    KeyValueRepository<String, String> rocksDB;


    // ---------BLOCKS--------------------------------------------------------------------------------------------------------------

    /**
     * @param hash The block hash that the block is referred to in the DB
     * @return Response Object
     */
    public ResponseEntity<Object> constructResponseForFetchBlockContent(String hash) {
        try {
            return ResponseEntity.ok(blockService.fetchBlockContent(hash));
        } catch (NullPointerException e) {
            log.error("Wrong hash provided. The fetch from DB method returns NULL");
            e.printStackTrace();
            return ResponseEntity.noContent().build();
        } catch (IOException e) {
            log.error("Error occurred while referring to new file PATH..");
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Utility.constructJsonResponse("error-msg", "File path referred to in DB is wrong or the file does not exist in that location"));
        } catch (ParseException e) {
            log.error("Error occurred while parsing contents of block file to JSON");
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Utility.constructJsonResponse("error-msg", "Error while parsing Block data"));
        }
    }

    /**
     * @param height The block height; Represents the count of the block
     * @return Response Object
     */
    public ResponseEntity<Object> constructResponseForFetchBlockContentByHeight(String height) {
        try {
            return ResponseEntity.ok(blockService.fetchBlockContentByHeight(Integer.parseInt(height)));
        } catch (FileNotFoundException e) {
            log.error("Invalid height specified... Unable to find {}", "\\blk" + String.format("%010d", Integer.parseInt(height) + 1) + ".dat");
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Utility.constructJsonResponse("msg", "Block with height " + height + " was not found"));
        } catch (ParseException e) {
            log.error("error while parsing contents in file " + BLOCKCHAIN_STORAGE_PATH + "\\blk" + String.format("%010d", Integer.parseInt(height) + 1) + ".dat to JSON");
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Utility.constructJsonResponse("msg", "Couldn't Parse block contents to JSON..."));
        }
    }

    /**
     * Creates the genesis block. Adds in the coinbase transaction to initialize it as a valid block.
     *
     * @param walletName Name of the Wallet; Is specified to determine the owner of the block miner
     * @return Response Object
     */
    public ResponseEntity<Object> createGenesisBlock(String walletName) {

        File base = new File(BLOCKCHAIN_STORAGE_PATH);
        if (base.isDirectory())
            log.info("The directory \"blockchain\" found... \nAdding blocks...");
        else {
            if (base.mkdir())
                log.info("directory \"blockchain\" created... \nAdding blocks...");
            else {
                log.error("Unable to create dir \"blockchain\"");
                return ResponseEntity.badRequest().body(Utility.constructJsonResponse("error", "unable to create dir \"blockchain\"..."));
            }
        }

        // check for if genesis block already exists
        List<String> files = Utility.listFilesInDirectory(BLOCKCHAIN_STORAGE_PATH);

        if (!ObjectUtils.isEmpty(files) &&
                files.get(0).equals("INVALID DIRECTORY"))
            return ResponseEntity.internalServerError().body(Utility.constructJsonResponse("msg", "The configuration set for the block storage path is invalid as that directory wasn't found"));
        else if (!ObjectUtils.isEmpty(files)) {
            log.info("Files present in directory \"{}\" : \n{}", BLOCKCHAIN_STORAGE_PATH, files);
            return ResponseEntity.badRequest().body(Utility.constructJsonResponse("msg", "genesis block already exists"));
        }

        try {
            Block genesis;
            try {
                genesis = blockService.mineGenesisBlock(walletName);
            } catch (MyCustomException e) {
                return ResponseEntity.internalServerError().body(e.getMessageAsJSONString());
            }
            return ResponseEntity.ok(new JSONParser().parse(blockService.saveBlock(genesis, "blk" + String.format("%010d", genesis.getHeight() + 1))));
        } catch (ParseException e) {
            log.error("Error while constructing response for createGenesisBlock()..");
            e.printStackTrace();
        }
        return null;
    }


    // ---------WALLET--------------------------------------------------------------------------------------------------------------

    /**
     * @param request Stores the Information required to create a new Wallet in a DTO
     * @return Response Object
     */
    public ResponseEntity<Object> createWallet(CreateWalletRequestDto request) {
        // validation
        if (Strings.isEmpty(request.getWalletName())) {
            log.error("Wallet Name cannot be empty");
            return ResponseEntity.badRequest().body(Utility.constructJsonResponse("msg", "Wallet Name cannot be empty"));
        }

        WalletInfoDto value = null;
        try {
            value = Utility.generateKeyPairToFile(request.getKeyName());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        log.info("\n {}", request.getWalletName());
        try {
            rocksDB.save(request.getWalletName(), new ObjectMapper().writeValueAsString(value), "Wallets");
            assert value != null;
            rocksDB.save(value.getAddress(), request.getWalletName(), "Nodes");
            rocksDB.save(value.getAddress(), "EMPTY", "Accounts");
            return constructWalletResponseFromInfo(new ObjectMapper().writeValueAsString(value));
        } catch (JsonProcessingException e) {
            log.error("Error while trying to parse WalletInfoDto to JSON...");
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Utility.constructJsonResponse("msg", "Encountered an error while parsing..."));
        }
    }

    /**
     * @param data This parameter stores the WalletInfo as a JSON string which will then be converted to a Response Object
     * @return Response Object
     */
    private ResponseEntity<Object> constructWalletResponseFromInfo(String data) {
        WalletInfoDto info = null;
        try {
            info = new ObjectMapper().readValue(data, WalletInfoDto.class);
            log.info("Wallet Contents: \n{}", new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(info));
        } catch (JsonProcessingException e) {
            log.error("Error while parsing json to WalletInfoDto..");
            e.printStackTrace();
        }
        WalletResponseDto response = new WalletResponseDto();
        assert info != null;
        response.setPublicKey(info.getPublicKey());
        response.setPrivateKey(info.getPrivateKey());
        response.setHash160(info.getHash160());

        // calculate balance from AccountDB
        String transactions = rocksDB.find(info.getAddress(), "Accounts");
        if (transactions.equals("EMPTY"))
            response.setBalance(new BigDecimal("0.0"));
        else {
            log.info(Utility.beautify(transactions));

            JSONObject transactionsJSON;
            try {
                transactionsJSON = new ObjectMapper().readValue(transactions, JSONObject.class);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                return ResponseEntity.internalServerError().body(Utility.constructJsonResponse("msg", "Error while Parsing UTXO data from Accounts DB to JSON..."));
            }

            BigDecimal sum = new BigDecimal("0.0");
            List<UTXODto> UTXOs;
            try {
                UTXOs = transactionService.retrieveAllUTXOs(transactionsJSON);
            } catch (JsonProcessingException e) {
                log.error("Error while parsing data in Transaction DB to an object of class <Transaction>");
                e.printStackTrace();
                return ResponseEntity.internalServerError().body(Utility.constructJsonResponse("msg", "Couldn't parse transaction data(String) to Object<Transaction>..."));
            }

            if (UTXOs == null)
                return ResponseEntity.internalServerError().body(Utility.constructJsonResponse("msg", "Transactions present in wallet not found in Transactions DB..."));
            for (UTXODto utxo : UTXOs)
                sum = sum.add(utxo.getAmount());
            response.setBalance(sum);
            try {
                log.info("UTXOs for address {} : \n{}", info.getAddress(), new ObjectMapper().writer().withDefaultPrettyPrinter().writeValueAsString(WalletUTXOResponseDto.builder()
                        .UTXOs(UTXOs)
                        .total(sum)
                        .build()
                ));
            } catch (JsonProcessingException e) {
                log.error("Error while parsing WalletUTXOsResponseDto to JSON..");
                e.printStackTrace();
                return ResponseEntity.internalServerError().body(Utility.constructJsonResponse("msg", "Couldn't parse WalletUTXOsResponseDto to JSON..."));
            }
        }
        response.setAddress(info.getAddress());
        return ResponseEntity.ok(response);
    }

    /**
     * @param walletName Name of the Wallet
     * @return Response Object
     */
    public ResponseEntity<Object> fetchWalletInfo(String walletName) {
        String value = rocksDB.find(walletName, "Wallets");
        log.info("Name of wallet: {}", walletName);
        if (value == null) return ResponseEntity.noContent().build();
        return constructWalletResponseFromInfo(value);
    }

    /**
     * @return Response Object
     */
    public ResponseEntity<Object> fetchAllWallets() {
        return ResponseEntity.ok(walletService.fetchWallets());
    }

    /**
     * Verifies your dodo-coin-address by the hash-160 checksum in the address; Usefull when you want to know if the address you are passing is valid and there were no mistakes made when entering it
     *
     * @param request Stores the wallet address in a DTO
     * @return Response Object
     */
    public ResponseEntity<Object> constructResponseForValidateAddress(VerifyAddressRequestDto request) {
        try {
            if (walletService.verifyAddress(request.getAddress(), request.getHash160()))
                return ResponseEntity.ok(Utility.constructJsonResponse("msg", "valid"));
        } catch (MyCustomException e) {
            return ResponseEntity.badRequest().body(e.getMessageAsJSONString());
        }
        return ResponseEntity.ok(Utility.constructJsonResponse("msg", "invalid"));
    }

    // ---------TRANSACTION--------------------------------------------------------------------------------------------------------------

    /**
     * @param id The Transaction ID
     * @return Response Object
     */
    public ResponseEntity<Object> constructResponseForFetchTransaction(String id) {
        try {
            return ResponseEntity.ok(transactionService.fetchTransaction(id));
        } catch (NullPointerException e) {
            log.error("Could not find transaction with id {}", id);
            return ResponseEntity.badRequest().body(Utility.constructJsonResponse("msg", String.format("transaction %s not found...", id)));
        }
    }

    /**
     * @param address Wallet Address where the UTXOs are stored
     * @return Response Object
     */
    public ResponseEntity<Object> fetchUTXOs(String address) {
        BigDecimal sum = new BigDecimal("0.0");
        List<UTXODto> UTXOs;
        WalletUTXOResponseDto response;
        try {
            String transactions = rocksDB.find(address, "Accounts");

            if (Strings.isEmpty(transactions)) {
                log.error(String.format("No transaction(s) found with address: %s", address));
                return ResponseEntity.badRequest().body(Utility.constructJsonResponse("msg", String.format("No transaction(s) found with address: %s", address)));
            }

            try {
                UTXOs = transactionService.retrieveAllUTXOs(new ObjectMapper().readValue(transactions, JSONObject.class));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                return ResponseEntity.internalServerError().body(Utility.constructJsonResponse("err", "Error while casting UTXO info to JSONObject from AccountsDB..."));
            }
            if (UTXOs == null)
                return ResponseEntity.internalServerError().body(Utility.constructJsonResponse("msg", "Transactions present in wallet not found in Transactions DB..."));
            for (UTXODto utxo : UTXOs)
                sum = sum.add(utxo.getAmount());
            response = WalletUTXOResponseDto.builder()
                    .UTXOs(UTXOs)
                    .total(sum)
                    .build();
            log.info("UTXOs for address {} : \n{}", address, new ObjectMapper().writer().withDefaultPrettyPrinter().writeValueAsString(response));
        } catch (JsonProcessingException e) {
            log.error("Error while parsing WalletUTXOsResponseDto to JSON..");
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Utility.constructJsonResponse("msg", "Couldn't parse WalletUTXOsResponseDto to JSON..."));
        }
        return ResponseEntity.ok(response);
    }


    /**
     * This function gives a list of the possible UTXOs that gets selected from a particular wallet
     * for a transaction i.e. The sum of amounts of the UTXOs must make up a value greater than or equal to
     * the sum of the amount and the transaction fee
     *
     * @param amount         Amount to transact
     * @param algorithm      The type of algorithm to use for filtering the UTXOs
     * @param walletName     The name of the wallet from which the amount is to be withdrawn
     * @param transactionFee The transaction fee that will be counted for the current transaction
     * @return Response Object
     */
    public ResponseEntity<Object> fetchUTXOsForTransaction(String amount, Integer algorithm, String walletName, String transactionFee) {
        // fetching Wallet Address
        WalletInfoDto walletInfo;
        try {
            if (Strings.isEmpty(walletName)) {
                String info = rocksDB.find("default", "Wallets");
                if (Strings.isEmpty(info))
                    return ResponseEntity.internalServerError().body(Utility.constructJsonResponse("msg", "default wallet not found..."));
                walletInfo = new ObjectMapper().readValue(info, WalletInfoDto.class);
            } else {
                String info = rocksDB.find(walletName, "Wallets");
                if (Strings.isEmpty(info))
                    return ResponseEntity.internalServerError().body(Utility.constructJsonResponse("msg", String.format("Wallet \"%s\" not found in WalletsDB...", walletName)));
                walletInfo = new ObjectMapper().readValue(info, WalletInfoDto.class);
            }
        } catch (JsonProcessingException e) {
            log.error("Error occurred while parsing Wallet content from String to DTO object");
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }

        List<UTXODto> UTXOs;
        try {
            UTXOs = transactionService.selectivelyFetchUTXOs(new BigDecimal(amount), algorithm, walletInfo.getAddress(), Strings.isEmpty(transactionFee) ? new BigDecimal(0) : new BigDecimal(transactionFee));
        } catch (MyCustomException e) {
            return ResponseEntity.badRequest().body(e.getMessageAsJSONString());
        }
        BigDecimal total = new BigDecimal(0);
        for (UTXODto utxo : UTXOs)
            total = total.add(utxo.getAmount());
        return ResponseEntity.ok(WalletUTXOResponseDto.builder()
                .UTXOs(UTXOs)
                .total(total)
                .build());
    }

    /**
     * Returns the Transaction Information after it has been successfully completed;
     * Also validates important parameters within the requestDto necessary for the transaction
     *
     * @param requestDto Contains all the information to perform a dodo-coin transaction
     * @return Response Object
     */
    public ResponseEntity<Object> makeTransaction(MakeTransactionDto requestDto) {
        // validation for To Address
        if (Strings.isEmpty(requestDto.getTo())) {
            log.error("The field \"To\" cannot be empty");
            return ResponseEntity.badRequest().build();
        }
        // validation for Amount
        if (ObjectUtils.isEmpty(requestDto.getAmount())) {
            log.error("The field \"amount\" cannot be empty");
            return ResponseEntity.badRequest().build();
        }
        // Using the default Wallet if the From address is empty
        if (Strings.isEmpty(requestDto.getFrom())) {
            String defaultWalletInfo = rocksDB.find("default", "Wallets");
            if (Strings.isEmpty(defaultWalletInfo)) {
                log.error("\"default\" wallet not found...");
                return ResponseEntity.internalServerError().build();
            }
            requestDto.setFrom(defaultWalletInfo);
        } else {
            String walletNameFromAddress = rocksDB.find(requestDto.getFrom(), "Nodes");
            // validating from address
            if (Strings.isEmpty(walletNameFromAddress)) {
                log.error(String.format("No wallet found with address %s", requestDto.getFrom()));
                return ResponseEntity.badRequest().build();
            }
            String walletInfoString = rocksDB.find(walletNameFromAddress, "Wallets");
            if (Strings.isEmpty(walletInfoString)) {
                log.error("Wallet not found in WalletDB although it was found in NodesDB");
                return ResponseEntity.internalServerError().build();
            }
            requestDto.setFrom(walletInfoString);
        }

        if (Strings.isEmpty(rocksDB.find(requestDto.getTo(), "Nodes"))) {
            log.error("Invalid To address..");
            return ResponseEntity.badRequest().build();
        }

        try {
            return ResponseEntity.ok(transactionService.makeTransaction(requestDto));
        } catch (MyCustomException e) {
            return ResponseEntity.internalServerError().body(e.getMessageAsJSONString());
        }
    }

    // ----------------------------------------------------------------------------------------------------------------------------------

    /**
     * <b>Deletes</b> any value from any of the DBs available by its key
     *
     * @param key Can be any key that points to a specific value in a given DB
     * @param db  The Name of the DB
     * @return Response Object
     */
    public ResponseEntity<Object> delete(String key, String db) {
        if (!rocksDB.delete(key, db))
            return ResponseEntity.badRequest().build();
        return ResponseEntity.ok().build();
    }

}
