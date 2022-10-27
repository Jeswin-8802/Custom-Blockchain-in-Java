package io.mycrypto.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.mycrypto.entity.Block;
import io.mycrypto.repository.KeyValueRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;

@Slf4j
public class Utility {

    private static final String BLOCKCHAIN_STORAGE_PATH = "C:\\REPO\\Github\\blockchain";

    private static final String GENESIS_BLOCK = "blk000000000001.dat";

    // sha-384 hashing function
    public static String getHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            //Applies sha256 to our input,
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            StringBuffer hexString = new StringBuffer(); // This will contain hash as hexidecimal
            for (int i = 0; i < hash.length; i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if(hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();

        }
        catch(Exception ex) {
            log.error("Error occurred while creating hash of >> {} << \nexception: {}, message: {}, stackTrace: {}", input, ex.getCause(), ex.getMessage(), ex.getStackTrace());
        }
//        try {
//            // getInstance() method is called with algorithm SHA-384
//            MessageDigest md = MessageDigest.getInstance("SHA-256");
//
//            // digest() method is called
//            // to calculate message digest of the input string
//            // returned as array of byte
//            byte[] messageDigest = md.digest(input.getBytes());
//
//            // Convert byte array into signum representation
//            BigInteger no = new BigInteger(1, messageDigest);
//
//            // Convert message digest into hex value
//            String hashtext = no.toString(16);
//
//            // Add preceding 0s to make it 32 bit
//            while (hashtext.length() < 32) {
//                hashtext = "0" + hashtext;
//            }
//
//            // return the HashText
//            return hashtext;
//        }
//
//        // For specifying wrong message digest algorithms
//        catch (NoSuchAlgorithmException ex) {
//            log.error("Error occurred while creating hash of >> {} << \nexception: {}, message: {}, stackTrace: {}", input, ex.getCause(), ex.getMessage(), ex.getStackTrace());
//        }
        return null;
    }

    public static void createGenesisBlock(KeyValueRepository db) {
        try {
            DataOutputStream out = new DataOutputStream(new FileOutputStream(BLOCKCHAIN_STORAGE_PATH + "\\" + GENESIS_BLOCK));
            Block genesis = new Block();
            genesis.setPreviousHash(getHash("0"));
            genesis.setHeight(0);
            genesis.setTimeStamp(new Date().getTime());
            genesis.setTx(new ArrayList<>());
            genesis.setNumTx(0);
            genesis.setMerkleRoot(getHash(getHash("0") + getHash("0")));
            genesis.setNonce(1);
            genesis.setDifficulty(1);
            log.info("Hash of genesis block ==> {}", genesis.calculateHash());
            log.info("mining the genesis block...");
            genesis.mineBlock(db);

            ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
            String json = ow.writeValueAsString(genesis);

            log.info("{} ==> \n{}", GENESIS_BLOCK, json);
            db.save(genesis.getHash(), BLOCKCHAIN_STORAGE_PATH + "\\" + GENESIS_BLOCK, "Blockchain");
            out.close();
        } catch (FileNotFoundException ex) {
            log.error("Error occurred while creating {} at location {} \nexception: {}, message: {}, stackTrace: {}", GENESIS_BLOCK, BLOCKCHAIN_STORAGE_PATH, ex.getCause(), ex.getMessage(), ex.getStackTrace());
        } catch (JsonProcessingException ex) {
            log.error("Error occurred while parsing Object(Block) to json \nexception: {}, message: {}, stackTrace: {}", ex.getCause(), ex.getMessage(), ex.getStackTrace());
        } catch (IOException ex) {
            log.error("Error occurred while creating DataOutputStream \nexception: {}, message: {}, stackTrace: {}", ex.getCause(), ex.getMessage(), ex.getStackTrace());
        }
    }
}
