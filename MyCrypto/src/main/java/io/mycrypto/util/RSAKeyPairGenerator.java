package io.mycrypto.util;

import io.mycrypto.repository.KeyValueRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;
import java.util.Scanner;

@Slf4j
@Service
public class RSAKeyPairGenerator {

    @Autowired
    KeyValueRepository<String, String> rocksDB;

    private static final String PUBLIC_KEY_NAME = "myPublicKey";

    private static final String PRIVATE_KEY_NAME = "myPrivateKey";

    private static final String LOCATION_TO_STORE_KEYS = "src/main/resources/";

    private static final String STARTING_AMOUNT = "100"; // only the admin should have the permissions to circulate currency otherwise set to 0 (can be validated by checking UTXO by transaction order)


    // can store at specific location as well as store in the Wallets DB
    public void generateKeyPairToFile(String walletName) {
        try (FileOutputStream pubKey = new FileOutputStream(LOCATION_TO_STORE_KEYS + PUBLIC_KEY_NAME + ".pem");
             FileOutputStream priKey = new FileOutputStream(LOCATION_TO_STORE_KEYS + PRIVATE_KEY_NAME + ".pem")) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);            KeyPair pair = generator.generateKeyPair();

            PrivateKey privateKey = pair.getPrivate();
            PublicKey publicKey = pair.getPublic();

            log.info("generating " + PRIVATE_KEY_NAME + ".pem...........\n");

            String privateKeyString = "-----BEGIN PRIVATE KEY-----" +
                    "\n" +
                    Base64.getMimeEncoder().encodeToString(privateKey.getEncoded()) +
                    "\n" +
                    "-----END PRIVATE KEY-----";

            log.info(PRIVATE_KEY_NAME + ".pem ==> \n" + privateKeyString + "\n");

            log.info("generating " + PUBLIC_KEY_NAME + ".pem...........\n");

            String publicKeyString = "-----BEGIN PUBLIC KEY-----" +
                    "\n" +
                    Base64.getMimeEncoder().encodeToString(publicKey.getEncoded()) +
                    "\n" +
                    "-----END PUBLIC KEY-----";

            log.info(PUBLIC_KEY_NAME + ".pem ==> \n" + publicKeyString + "\n");

            String value = publicKeyString + " " + privateKeyString + " " + STARTING_AMOUNT;

            rocksDB.save(walletName, value, "Wallets");

            priKey.write(privateKeyString.getBytes(StandardCharsets.UTF_8));
            pubKey.write(publicKeyString.getBytes(StandardCharsets.UTF_8));
        } catch (IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }
}
