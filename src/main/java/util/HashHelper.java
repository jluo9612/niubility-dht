package util;

//import com.sun.tools.corba.se.idl.toJavaPortable.Helper;

import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

/**
 * HashHelper:
 * 1. Hash string and socket address by SHA-1
 * 2. Calculate distance between nodes and values in finger table.
 */

public final class HashHelper {
    private static HashMap<Integer, Long> powerOfTwo = null;

    /**
     * static to initialize
     */
    static {
        initialize();
    }

    /**
     * Initialize the power of two hash table
     */
    private static void initialize() {
        powerOfTwo = new HashMap<>();
        powerOfTwo.put(0, 1l);
        for (int i = 1; i <= 32; i++) {
            powerOfTwo.put(i, powerOfTwo.get(i - 1) * 2);
        }
    }


    /**
     * Compute a socket address's hash code in 32 bit identifier
     * @param addr： socket address
     * @return 32-bit identifier in long type
     */
    public static long hashSocketAddress(InetSocketAddress addr) {
        int hashcode = addr.hashCode();
        return sha1HashCode(hashcode);
    }

    /**
     * Compute a string's hash code in 32 bit identifier
     * @param str: string
     * @return 32-bit identifier in long type
     */
    public static long hashString(String str) {
        int hashcode = str.hashCode();
        return sha1HashCode(hashcode);
    }

    /**
     * Compute SHA-1 hash code to 32 bit identifier
     * @param hashcode: integer
     * @return 32-bit identifier in long type
     */
    public static long sha1HashCode(int hashcode) {

        byte[] hashbytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            hashbytes[i] = (byte) (hashcode >> (i * 8));
        }

        MessageDigest sha = null;
        try {
            sha = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        if (sha != null) {
            sha.reset();
            sha.update(hashbytes);
            byte[] shaBytes = sha.digest(); // length of shaBytes is 20
            long ret = 0;

            // Compress sha-1 bytes into 32-bit result
            for (int i = 0; i < 4; i++) {
                byte temp = shaBytes[0];
                for (int j = i * 5 + 1; j < (i + 1) * 5; j++) {
                    temp ^= shaBytes[j];
                }
                ret = ret | ((temp & 0xFF) << (i * 8));
            }
            ret = ret & 0xFFFFFFFFl;
            return ret;
        }
        return 0;
    }

    /**
     * Get distance between original identifier and current identifier
     * @param original: original identifier
     * @param current:  current identifier
     * @return relative identifier
     */
    public static long getRelativeId(long original, long current) {
        long res = original - current;
        return res < 0 ? res + getPowerOfTwo(32) : res;
    }

    /**
     * Get a socket address by SHA-1 in hex,
     * Get the approximate position of node in the ring
     * @param addr
     * @return
     */
    public static String hexIdAndLocation(InetSocketAddress addr) {
        long hash = hashSocketAddress(addr);
        return (hexLongDigit(hash) + " (" + locate(hash) + "%)");
    }

//    long hash = hashSocketAddress(addr);
//		return (longTo8DigitHex(hash)+" ("+hash*100/Helper.getPowerOfTwo(32)+"%)");

    public static double locate(long hash) {
        return hash*100 / getPowerOfTwo(32);
    }

    /**
     * Transfer a long type number to 8-digit hex string
     * @param id: long
     * @return hex string
     */
    public static String hexLongDigit(long id) {
        String hexString = Long.toHexString(id);
        StringBuilder sb = new StringBuilder();
        for (int i = hexString.length(); i < 8; i++) {
            sb.append("0");
        }
        sb.append(hexString);
        return sb.toString();
    }

    /**
     * Get the ith position in finger table
     * @param id: node's identifier
     * @param i:  index of finger table
     * @return ft[i] identifier
     */
    public static long ithInFingerTable(long id, int i) {
        return (id + getPowerOfTwo(i - 1)) % getPowerOfTwo(32);
    }

    /**
     * Get the power of 2
     * @param i
     * @return 2 ^ i
     */
    public static long getPowerOfTwo(int i) {
        return powerOfTwo.get(i);
    }

}
