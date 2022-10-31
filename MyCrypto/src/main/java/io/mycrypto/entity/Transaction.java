package io.mycrypto.entity;

import io.mycrypto.util.Utility;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Objects;

@Slf4j
@Data
public class Transaction {
    String transactionId; // it's hash
    long timeStamp;
    String from; // from Wallet address
    String to; // to Wallet address
    BigDecimal amount; // amount to transfer
    // FACT(Bitcoin): The satoshi is currently the smallest unit of the bitcoin currency recorded on the blockchain. It is a one hundred millionth of a single bitcoin (0.00000001 BTC)
    BigDecimal UTXO; // (Sender's) amount of digital currency remaining after a cryptocurrency transaction is executed (Unspent Transaction Output)
    String msg = "transferring...";
    BigDecimal transactionFee = new BigDecimal("0.0");

    public Transaction() {
        this.timeStamp = new Date().getTime();
    }

    public void calculateHash() {
        if (!ObjectUtils.isEmpty(from) && !ObjectUtils.isEmpty(to) && !ObjectUtils.isEmpty(amount) && !ObjectUtils.isEmpty(UTXO))
            setTransactionId(Utility.getHashSHA384(Objects.requireNonNull(Utility.getHashSHA384(transactionId + timeStamp + from + to + amount + UTXO + msg + transactionFee)))); // using hashing algorithm twice
        else
            log.error("Error occurred while calculating hash of transaction as the required fields in the transaction are either empty or null");
    }
}
