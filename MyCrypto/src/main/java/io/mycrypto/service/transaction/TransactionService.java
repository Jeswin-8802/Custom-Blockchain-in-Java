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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.*;

@PropertySource("classpath:config.properties")
@Slf4j
@Service
public class TransactionService {
    @Value("${BLOCK_REWARD}")
    private String BLOCK_REWARD;

    private static final BigDecimal DEFAULT_TRANSACTION_FEE = new BigDecimal("0.0001");
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
    // -----------------------------------------------------------

    @Value("${TRANSACTIONS_COUNT_LOWER_LIMIT}")
    private Integer TRANSACTIONS_COUNT_LOWER_LIMIT;

    @Value("${TRANSACTIONS_COUNT_UPPER_LIMIT}")
    private Integer TRANSACTIONS_COUNT_UPPER_LIMIT;

    @Autowired
    KeyValueRepository<String, String> rocksDB;

    // -----------------------------------------------------------

    /**
     * Fetches transaction by its ID
     *
     * @param id                      Transaction ID
     * @param searchInTransactionPool
     * @return Transaction Information in JSONObject format
     */
    public JSONObject fetchTransaction(String id, Boolean searchInTransactionPool) throws NullPointerException {
        String json = rocksDB.find(id, searchInTransactionPool ? "Transactions-Pool" : "Transactions");
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
     * Performs Transaction
     *
     * @param requestDto Holds all the information necessary for creating a transaction
     * @return Transaction object containing all the transaction information of the current transaction
     */
    public Transaction makeTransaction(MakeTransactionDto requestDto) throws MyCustomException {
        String methodName = "makeTransaction(MakeTransactionDto)";
        WalletInfoDto fromInfo;
        try {
            fromInfo = new ObjectMapper().readValue(requestDto.getFrom(), WalletInfoDto.class);
        } catch (JsonProcessingException e) {
            log.error("Error while parsing contents of wallet to WalletInfoDto.class...");
            throw new MyCustomException("Error while parsing contents of wallet to WalletInfoDto.class...");
        }

        Transaction transaction = new Transaction(fromInfo.getAddress(), requestDto.getTo());

        Boolean processCurrencyInjectionTransaction = Boolean.FALSE;

        // check for if currency injection can be performed
        try (InputStream input = new FileInputStream("C:\\Users\\WHNP83\\Documents\\GitHub\\Dodo-coin\\MyCrypto\\src\\main\\resources\\config.properties")) {
            Properties prop = new Properties();
            // load the properties file
            prop.load(input);
            if (fromInfo.getAddress().equals(prop.getProperty("ADMIN_ADDRESS")) && Integer.parseInt(prop.getProperty("INJECT_CURRENCY_IF_ADMIN")) == 1)
                processCurrencyInjectionTransaction = Boolean.TRUE;
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        List<Input> inputs = new ArrayList<>();
        List<Output> outputs = new ArrayList<>();
        BigDecimal total;

        if (!processCurrencyInjectionTransaction) {
            List<UTXODto> utxos = selectivelyFetchUTXOs(requestDto.getAmount(), requestDto.getAlgorithm(), fromInfo.getAddress(), ObjectUtils.isEmpty(requestDto.getTransactionFee()) ? DEFAULT_TRANSACTION_FEE : requestDto.getTransactionFee());

            // Fetching UTXO information from AccountsDB
            // Needed for tracking the UTXO associated with an account; The UTXO used will be removed and the updated json will be put back into the AccountsDB
            JSONObject transactionJSON;
            try {
                transactionJSON = new ObjectMapper().readValue(rocksDB.find(fromInfo.getAddress(), "Accounts"), JSONObject.class);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                throw new MyCustomException("Parse Error...");
            }

            transaction.setNumInputs(utxos.size());

            for (UTXODto utxoDto : utxos) {
                Input input = new Input();
                input.setTransactionId(utxoDto.getTransactionId());
                input.setVout(utxoDto.getVout());
                String scriptSig = constructScriptSig(fromInfo, "abcd");
                input.setScriptSig(scriptSig);
                input.setSize((long) scriptSig.getBytes().length);

                // removing UTXO used for transaction from wallet
                transactionJSON.remove(utxoDto.getTransactionId());

                inputs.add(input);
            }
            log.info("{}::----------------------- {}", methodName, transactionJSON);
            // saving updated transaction UTXO information in Accounts DB
            if (transactionJSON.isEmpty())
                rocksDB.save(fromInfo.getAddress(), "EMPTY", "Accounts");
            else
                rocksDB.save(fromInfo.getAddress(), transactionJSON.toJSONString(), "Accounts");
            transaction.setInputs(inputs);

            total = new BigDecimal(0);
            // total UTXO available for transaction
            for (UTXODto utxoDto : utxos)
                total = total.add(utxoDto.getAmount());

            log.info("{}::Total ------------------------> {}", methodName, total);

            // construct outputs
            // There are 2 outputs; 1 being sent to receiver and the balance that gets credited

            // output 1
            Output output1 = new Output();
            output1.setScriptPubKey(new ScriptPublicKey(Utility.getHash160FromAddress(requestDto.getTo()), requestDto.getTo()));
            output1.setAmount(requestDto.getAmount());
            output1.setN(0L);
            outputs.add(output1);
            // output 2
            Output output2 = new Output();
            output2.setScriptPubKey(new ScriptPublicKey(fromInfo.getHash160(), fromInfo.getAddress()));
            output2.setAmount(total.subtract(requestDto.getAmount()).subtract(ObjectUtils.isEmpty(requestDto.getTransactionFee()) ? DEFAULT_TRANSACTION_FEE : requestDto.getTransactionFee()));    // total - amount - transactionFee
            output2.setN(1L);
            outputs.add(output2);

            transaction.setNumOutputs(2);

        } else {
            transaction.setNumInputs(0L);

            Input input = new Input();
            input.setTransactionId("");
            input.setVout(-1L);
            String scriptSig = constructScriptSig(fromInfo, "abcd");
            input.setScriptSig(scriptSig);
            input.setSize((long) scriptSig.getBytes().length);

            inputs.add(input);

            transaction.setInputs(inputs);

            total = requestDto.getAmount();

            Output newCurrency = new Output();
            newCurrency.setScriptPubKey(new ScriptPublicKey(Utility.getHash160FromAddress(requestDto.getTo()), requestDto.getTo()));
            newCurrency.setAmount(total.subtract(DEFAULT_TRANSACTION_FEE));
            newCurrency.setN(0L);

            outputs.add(newCurrency);

            transaction.setNumOutputs(1);
        }

        transaction.setOutputs(outputs);
        transaction.setSpent(total);
        transaction.setTransactionFee(ObjectUtils.isEmpty(requestDto.getTransactionFee()) ? DEFAULT_TRANSACTION_FEE : requestDto.getTransactionFee());
        transaction.setMsg(Strings.isEmpty(requestDto.getMessage()) ? String.format("Transferring %s from %s to %s ...", requestDto.getAmount(), fromInfo.getAddress(), requestDto.getTo()) : requestDto.getMessage());
        transaction.calculateHash();

        saveTransaction(transaction, "Transactions-Pool");

        // Note that the transaction will not be added to the Accounts DB until it gets mined

        return transaction;
    }

    private String constructScriptSig(WalletInfoDto fromInfo, String dataToSign) throws MyCustomException {
        String methodName = "constructScriptSig(WalletInfoDto, String)";
        String signature;
        try {
            signature = Utility.getSignedData(fromInfo.getPrivateKey(), "abcd");
        } catch (NoSuchAlgorithmException | SignatureException | InvalidKeyException |
                 UnsupportedEncodingException e) {
            e.printStackTrace();
            throw new MyCustomException("Error Occurred while getting data signed...");
        }
        log.info("{}::Signature ==> {}", methodName, signature);

        return signature + " " + fromInfo.getPublicKey();
    }

    public Transaction constructCoinbaseTransaction(WalletInfoDto info, List<Transaction> transactionsList) throws MyCustomException {
        Transaction coinbase = new Transaction("", info.getAddress());
        coinbase.setNumInputs(0);
        coinbase.setInputs(new ArrayList<>());
        coinbase.setNumOutputs(2);

        // construct outputs

        // construct Script Public Key (Locking Script)
        ScriptPublicKey script = new ScriptPublicKey(info.getHash160(), info.getAddress());
        Output output1 = new Output();
        output1.setAmount(new BigDecimal(BLOCK_REWARD));
        output1.setN(0L);
        output1.setScriptPubKey(script);

        Output output2 = new Output();

        // calculate the transaction fee for all the transactions that will be included in the block
        BigDecimal total = new BigDecimal(0);
        for (Transaction tx: transactionsList)
            total = total.add(tx.getTransactionFee());

        output2.setAmount(total);
        output2.setN(1L);
        output2.setScriptPubKey(script);

        coinbase.setOutputs(List.of(output1, output2));
        coinbase.setSpent(new BigDecimal("0.0")); // 0 as there are no inputs
        coinbase.setMsg("COINBASE...");
        coinbase.calculateHash(); // calculates and sets transactionId

        saveTransaction(coinbase, "Transactions");

        saveTransactionToWalletIfTransactionPointsToWalletOwned(coinbase);

        return coinbase;
    }

    public List<Transaction> retrieveAndDeleteTransactionsFromTransactionsPool() throws MyCustomException {
        // checking for if there exists enough transactions within the Transactions Pool
        if (rocksDB.getCount("Transactions-Pool") < TRANSACTIONS_COUNT_LOWER_LIMIT)
            throw new MyCustomException(String.format("Not enough transactions in the Transactions Pool to mine a Block; Must contain at least %s transactions", TRANSACTIONS_COUNT_LOWER_LIMIT));

        // retrieving transactions from the transaction pool
        Map<String, String> transactionsMap = rocksDB.getList("Transactions-Pool");

        // converting transactions from JSON String to <Transaction.class>
        List<Transaction> transactions = new ArrayList<>();
        for (String transactionHash: transactionsMap.keySet()) {
            try {
                Transaction tx = new ObjectMapper().readValue(transactionsMap.get(transactionHash), Transaction.class);
                transactions.add(tx);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                throw new MyCustomException(String.format("Error while parsing Transaction with id: %s from JSON String to <Transaction.class>", transactionHash));
            }
        }

        // sorting transactions according to transaction fee
        transactions.sort(Comparator.comparing(Transaction::getTransactionFee));

        // Taking atMost N Transactions; N is the Uppermost Limit of transactions in a block
        if (transactions.size() > TRANSACTIONS_COUNT_UPPER_LIMIT) {
            Long N = new Random().nextLong(TRANSACTIONS_COUNT_LOWER_LIMIT, TRANSACTIONS_COUNT_UPPER_LIMIT); // to bring in variability
            for (long i = transactions.size() - 1; i >= N; i--)
                transactions.remove((int) i);
        }

        // fetch list of Transaction Details in all Wallet
        // to be used below
        Map<String, String> info = rocksDB.getList("Accounts");
        if (info == null) {
            log.error("No content found in Accounts DB...");
            throw new MyCustomException("No content found in Accounts DB...");
        }

        // removing the transactions from the Transactions-PoolDB and adding to TransactionsDB
        for (Transaction tx: transactions) {
            rocksDB.delete(tx.getTransactionId(), "Transactions-Pool");

            // save transaction information to AccountsDB if transaction has your wallet address

            for (Output out : tx.getOutputs()) {
                // If the output is mapped to an address that is owned
                if (info.containsKey(out.getScriptPubKey().getAddress()))
                    addTransactionToAccounts(out.getScriptPubKey().getAddress(), tx.getTransactionId(), out.getN());
            }

            saveTransaction(tx, "Transactions");
        }

        return transactions;
    }

    /**
     * Creates a coinbase transaction
     *
     * @param info Holds Wallet Information
     * @return Transaction Object containing information about the coinbase transaction
     */
    public Transaction constructCoinbaseTransactionForGenesisBlock(WalletInfoDto info) throws MyCustomException {
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

        saveTransaction(coinbase, "Transactions");

        // save transaction information to AccountsDB if transaction has your wallet address
        saveTransactionToWalletIfTransactionPointsToWalletOwned(coinbase);

        return coinbase;
    }

    /**
     * @param tx Transaction Object holding all the Transaction Information
     */
    public void saveTransaction(Transaction tx, String DB) throws MyCustomException {
        String methodName = "saveTransaction(Transaction, String)";
        String json;
        try {
            ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
            json = ow.writeValueAsString(tx);
            tx.setSize(new BigInteger(String.valueOf(json.replace(" ", "").length() - "\"size\":null\"weight\": null".length())));
            tx.setWeight(new BigInteger("4").multiply(tx.getSize()).subtract(new BigInteger(String.valueOf(tx.getInputs().size()))));
            json = ow.writeValueAsString(tx);
            log.info("{}::{} ==> \n{}", methodName, tx.getTransactionId(), json);
        } catch (JsonProcessingException e) {
            log.error("Error occurred while parsing Object(Transaction) to json");
            e.printStackTrace();
            throw new MyCustomException("Error occurred while parsing Object(Transaction) to json");
        }

        rocksDB.save(tx.getTransactionId(), json, DB);

        // Saving to Transactions DB only when a transaction is present in a block that is mined
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

        if (transactions.containsKey(txId))
            transactions.put(txId, ((String)transactions.get(txId)).concat("," + vout.toString()));
        else
            transactions.put(txId, vout.toString());
        rocksDB.save(address, transactions.toJSONString(), "Accounts");
    }


    /**
     * Retrieves all UTXOs linked to a WaLLet
     *
     * @param transactions A list of all (transactionId, VOUT) for a given wallet
     * @return A list of UTXOs liked to a given Wallet
     */
    public List<UTXODto> retrieveAllUTXOs(JSONObject transactions, String db) throws JsonProcessingException, MyCustomException {
        List<UTXODto> result = new ArrayList<>();
        for (Object txId : transactions.keySet()) {
            String transaction = rocksDB.find((String) txId, db);
            if (transaction == null) {
                log.error("Could not find transaction {} obtained from Account DB in {}} DB", txId, db);
                throw new MyCustomException("Transactions present in wallet not found in Transactions DB...");
            }

            List<Output> outputs = new ObjectMapper().readValue(transaction, Transaction.class).getOutputs();
            BigDecimal amount = null;

            String outN = ((String) transactions.get(txId));

            // getting the vout
            for (Output output : outputs) {
                if (outN.contains(",")) {
                    for (String n: outN.split(",")) {
                        if (output.getN() == Long.parseLong(n)) {
                            amount = output.getAmount();
                            result.add(UTXODto.builder()
                                    .transactionId((String) txId)
                                    .vout(Long.parseLong(n))
                                    .amount(amount)
                                    .build()
                            );
                            break;
                        }
                    }
                } else {
                    if (output.getN() == Long.parseLong(outN)) {
                        amount = output.getAmount();
                        result.add(UTXODto.builder()
                                .transactionId((String) txId)
                                .vout(Long.parseLong(outN))
                                .amount(amount)
                                .build()
                        );
                        break;
                    }
                }
            }
        }
        return result;
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
            allUTXOs = retrieveAllUTXOs(new ObjectMapper().readValue(transactions, JSONObject.class), "Transactions"); // Note that for a UTXO to be used, it must have been mined
        } catch (JsonProcessingException e) {
            log.error("Error while parsing transaction info in DB to <Transaction.class> OR utxo info to JSON");
            e.printStackTrace();
            throw new MyCustomException("Error while parsing transaction info in DB to <Transaction.class> OR utxo info to JSON");
        }
        if (CollectionUtils.isEmpty(allUTXOs))
            throw new MyCustomException("Wallet is empty...");

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
        if (total.compareTo(amount.add(transactionFee)) < 0)
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

    private void saveTransactionToWalletIfTransactionPointsToWalletOwned(Transaction tx) throws MyCustomException {
        // fetch list of Transaction Details in all Wallet
        Map<String, String> utxoInfo = rocksDB.getList("Accounts");
        if (utxoInfo == null) {
            log.error("No content found in Accounts DB...");
            throw new MyCustomException("No content found in Accounts DB...");
        }

        for (Output out : tx.getOutputs()) {
            // If the output is mapped to an address that is owned
            if (utxoInfo.containsKey(out.getScriptPubKey().getAddress()))
                addTransactionToAccounts(out.getScriptPubKey().getAddress(), tx.getTransactionId(), out.getN());
        }
    }
}
