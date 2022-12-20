package io.mycrypto.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mycrypto.dto.WalletInfoDto;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.util.Strings;
import org.bitcoinj.core.Base58;
import org.bitcoinj.core.Utils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;


@Slf4j
public final class Utility {
    private static final String PUBLIC_KEY_NAME = "myPublicKey";
    private static final String PRIVATE_KEY_NAME = "myPrivateKey";
    private static final String OUTER_RESOURCE_FOLDER = "RESOURCES";
    private static final String FOLDER_TO_STORE_KEYS = "KEYS";

    private static final String LOCATION_TO_STORE_KEY;

    private static final byte EVEN = 0x02;
    private static final byte ODD = 0x03;

    static {
        LOCATION_TO_STORE_KEY = SystemUtils.USER_DIR + osAppender() + OUTER_RESOURCE_FOLDER + osAppender() + FOLDER_TO_STORE_KEYS + osAppender();
    }

    public static String osAppender() {
        return SystemUtils.IS_OS_LINUX || SystemUtils.IS_OS_MAC ? "/" : "\\";
    }

    // sha-384 hashing function
    public static String getHashSHA384(String input) {
        try {
            return bytesToHex(MessageDigest.getInstance("SHA-384").digest(input.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    // sha-256 hashing function
    public static byte[] getHashSHA256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String getHashSHA256(String input) {
        try {
            return bytesToHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }



    /*---- Private functions ----*/

    public static WalletInfoDto generateKeyPairToFile(String keyName) throws FileNotFoundException {

        File base = new File(LOCATION_TO_STORE_KEY);
        if (base.isDirectory())
            log.info("The directory \"KEYS\" found... \nAdding keys to folder");
        else {
            if (base.mkdir())
                log.info("directory \"KEYS\" created... \nAdding keys to folder");
            else {
                log.error("Unable to create dir \"KEYS\"");
                throw new FileNotFoundException("Unable to create dir \"KEYS\"");
            }
        }

        Security.addProvider(new BouncyCastleProvider());
        try (FileOutputStream pubKey = new FileOutputStream(LOCATION_TO_STORE_KEY + (Strings.isNotEmpty(keyName) ? keyName + "_pub" : PUBLIC_KEY_NAME) + ".pem");
             FileOutputStream priKey = new FileOutputStream(LOCATION_TO_STORE_KEY + (Strings.isNotEmpty(keyName) ? keyName + "_prv" : PRIVATE_KEY_NAME) + ".pem")) {

            KeyPairGenerator generator = KeyPairGenerator.getInstance("ECDSA", "BC");
            ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256k1");
            generator.initialize(ecSpec);
            KeyPair pair = generator.generateKeyPair();

            PrivateKey privateKey = pair.getPrivate();
            PublicKey publicKey = pair.getPublic();

            log.info("generating " + PRIVATE_KEY_NAME + ".pem...........\n");

            String privateKeyString = "-----BEGIN PRIVATE KEY-----" + "\n" +
                    Base64.getMimeEncoder().encodeToString(privateKey.getEncoded()) + "\n" +
                    "-----END PRIVATE KEY-----";

            log.info(PRIVATE_KEY_NAME + ".pem ==> \n" + privateKeyString + "\n");

            log.info("generating " + PUBLIC_KEY_NAME + ".pem...........\n");

            String publicKeyString = "-----BEGIN PUBLIC KEY-----" + "\n" +
                    Base64.getMimeEncoder().encodeToString(publicKey.getEncoded()) + "\n" +
                    "-----END PUBLIC KEY-----";

            log.info(PUBLIC_KEY_NAME + ".pem ==> \n" + publicKeyString + "\n");

            priKey.write(privateKeyString.getBytes(StandardCharsets.UTF_8));
            pubKey.write(publicKeyString.getBytes(StandardCharsets.UTF_8));

            ECPublicKey epub = (ECPublicKey) publicKey;
            ECPoint pt = epub.getW();

            byte[] pubBytes = compressPublicKey(pt);

            log.info("Compressed Public Key: {}", bytesToHex(pubBytes));

            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] s1 = sha.digest(pubBytes);

            log.info("SHA256(public-key): {}", bytesToHex(s1));

            byte[] ripeMD = hash160(pubBytes);
            //adds 0x00
            byte[] ripeMDPadded = new byte[ripeMD.length + 1];
            ripeMDPadded[0] = 0;
            System.arraycopy(ripeMD, 0, ripeMDPadded, 1, ripeMD.length);

            log.info("RIPEMD160 ==> 00 + HASH160(public-key): {}, length: {}", bytesToHex(ripeMDPadded), ripeMDPadded.length);


            byte[] checksum = sha.digest(sha.digest(ripeMD));
            log.info("checksum: {}", bytesToHex(checksum));

            //add check sum
            byte[] sumBytes = new byte[ripeMDPadded.length + 4];
            System.arraycopy(ripeMDPadded, 0, sumBytes, 0, ripeMDPadded.length);
            System.arraycopy(checksum, 0, sumBytes, ripeMDPadded.length, 4);

            log.info("Wallet Address: {}", Base58.encode(sumBytes));

            WalletInfoDto data = new WalletInfoDto();
            data.setPublicKey(bytesToHex(publicKey.getEncoded()));
            data.setPrivateKey(bytesToHex(privateKey.getEncoded()));
            data.setHash160(bytesToHex(ripeMD));
            data.setAddress(Base58.encode(sumBytes));

            return data;
        } catch (IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidAlgorithmParameterException | NoSuchProviderException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public static String bytesToHex(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for (byte b : a)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }

    public static byte[] hexToBytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int index = 0; index < hex.length(); index += 2)
            bytes[index / 2] = (byte) Integer.parseInt(hex.substring(index, index + 2), 16);
        return bytes;
    }

    public static byte[] hash160(byte[] input) {
        return Utils.sha256hash160(input);
    }

    public static byte[] compressPublicKey(ECPoint pt) {
        byte[] y = pt.getAffineY().toByteArray();
        byte[] x = pt.getAffineX().toByteArray();
        byte[] pk = new byte[x.length + 1];
        System.arraycopy(x, 0, pk, 1, x.length);
        if (y[y.length - 1] % 2 == 1)
            pk[0] = ODD;
        else
            pk[0] = EVEN;
        return pk;
    }

    public static String beautify(String json) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(json));
        } catch (JsonProcessingException e) {
            log.error("Error while trying to beautify JSON string");
            e.printStackTrace();
        }
        return null;
    }

    public static String constructMerkleTree(List<String> transactionIds) {
        int size = transactionIds.size();
        if (size % 2 == 1) {
            transactionIds.add(transactionIds.get(size - 1));
            size++;
        }
        while (size > 1) {
            int pos = 0;
            for (int i = 0; i < size; i += 2) {
                transactionIds.set(pos, getHashSHA384(transactionIds.get(i) + transactionIds.get(i + 1)));
                pos++;
            }
            if (size % 2 == 1) {
                transactionIds.set(pos + 1, transactionIds.get(pos));
                size++;
            }
            size /= 2;
        }
        return transactionIds.get(0);
    }

    public static JSONObject constructJsonResponse(String key, String message) {
        try {
            return (JSONObject) new JSONParser().parse(String.format("""
                    {
                        "%s": "%s"
                    }
                    """, key, message));
        } catch (ParseException ignore) {
            // ignore
        }
        return null;
    }

    public static List<String> listFilesInDirectory(String path) {
        FilenameFilter filter = (f, name) -> name.endsWith(".dat");
        File f = new File(path);
        if (f.exists() && f.isDirectory())
            return new ArrayList<>(List.of(Objects.requireNonNull(f.list(filter))));
        return new ArrayList<>(List.of("INVALID DIRECTORY"));
    }
}
