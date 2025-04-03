package com.treasureHunt;

import org.junit.Test;
import java.util.HexFormat;
import java.util.Random;

import static org.junit.Assert.assertEquals;

public class InflateTest {
    private final Inflate inflate = new Inflate();

    private byte[] toByte(String HexString) {
        return HexFormat.ofDelimiter(" ").parseHex(HexString);
    }

    @Test
    public void DecompressNumbers() {
        byte[] toDecode = toByte("33 30 30 30 34 34 34 00 93 46 46 46 40 86 a5 a5 25 00");
        assertEquals("000111000111222000999", inflate.uncompress(toDecode));
    }

    @Test
    public void DecompressAlphabets() {
        byte[] toDecode = toByte("4b 4c 04 82 24 08 48 04 a3 a4 c4 92 92 12 00");
        assertEquals("aaaaabbbbbbbabbabbbattt", inflate.uncompress(toDecode));
    }

    @Test
    public void DecompressAlphaNumeric() {
        byte[] toDecode = toByte("cb ce 06 02 23 10 c8 cc 06 c1 02 10 30 04 01 00");
        assertEquals("kkkkk22222ikikikppppp11111", inflate.uncompress(toDecode));
    }

    @Test
    public void DecompressSpecialChar() {
        byte[] toDecode = toByte("b3 b5 b5 b5 d5 06 01 6b 10 b0 02 01 00");
        assertEquals("====+++++;;;;;:::::", inflate.uncompress(toDecode));
    }

    @Test
    public void DecompressRandomString() {
        String ASCIICHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890:;\"'!@#$%^&*()-=_+,.<>?/|\\";
        StringBuilder rand = new StringBuilder();
        Random rnd = new Random();
        while (rand.length() < 18) { // length of the random string.
            int index = (int) (rnd.nextFloat() * ASCIICHARS.length());
            rand.append(ASCIICHARS.charAt(index));
        }
        String randomStr = rand.toString();

        byte[] toDecode = Deflate.compress(randomStr);
        assertEquals(randomStr, inflate.uncompress(toDecode));
    }
}
