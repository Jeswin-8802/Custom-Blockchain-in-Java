package io.mycrypto.util;

import lombok.extern.slf4j.Slf4j;

import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

import static java.lang.Integer.rotateLeft;

@Slf4j
public final class Utility {

    public static final char[] ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();
    private static final String PUBLIC_KEY_NAME = "myPublicKey";
    private static final String PRIVATE_KEY_NAME = "myPrivateKey";
    private static final String LOCATION_TO_STORE_KEYS = "src/main/resources/";
    private static final String STARTING_AMOUNT = "100"; // only the admin should have the permissions to circulate currency otherwise set to 0 (can be validated by checking UTXO by transaction order
    private static final String EVEN = "02";
    private static final String ODD = "03";
    private static final char ENCODED_ZERO = ALPHABET[0];
    private static final int[] INDEXES = new int[128];
    private static final int BLOCK_LEN = 64;  // In bytes
    private static final int[] KL = {
            0x00000000, 0x5A827999, 0x6ED9EBA1, 0x8F1BBCDC, 0xA953FD4E
    };  // Round constants for left line
    private static final int[] KR = {
            0x50A28BE6, 0x5C4DD124, 0x6D703EF3, 0x7A6D76E9, 0x00000000
    };  // Round constants for right line
    private static final int[] RL = {  // Message schedule for left line
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
            7, 4, 13, 1, 10, 6, 15, 3, 12, 0, 9, 5, 2, 14, 11, 8,
            3, 10, 14, 4, 9, 15, 8, 1, 2, 7, 0, 6, 13, 11, 5, 12,
            1, 9, 11, 10, 0, 8, 12, 4, 13, 3, 7, 15, 14, 5, 6, 2,
            4, 0, 5, 9, 7, 12, 2, 10, 14, 1, 3, 8, 11, 6, 15, 13
    };
    private static final int[] RR = {  // Message schedule for right line
            5, 14, 7, 0, 9, 2, 11, 4, 13, 6, 15, 8, 1, 10, 3, 12,
            6, 11, 3, 7, 0, 13, 5, 10, 14, 15, 8, 12, 4, 9, 1, 2,
            15, 5, 1, 3, 7, 14, 6, 9, 11, 8, 12, 2, 10, 0, 4, 13,
            8, 6, 4, 1, 3, 11, 15, 0, 5, 12, 2, 13, 9, 7, 10, 14,
            12, 15, 10, 4, 1, 5, 8, 7, 6, 2, 13, 14, 0, 3, 9, 11
    };
    private static final int[] SL = {  // Left-rotation for left line
            11, 14, 15, 12, 5, 8, 7, 9, 11, 13, 14, 15, 6, 7, 9, 8,
            7, 6, 8, 13, 11, 9, 7, 15, 7, 12, 15, 9, 11, 7, 13, 12,
            11, 13, 6, 7, 14, 9, 13, 15, 14, 8, 13, 6, 5, 12, 7, 5,
            11, 12, 14, 15, 14, 15, 9, 8, 9, 14, 5, 6, 8, 6, 5, 12,
            9, 15, 5, 11, 6, 8, 13, 12, 5, 12, 13, 14, 11, 8, 5, 6
    };
    private static final int[] SR = {  // Left-rotation for right line
            8, 9, 9, 11, 13, 15, 15, 5, 7, 7, 8, 11, 14, 14, 12, 6,
            9, 13, 15, 7, 12, 8, 9, 11, 7, 7, 12, 7, 6, 15, 13, 11,
            9, 7, 15, 11, 8, 6, 6, 14, 12, 13, 5, 14, 13, 13, 7, 5,
            15, 5, 8, 11, 14, 14, 6, 14, 6, 9, 12, 9, 12, 5, 15, 8,
            8, 5, 12, 9, 12, 5, 14, 6, 8, 13, 6, 5, 15, 13, 11, 11
    };

    static {
        Arrays.fill(INDEXES, -1);
        for (int i = 0; i < ALPHABET.length; i++)
            INDEXES[ALPHABET[i]] = i;
    }

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



    /*---- Private functions ----*/

