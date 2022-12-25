package io.mycrypto.service.transaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.mycrypto.dto.MakeTransactionDto;
import io.mycrypto.dto.UTXODto;
import io.mycrypto.dto.WalletInfoDto;
import io.mycrypto.entity.Input;
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
import org.springframework.util.ObjectUtils;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.*;

@Slf4j
@Service
public class TransactionService {
    private static final String BLOCK_REWARD = "13.0";
    private static final BigDecimal NOMINAL_TRANSACTION_FEE = new BigDecimal("0.0001");
    // refer : https://www.baeldung.com/cs/subset-of-numbers-closest-to-target
    private static final String OPTIMIZED = "OPTIMIZED"; // Meet In the Middle Approach; Selects the UTXOs whose sum closest represents a target amount

    // AVAILABLE ALGORITHMS --------------------------------------
    private static final String HIGHEST_SORTED = "HIGHEST_SORTED"; // trivial
    private static final String LOWEST_SORTED = "LOWEST_SORTED"; // trivial
    private static final String RANDOM = "RANDOM"; // trivial
    private static final List<String> ALLOWED_ALGORITHMS;

    static {
        ALLOWED_ALGORITHMS = List.of(OPTIMIZED, HIGHEST_SORTED, LOWEST_SORTED, RANDOM);
    }

    @Autowired
    KeyValueRepository<String, String> rocksDB;

    // -----------------------------------------------------------

    /**
     * Fetches transaction by its ID
     *
     * @param id Transaction ID
     * @return Transaction Information in JSONObject format
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
     * Creates a coinbase transaction
     *
     * @param info Holds Wallet Information
     * @return Transaction Object containing information about the coinbase transaction
     */
    public Transaction constructCoinbaseTransaction(WalletInfoDto info) throws MyCustomException {
        Transaction coinbase = new Transaction("", info.getAddress());
        coinbase.setNumInputs(0);
        coinbase.setInputs(new ArrayList<>());
        coinbase.setNumOutputs(1);

        // construct output

        // construct Script Public Key (Locking Script)
        ScriptPublicKey script = new ScriptPublicKey(info.getHash160(), info.getAddress());

        Output output = new Output();
        output.setAmount(new BigDecimal(BLOCK_REWARD));
        output.setN(0L);
        output.setScriptPubKey(script);

        // set output
        coinbase.setOutputs(List.of(output));
        coinbase.setSpent(new BigDecimal("0.0")); // 0 as there are no inputs
        coinbase.setMsg("The first and only transaction within the genesis block...");
        coinbase.calculateHash(); // calculates and sets transactionId

        saveTransaction(coinbase);

        return coinbase;
    }

    /**
     * @param tx Transaction Object holding all the Transaction Information
     */
    public void saveTransaction(Transaction tx) throws MyCustomException {
        String json;
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

        // save transaction information to AccountsDB if transaction has your wallet address 👇

        // fetch list of Transaction Details in all Wallet
        Map<String, String> info = rocksDB.getList("Accounts");
        if (info == null) {
            log.error("No content found in Accounts DB...");
            throw new MyCustomException("No content found in Accounts DB...");
        }

        for (Output out : tx.getOutputs()) {
            // If the output is mapped to an address that I own
            if (info.containsKey(out.getScriptPubKey().getAddress()))
                addTransactionToAccounts(out.getScriptPubKey().getAddress(), tx.getTransactionId(), out.getN());
        }
    }

    /**
     * Adds transaction information to associated accounts into DB to keep track of UTXOs
     *
     * @param address Wallet Address
     * @param txId    Transaction ID
     * @param vout    The VOUT value for the Output in a Transaction
     */
    void addTransactionToAccounts(String address, String txId, Long vout) throws MyCustomException {
        String existingTransactions = rocksDB.find(address, "Accounts");

        JSONObject transactions;
        if (!existingTransactions.equals("EMPTY")) {
            try {
                transactions = new ObjectMapper().readValue(existingTransactions, JSONObject.class);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                throw new MyCustomException("Error while casting transaction UTXO data from Accounts DB to JSON Object...");
            }
        } else {
            transactions = new JSONObject();
        }
        transactions.put(txId, vout.toString());
        rocksDB.save(address, transactions.toJSONString(), "Accounts");
    }


