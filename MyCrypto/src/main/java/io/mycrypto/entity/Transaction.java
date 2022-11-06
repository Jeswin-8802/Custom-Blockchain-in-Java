package io.mycrypto.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mycrypto.util.Utility;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;
import java.util.List;
import java.util.Objects;

// https://learnmeabitcoin.com/technical/transaction-data

@Data
@Slf4j
@ToString
public class Transaction {
    @JsonProperty("hash")
    String transactionId; // it's hash
    @JsonProperty("timestamp")
    long timeStamp;
    @JsonProperty("size")
    BigInteger size;
    @JsonProperty("weight")
    BigInteger weight; // (size of transaction without unlocking code) x 4
    @JsonProperty("from-dodo-coin-address")
    String from; // from Wallet address (essentially the owner of the UTXOs)
    @JsonProperty("to-dodo-coin-address")
    String to; // to Wallet address (supports only p2p transactions for the time being)
    @JsonProperty("number-of-inputs")
    long numInputs;
    @JsonProperty("inputs")
    List<Input> inputs;
    @JsonProperty("number-of-outputs")
    long numOutputs;
    @JsonProperty("outputs")
    List<Output> outputs;
    @JsonProperty("utxo")
    BigDecimal UTXO; // (Sender's) amount of digital currency remaining after a cryptocurrency transaction is executed (Unspent Transaction Output)
    @JsonProperty("message")
    String msg = "transferring...";
    @JsonProperty("transaction-fee")
    BigDecimal transactionFee = new BigDecimal("0.0");

    public Transaction(String from, String to) {
        this.timeStamp = new Date().getTime();
        this.from = from;
        this.to = to;
    }

    public Transaction() {
        this.timeStamp = new Date().getTime();
    }

    public void calculateHash() {
        this.transactionId = Utility.getHashSHA384(Objects.requireNonNull(Utility.getHashSHA384(this.timeStamp + this.from + this.to + this.numInputs + this.inputs + this.outputs + this.UTXO + this.transactionFee)));
    }
}
