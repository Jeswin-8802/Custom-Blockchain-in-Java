package io.mycrypto.util;

import io.mycrypto.repository.KeyValueRepository;
import io.mycrypto.repository.RocksDBRepositoryImpl;
import lombok.extern.slf4j.Slf4j;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;
import java.util.Scanner;

@Slf4j
public class RSAKeyPairGenerator {

    private static final String PUBLIC_KEY_NAME = "myPublicKey";

    private static final String PRIVATE_KEY_NAME = "myPrivateKey";

    private static final String LOCATION_TO_STORE_KEYS = "src/main/resources/";

    private static final String STARTING_AMOUNT = "100";

    public static void main(String[] args) {
        new RSAKeyPairGenerator().generateKeyPairToFile(PUBLIC_KEY_NAME, PRIVATE_KEY_NAME, null);
    }


    // can store at specific location as well as store in the Wallets DB
    public void generateKeyPairToFile(String publicKeyName, String privateKeyName, KeyValueRepository db) {
        try (FileOutputStream pubKey = new FileOutputStream(LOCATION_TO_STORE_KEYS + publicKeyName + ".pem");
             FileOutputStream priKey = new FileOutputStream(LOCATION_TO_STORE_KEYS + privateKeyName + ".pem")) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();

            PrivateKey privateKey = pair.getPrivate();
            PublicKey publicKey = pair.getPublic();

            log.info("generating " + privateKeyName + ".pem...........\n");

            String privateKeyString = "-----BEGIN PRIVATE KEY-----" +
                    "\n" +
                    Base64.getMimeEncoder().encodeToString(privateKey.getEncoded()) +
                    "\n" +
                    "-----END PRIVATE KEY-----";

            log.info(privateKeyName + ".pem ==> \n" + privateKeyString + "\n");

            log.info("generating " + publicKeyName + ".pem...........\n");

            String publicKeyString = "-----BEGIN PUBLIC KEY-----" +
                    "\n" +
                    Base64.getMimeEncoder().encodeToString(publicKey.getEncoded()) +
                    "\n" +
                    "-----END PUBLIC KEY-----";

            log.info(publicKeyName + ".pem ==> \n" + publicKeyString + "\n");

            String value = publicKeyString + " " + privateKeyString + " " + STARTING_AMOUNT;

            Scanner sc = new Scanner(System.in);
            System.out.print("\n\nPlease Enter the name of the new Wallet: ");
            String name = sc.nextLine();
            sc.close();

            db.save(name, value, "Wallets");

            priKey.write(privateKeyString.getBytes(StandardCharsets.UTF_8));
            pubKey.write(publicKeyString.getBytes(StandardCharsets.UTF_8));
        } catch (IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }
}