    /**
     * Retrieves all UTXOs linked to a WaLLet
     *
     * @param transactions A list of all (transactionId, VOUT) for a given wallet
     * @return A list of UTXOs liked to a given Wallet
     */
    public List<UTXODto> retrieveAllUTXOs(JSONObject transactions) throws JsonProcessingException {
        List<UTXODto> result = new ArrayList<>();
        for (Object s : transactions.keySet()) {
            String transaction = rocksDB.find((String) s, "Transactions");
            if (transaction == null) {
                log.error("Could not find transaction {} obtained from Account DB in Transactions DB", s);
                return null;
            }

            List<Output> outputs = new ObjectMapper().readValue(transaction, Transaction.class).getOutputs();
            BigDecimal amount = null;

            // getting the vout
            for (Output output : outputs) {
                if (output.getN() == Long.parseLong((String) transactions.get(s))) {
                    amount = output.getAmount();
                    break;
                }
            }

            result.add(UTXODto.builder()
                    .transactionId((String) s)
                    .vout(Long.parseLong((String) transactions.get(s)))
                    .amount(amount)
                    .build()
            );
        }
        return result;
    }

    /**
     * Performs Transaction
     *
     * @param requestDto Holds all the information necessary for creating a transactiob
     * @return Transaction object containing all the transaction information of the current transaction
     */
    public Transaction makeTransaction(MakeTransactionDto requestDto) throws MyCustomException {
        WalletInfoDto fromInfo;
        try {
            fromInfo = new ObjectMapper().readValue(requestDto.getFrom(), WalletInfoDto.class);
        } catch (JsonProcessingException e) {
            log.error("Error while parsing contents of wallet to WalletInfoDto.class...");
            throw new MyCustomException("Error while parsing contents of wallet to WalletInfoDto.class...");
        }

        Transaction transaction = new Transaction(fromInfo.getAddress(), requestDto.getTo());
        List<UTXODto> utxos = selectivelyFetchUTXOs(requestDto.getAmount(), requestDto.getAlgorithm(), fromInfo.getAddress(), ObjectUtils.isEmpty(requestDto.getTransactionFee()) ? NOMINAL_TRANSACTION_FEE : requestDto.getTransactionFee());
        // Fetching UTXO information from AccountsDB
        JSONObject transactionJSON;
        try {
            transactionJSON = new ObjectMapper().readValue(rocksDB.find(fromInfo.getAddress(), "Accounts"), JSONObject.class);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            throw new MyCustomException("Parse Error...");
        }

        transaction.setNumInputs(utxos.size());
        List<Input> inputs = new ArrayList<>();
        for (UTXODto utxoDto : utxos) {
            // construct Script Sig
            String signature;
            try {
                signature = Utility.getSignedData(fromInfo.getPrivateKey(), "abcd");
            } catch (NoSuchAlgorithmException | SignatureException | InvalidKeyException |
                     UnsupportedEncodingException e) {
                e.printStackTrace();
                throw new MyCustomException("Error Occurred while getting data signed...");
            }
            log.info("Signature ==> {}", signature);

            String scriptSig = signature + " " + fromInfo.getPublicKey();

            Input input = new Input();
            input.setTransactionId(utxoDto.getTransactionId());
            input.setVout(utxoDto.getVout());
            input.setScriptSig(scriptSig);
            input.setSize((long) scriptSig.getBytes().length);

            // removing UTXO used for transaction from wallet
            transactionJSON.remove(utxoDto.getTransactionId());

            inputs.add(input);
        }
        // saving updated transaction UTXO information in Accounts DB
        if (transactionJSON.isEmpty())
            rocksDB.save(fromInfo.getAddress(), "EMPTY", "Accounts");
        else
            rocksDB.save(fromInfo.getAddress(), transactionJSON.toJSONString(), "Accounts");
        transaction.setInputs(inputs);

        // total UTXO available for transaction
        BigDecimal total = new BigDecimal(0);
        for (UTXODto utxoDto : utxos)
            total = total.add(utxoDto.getAmount());

        // construct outputs
        // There are 2 outputs; 1 being sent to receiver and the balance that gets credited

        List<Output> outputs = new ArrayList<>();
        // output 1
        Output output1 = new Output();
        output1.setScriptPubKey(new ScriptPublicKey(Utility.getHash160FromAddress(requestDto.getTo()), requestDto.getTo()));
        output1.setAmount(requestDto.getAmount());
        output1.setN(0L);
        outputs.add(output1);
        // output 2
        Output output2 = new Output();
        output2.setScriptPubKey(new ScriptPublicKey(fromInfo.getHash160(), fromInfo.getAddress()));
        output2.setAmount(total.subtract(requestDto.getAmount()).subtract(ObjectUtils.isEmpty(requestDto.getTransactionFee()) ? NOMINAL_TRANSACTION_FEE : requestDto.getTransactionFee()));    // total - amount - transactionFee
        output2.setN(1L);
        outputs.add(output2);

        transaction.setOutputs(outputs);
        transaction.setNumOutputs(2);
        transaction.setSpent(total);
        transaction.setTransactionFee(ObjectUtils.isEmpty(requestDto.getTransactionFee()) ? NOMINAL_TRANSACTION_FEE : requestDto.getTransactionFee());
        transaction.setMsg(Strings.isEmpty(requestDto.getMessage()) ? String.format("Transafering %s from %s to %s ...", requestDto.getAmount(), fromInfo.getAddress(), requestDto.getTo()) : requestDto.getMessage());
        transaction.calculateHash();

        saveTransaction(transaction);

        return transaction;
    }

