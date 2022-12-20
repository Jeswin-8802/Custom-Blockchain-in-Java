package io.mycrypto.service.transaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.mycrypto.dto.WalletInfoDto;
import io.mycrypto.dto.UTXODto;
import io.mycrypto.entity.Output;
import io.mycrypto.entity.ScriptPublicKey;
import io.mycrypto.entity.Transaction;
import io.mycrypto.exception.MyCustomException;
import io.mycrypto.repository.KeyValueRepository;
import io.mycrypto.util.UTXOFilterAlgorithms;
import io.mycrypto.util.Utility;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class TransactionService {
    @Autowired
    KeyValueRepository<String, String> rocksDB;

    private static final String BLOCK_REWARD = "13.0";

    // AVAILABLE ALGORITHMS --------------------------------------

    // refer : https://www.baeldung.com/cs/subset-of-numbers-closest-to-target
    private static final String OPTIMIZED = "OPTIMIZED"; // Meet In the Middle Approach; Selects the UTXOs whose sum closest represents a target amount

    private static final String HIGHEST_SORTED = "HIGHEST_SORTED"; // trivial

    private static final String LOWEST_SORTED = "LOWEST_SORTED"; // trivial

    private static final String RANDOM = "RANDOM"; // trivial

    private static final List<String> ALLOWED_ALGORITHMS;

    static {
        ALLOWED_ALGORITHMS = List.of(OPTIMIZED, HIGHEST_SORTED, LOWEST_SORTED, RANDOM);
    }

    // -----------------------------------------------------------

    /**
     * Fetches transaction by its ID
     *
     * @param id Transaction ID
     * @return Transaction Information in JSONObject format
     * @throws NullPointerException
     */
    public JSONObject fetchTransaction(String id) throws NullPointerException {
        String json = rocksDB.find(id, "Transactions");
        try {
            if (json != null)
                return (JSONObject) new JSONParser().parse(json);
            throw new NullPointerException();
        } catch (ParseException e) {
            log.error("Error while parsing contents of {} in DB to JSON", id);
            e.printStackTrace();
        }
        return null;
    }

    /**
     * @param tx Transaction Object holding all the Transaction Information
     * @return True or False representing if the Transaction was saved successfully
     */
    public boolean saveTransaction(Transaction tx) throws MyCustomException {
        String json = null;
        try {
            ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
            json = ow.writeValueAsString(tx);
            tx.setSize(new BigInteger(String.valueOf(json.replace(" ", "").length() - "\"size\":null\"weight\": null".length())));
            tx.setWeight(new BigInteger("4").multiply(tx.getSize()).subtract(new BigInteger(String.valueOf(tx.getInputs().size()))));
            json = ow.writeValueAsString(tx);
            log.info("{} ==> \n{}", tx.getTransactionId(), json);
        } catch (JsonProcessingException e) {
            log.error("Error occurred while parsing Object(Transaction) to json");
            e.printStackTrace();
            throw new MyCustomException("Error occurred while parsing Object(Transaction) to json");
        }

        rocksDB.save(tx.getTransactionId(), json, "Transactions");


        // get toAddress of Outputs and the subsequent VOUT
        Map<String, Long> map = new HashMap<>();

        for (Output out : tx.getOutputs())
            map.put(out.getScriptPubKey().getAddress(), out.getN());

        // save transaction information to AccountsDB if transaction has your wallet address

        // fetch list of wallet info to obtain all wallet addresses
        Map<String, String> info = rocksDB.getList("Wallets");
        if (info == null) {
            log.error("No content found in Wallets DB, Looks like you haven't created a wallet yet...");
            throw new MyCustomException("No content found in Wallets DB, Looks like you haven't created a wallet yet...");
        }
        for (Map.Entry<String, String> i : info.entrySet()) {
            WalletInfoDto temp = null;
            try {
                temp = new ObjectMapper().readValue(i.getValue(), WalletInfoDto.class);
            } catch (JsonProcessingException e) {
                log.error("Error occurred while trying to parse data from Wallets DB to that of type <WalletInfoDto>...");
                e.printStackTrace();
                throw new MyCustomException("Error occurred while trying to parse data from Wallets DB to that of type <WalletInfoDto>...");
            }
            if (temp.getAddress().equals(tx.getTo()))
                addTransactionToAccounts(temp.getAddress(), tx.getTransactionId(), map.get(temp.getAddress()));
        }

        return true;
    }

    /**
     * Adds transaction information to associated accounts into DB to keep track of UTXOs
     *
     * @param address Wallet Address
     * @param txId    Transaction ID
     * @param vout    The VOUT value for the Output in a Transaction
     */
    void addTransactionToAccounts(String address, String txId, long vout) {
        String existingTransactions = rocksDB.find(address, "Accounts");
        rocksDB.save(address, (existingTransactions.equals("EMPTY") ? "" : existingTransactions + " ") + txId + "," + vout, "Accounts");
    }


    /**
     * @param transactions A list of all (transactionId, VOUT) for a given wallet
     * @return A list of UTXOs liked to a given Wallet
     * @throws JsonProcessingException
     */
    public List<UTXODto> retrieveAllUTXOs(String[] transactions) throws JsonProcessingException {
        List<UTXODto> result = new ArrayList<>();
        for (String s : transactions) {
            String[] temp = s.split(",");
            String transaction = rocksDB.find(temp[0], "Transactions");
            if (transaction == null) {
                log.error("Could not find transaction {} obtained from Account DB in Transactions DB", temp[0]);
                return null;
            }
            BigDecimal amount = new ObjectMapper().readValue(transaction, Transaction.class).getOutputs().get(Integer.parseInt(temp[1])).getAmount();
            result.add(UTXODto.builder()
                    .transactionId(temp[0])
                    .vout(Long.parseLong(temp[1]))
                    .amount(amount)
                    .build()
            );
        }
        return result;
    }

    public Transaction constructCoinbaseTransaction(WalletInfoDto info) throws MyCustomException {
        Transaction coinbase = new Transaction("", info.getAddress());
        coinbase.setNumInputs(0);
        coinbase.setInputs(new ArrayList<>());
        coinbase.setNumOutputs(1);

        // construct output
        Output output = new Output();
        output.setAmount(new BigDecimal(BLOCK_REWARD));
        output.setN(0);

        // construct Script Public Key (Locking Script)
        ScriptPublicKey script = new ScriptPublicKey(info.getHash160(), info.getAddress());
        output.setScriptPubKey(script);

        // set output
        coinbase.setOutputs(List.of(output));
        coinbase.setSpent(new BigDecimal("0.0")); // 0 as there are no inputs
        coinbase.setMsg("The first and only transaction within the genesis block...");
        coinbase.calculateHash(); // calculates and sets transactionId

        if (!saveTransaction(coinbase))
            throw new MyCustomException("unable to save coinbase transaction to DataBase...");

        return coinbase;
    }

    public List<UTXODto> selectivelyFetchUTXOs(String amount, String algorithm, String walletName) throws MyCustomException {
        // fetching Wallet Info for given wallet name
        String info = rocksDB.find(walletName, "Wallets");
        if (Strings.isEmpty(info))
            throw new MyCustomException(String.format("No wallet found with the name: %s", walletName));

        WalletInfoDto walletInfo;
        try {
            walletInfo = new ObjectMapper().readValue(info, WalletInfoDto.class);
        } catch (JsonProcessingException e) {
            log.error("Error while parsing wallet info in DB to <WalletInfoDto>");
            e.printStackTrace();
            throw new MyCustomException("Error while parsing wallet info in DB to <WalletInfoDto>");
        }

        // transaction data for given wallet
        info = rocksDB.find(walletInfo.getAddress(), "Accounts");
        if (Strings.isEmpty(info))
            throw new MyCustomException(String.format("No transaction data found in wallet: %s", walletName));
        String[] transactions = info.split(" ");

        List<UTXODto> allUTXOs;
        try {
            allUTXOs = retrieveAllUTXOs(transactions);
        } catch(JsonProcessingException e) {
            log.error("Error while parsing transaction info in DB to <Transaction.class>");
            e.printStackTrace();
            throw new MyCustomException("Error while parsing transaction info in DB to <Transaction.class>");
        }

        // checking for allowed algorithms
        if (!ALLOWED_ALGORITHMS.contains(algorithm))
            throw new MyCustomException(String.format("Algorithm %s not present in ALLOWED_LIST of algorithms...", algorithm));

        // checking for if the wallet contains enough balance to transact the given amount
        BigDecimal total = new BigDecimal(0);
        for (UTXODto utxo: allUTXOs)
            total.add(utxo.getAmount());
        if (total.compareTo(new BigDecimal(amount)) < 0)
            throw new MyCustomException(String.format("Not enough balance to make up an amount >= %s; current balance: %s", amount, total));
        if (allUTXOs.size() == 1)
            return allUTXOs;

        List<UTXODto> filteredUTXOs = null;
        switch (algorithm) {
            case OPTIMIZED -> {
                filteredUTXOs = UTXOFilterAlgorithms.meetInTheMiddleSelectionAlgorithm(allUTXOs, new BigDecimal(amount));
            }
            case HIGHEST_SORTED -> {
                filteredUTXOs = UTXOFilterAlgorithms.selectUTXOsInSortedOrder(allUTXOs, new BigDecimal(amount), Boolean.TRUE);
            }
            case LOWEST_SORTED -> {
                filteredUTXOs = UTXOFilterAlgorithms.selectUTXOsInSortedOrder(allUTXOs, new BigDecimal(amount), Boolean.FALSE);
            }
            case RANDOM -> {

            }
        }

        return filteredUTXOs;
    }
}