    public static String generateKeyPairToFile() {
        try (FileOutputStream pubKey = new FileOutputStream(LOCATION_TO_STORE_KEYS + PUBLIC_KEY_NAME + ".pem");
             FileOutputStream priKey = new FileOutputStream(LOCATION_TO_STORE_KEYS + PRIVATE_KEY_NAME + ".pem")) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            KeyPair pair = generator.generateKeyPair();

            PrivateKey privateKey = pair.getPrivate();
            PublicKey publicKey = pair.getPublic();

            log.info("generating " + PRIVATE_KEY_NAME + ".pem...........\n");

            String privateKeyString = Base64.getMimeEncoder().encodeToString(privateKey.getEncoded());

            log.info(PRIVATE_KEY_NAME + ".pem ==> \n" + privateKeyString + "\n");

            log.info("generating " + PUBLIC_KEY_NAME + ".pem...........\n");

            String publicKeyString = Base64.getMimeEncoder().encodeToString(publicKey.getEncoded());

            log.info(PUBLIC_KEY_NAME + ".pem ==> \n" + publicKeyString + "\n");

            priKey.write(("-----BEGIN PRIVATE KEY-----" +
                    "\n" +
                    privateKeyString +
                    "\n" +
                    "-----END PRIVATE KEY-----").getBytes(StandardCharsets.UTF_8));
            pubKey.write(("-----BEGIN PUBLIC KEY-----" +
                    "\n" +
                    publicKeyString +
                    "\n" +
                    "-----END PUBLIC KEY-----").getBytes(StandardCharsets.UTF_8));

            return compressPublicKey(publicKeyString) + " " + privateKeyString;
        } catch (IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String compressPublicKey(String toCompress) {
        if (Integer.parseInt(toCompress.substring(128, 130), 16) % 2 == 0)
            return EVEN + toCompress.substring(2, 66);
        return ODD + toCompress.substring(2, 66);
    }


    /*---- Class constants ----*/

    public static String encodeBase58(String key) {
        byte[] input = null;
        try {
            input = key.getBytes("UTF-8");
        } catch (Exception ex) {
            log.error("Error occurred while encoding key (Base58) >> {} << \nexception: {}, message: {}, stackTrace: {}", key, ex.getCause(), ex.getMessage(), ex.getStackTrace());
        }
        if (input.length == 0) {
            return "";
        }
        // Count leading zeros.
        int zeros = 0;
        while (zeros < input.length && input[zeros] == 0) {
            ++zeros;
        }
        // Convert base-256 digits to base-58 digits (plus conversion to ASCII characters)
        input = Arrays.copyOf(input, input.length); // since we modify it in-place
        char[] encoded = new char[input.length * 2]; // upper bound
        int outputStart = encoded.length;
        for (int inputStart = zeros; inputStart < input.length; ) {
            encoded[--outputStart] = ALPHABET[divmod(input, inputStart, 256, 58)];
            if (input[inputStart] == 0) {
                ++inputStart; // optimization - skip leading zeros
            }
        }
        // Preserve exactly as many leading encoded zeros in output as there were leading zeros in input.
        while (outputStart < encoded.length && encoded[outputStart] == ENCODED_ZERO) {
            ++outputStart;
        }
        while (--zeros >= 0) {
            encoded[--outputStart] = ENCODED_ZERO;
        }
        // Return encoded string (including encoded leading zeros).
        return new String(encoded, outputStart, encoded.length - outputStart);
    }

    public static byte[] decodeBase58(String input) {
        if (input.length() == 0) {
            return new byte[0];
        }
        // Convert the base58-encoded ASCII chars to a base58 byte sequence (base58 digits).
        byte[] input58 = new byte[input.length()];
        for (int i = 0; i < input.length(); ++i) {
            char c = input.charAt(i);
            int digit = c < 128 ? INDEXES[c] : -1;
            if (digit < 0) {
                throw new IllegalStateException("InvalidCharacter in base 58");
            }
            input58[i] = (byte) digit;
        }
        // Count leading zeros.
        int zeros = 0;
        while (zeros < input58.length && input58[zeros] == 0) {
            ++zeros;
        }
        // Convert base-58 digits to base-256 digits.
        byte[] decoded = new byte[input.length()];
        int outputStart = decoded.length;
        for (int inputStart = zeros; inputStart < input58.length; ) {
            decoded[--outputStart] = divmod(input58, inputStart, 58, 256);
            if (input58[inputStart] == 0) {
                ++inputStart; // optimization - skip leading zeros
            }
        }
        // Ignore extra leading zeroes that were added during the calculation.
        while (outputStart < decoded.length && decoded[outputStart] == 0) {
            ++outputStart;
        }
        // Return decoded data (including original number of leading zeros).
        return Arrays.copyOfRange(decoded, outputStart - zeros, decoded.length);
    }

    private static byte divmod(byte[] number, int firstDigit, int base, int divisor) {
        // this is just long division which accounts for the base of the input digits
        int remainder = 0;
        for (int i = firstDigit; i < number.length; i++) {
            int digit = (int) number[i] & 0xFF;
            int temp = remainder * base + digit;
            number[i] = (byte) (temp / divisor);
            remainder = temp % divisor;
        }
        return (byte) remainder;
    }

    public static byte[] ripemd160(String input) {
        byte[] msg = null;
        try {
            msg = input.getBytes("UTF-8");
        } catch (Exception ex) {
            log.error("Error occurred while encoding key (Base58) >> {} << \nexception: {}, message: {}, stackTrace: {}", input, ex.getCause(), ex.getMessage(), ex.getStackTrace());
        }
        // Compress whole message blocks
        Objects.requireNonNull(msg);
        int[] state = {0x67452301, 0xEFCDAB89, 0x98BADCFE, 0x10325476, 0xC3D2E1F0};
        int off = msg.length / BLOCK_LEN * BLOCK_LEN;
        compress(state, msg, off);

        // Final blocks, padding, and length
        byte[] block = new byte[BLOCK_LEN];
        System.arraycopy(msg, off, block, 0, msg.length - off);
        off = msg.length % block.length;
        block[off] = (byte) 0x80;
        off++;
        if (off + 8 > block.length) {
            compress(state, block, block.length);
            Arrays.fill(block, (byte) 0);
        }
        long len = (long) msg.length << 3;
        for (int i = 0; i < 8; i++)
            block[block.length - 8 + i] = (byte) (len >>> (i * 8));
        compress(state, block, block.length);

        // Int32 array to bytes in little endian
        byte[] result = new byte[state.length * 4];
        for (int i = 0; i < result.length; i++)
            result[i] = (byte) (state[i / 4] >>> (i % 4 * 8));
        return new String(result).getBytes();
    }

    private static void compress(int[] state, byte[] blocks, int len) {
        if (len % BLOCK_LEN != 0)
            throw new IllegalArgumentException();
        for (int i = 0; i < len; i += BLOCK_LEN) {

            // Message schedule
            int[] schedule = new int[16];
            for (int j = 0; j < BLOCK_LEN; j++)
                schedule[j / 4] |= (blocks[i + j] & 0xFF) << (j % 4 * 8);

            // The 80 rounds
            int al = state[0], ar = state[0];
            int bl = state[1], br = state[1];
            int cl = state[2], cr = state[2];
            int dl = state[3], dr = state[3];
            int el = state[4], er = state[4];
            for (int j = 0; j < 80; j++) {
                int temp;
                temp = rotateLeft(al + f(j, bl, cl, dl) + schedule[RL[j]] + KL[j / 16], SL[j]) + el;
                al = el;
                el = dl;
                dl = rotateLeft(cl, 10);
                cl = bl;
                bl = temp;
                temp = rotateLeft(ar + f(79 - j, br, cr, dr) + schedule[RR[j]] + KR[j / 16], SR[j]) + er;
                ar = er;
                er = dr;
                dr = rotateLeft(cr, 10);
                cr = br;
                br = temp;
            }
            int temp = state[1] + cl + dr;
            state[1] = state[2] + dl + er;
            state[2] = state[3] + el + ar;
            state[3] = state[4] + al + br;
            state[4] = state[0] + bl + cr;
            state[0] = temp;
        }
    }

    private static int f(int i, int x, int y, int z) {
        assert 0 <= i && i < 80;
        if (i < 16) return x ^ y ^ z;
        if (i < 32) return (x & y) | (~x & z);
        if (i < 48) return (x | ~y) ^ z;
        if (i < 64) return (x & z) | (y & ~z);
        return x ^ (y | ~z);
    }

    public static String hash160(String input) {
        return Arrays.toString(ripemd160(getHashSHA256(input)));
    }
}