    /**
     * Selectively Fetches UTXOs from a wallet for a transaction based on a specific predetermined algorithm
     *
     * @param amount         Amount to transact
     * @param algorithm      The algorithm used to select suitable UTXOs for the transaction;
     *                       <i>Available options:   1 = <b>OPTIMAL</b>;
     *                       2 = <b>HIGHEST_SORTED</b>;
     *                       3 = <b>LOWEST_SORTED</b>;
     *                       4 = <b>RANDOM</b></i>
     * @param walletAddress  Points to the Wallet from which the UTXOs are to be selected
     * @param transactionFee The transaction fee that will be charged for the transaction;
     *                       This will be added to the amount when taking into consideration the selection of UTXOs for the transaction
     * @return List of UTXOs selected for a particular transaction
     */
    public List<UTXODto> selectivelyFetchUTXOs(BigDecimal amount, Integer algorithm, String walletAddress, BigDecimal transactionFee) throws MyCustomException {
        if (amount.equals(new BigDecimal(0)))
            throw new MyCustomException("Amount to start a transaction must be greater than 0");

        // transaction data for given wallet
        String transactions = rocksDB.find(walletAddress, "Accounts");
        if (transactions.equals("EMPTY"))
            throw new MyCustomException(String.format("No transaction data found for wallet with address: %s", walletAddress));

        List<UTXODto> allUTXOs;
        try {
            allUTXOs = retrieveAllUTXOs(new ObjectMapper().readValue(transactions, JSONObject.class));
        } catch (JsonProcessingException e) {
            log.error("Error while parsing transaction info in DB to <Transaction.class> OR utxo info to JSON");
            e.printStackTrace();
            throw new MyCustomException("Error while parsing transaction info in DB to <Transaction.class> OR utxo info to JSON");
        }

        String alg;
        switch (algorithm) {
            case 1 -> alg = OPTIMIZED;
            case 2 -> alg = HIGHEST_SORTED;
            case 3 -> alg = LOWEST_SORTED;
            case 4 -> alg = RANDOM;
            default -> throw new MyCustomException("Invalid algorithm type passed");
        }

        // checking for allowed algorithms
        if (!ALLOWED_ALGORITHMS.contains(alg))
            throw new MyCustomException(String.format("Algorithm %s not present in ALLOWED_LIST of algorithms...", alg));

        // checking for if the wallet contains enough balance to transact the given amount
        BigDecimal total = new BigDecimal(0);
        for (UTXODto utxo : allUTXOs)
            total = total.add(utxo.getAmount());
        // adding transaction fee
        total = total.add(transactionFee);
        if (total.compareTo(amount) < 0)
            throw new MyCustomException(String.format("Not enough balance to make up an amount (may or may not include transaction fee) >= %s; current balance: %s", amount, total));
        if (allUTXOs.size() == 1)
            return allUTXOs;

        List<UTXODto> filteredUTXOs = null;
        switch (Objects.requireNonNull(alg)) {
            case OPTIMIZED ->
                    filteredUTXOs = UTXOFilterAlgorithms.meetInTheMiddleSelectionAlgorithm(allUTXOs, amount.add(transactionFee));
            case HIGHEST_SORTED ->
                    filteredUTXOs = UTXOFilterAlgorithms.selectUTXOsInSortedOrder(allUTXOs, amount.add(transactionFee), Boolean.TRUE);
            case LOWEST_SORTED ->
                    filteredUTXOs = UTXOFilterAlgorithms.selectUTXOsInSortedOrder(allUTXOs, amount.add(transactionFee), Boolean.FALSE);
            case RANDOM ->
                    filteredUTXOs = UTXOFilterAlgorithms.selectRandomizedUTXOs(allUTXOs, amount.add(transactionFee));
        }

        return filteredUTXOs;
    }
}
