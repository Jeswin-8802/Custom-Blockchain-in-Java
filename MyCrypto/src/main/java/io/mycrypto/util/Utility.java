package io.mycrypto.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.mycrypto.entity.Block;
import io.mycrypto.repository.KeyValueRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

@Slf4j
public class Utility {

    private static final String PUBLIC_KEY_NAME = "myPublicKey";

    private static final String PRIVATE_KEY_NAME = "myPrivateKey";

    private static final String LOCATION_TO_STORE_KEYS = "src/main/resources/";

    private static final String STARTING_AMOUNT = "100"; // only the admin should have the permissions to circulate currency otherwise set to 0 (can be validated by checking UTXO by transaction order


    // sha-384 hashing function
    public static String getHashSHA384(String input) {
        try {
            // getInstance() method is called with algorithm SHA-384
            MessageDigest md = MessageDigest.getInstance("SHA-256");

            // digest() method is called
            // to calculate message digest of the input string
            // returned as array of byte
            byte[] messageDigest = md.digest(input.getBytes());

            // Convert byte array into signum representation
            BigInteger no = new BigInteger(1, messageDigest);

            // Convert message digest into hex value
            String hashtext = no.toString(16);

            // Add preceding 0s to make it 32 bit
            while (hashtext.length() < 32) {
                hashtext = "0" + hashtext;
            }

            // return the HashText
            return hashtext;
        }

        // For specifying wrong message digest algorithms
        catch (NoSuchAlgorithmException ex) {
            log.error("Error occurred while creating hash of >> {} << \nexception: {}, message: {}, stackTrace: {}", input, ex.getCause(), ex.getMessage(), ex.getStackTrace());
        }
        return null;
    }

    // sha-256 hashing function
    public static String getHashSHA256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            //Applies sha256 to our input,
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            StringBuffer hexString = new StringBuffer(); // This will contain hash as hexadecimal
            for (int i = 0; i < hash.length; i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();

        } catch (Exception ex) {
            log.error("Error occurred while creating hash of >> {} << \nexception: {}, message: {}, stackTrace: {}", input, ex.getCause(), ex.getMessage(), ex.getStackTrace());
        }
        return null;
    }

    public static String generateKeyPairToFile() {
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

            priKey.write(privateKeyString.getBytes(StandardCharsets.UTF_8));
            pubKey.write(publicKeyString.getBytes(StandardCharsets.UTF_8));

            return publicKeyString.replace("\n", "") + " " + privateKeyString.replace("\n", "") + " " + STARTING_AMOUNT;
        } catch (IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }
}
