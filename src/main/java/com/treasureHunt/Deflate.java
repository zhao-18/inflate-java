package com.treasureHunt;

import java.util.zip.Deflater;

public class Deflate {
    public static byte[] compress(String input) {
        byte[] inputBytes = input.getBytes();
        // Use 'nowrap' set to true to produce raw deflate stream
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        deflater.setInput(inputBytes);
        deflater.finish();

        byte[] buffer = new byte[1024];
        int compressedLength = deflater.deflate(buffer);

        byte[] compressedData = new byte[compressedLength];
        System.arraycopy(buffer, 0, compressedData, 0, compressedLength);

        return compressedData;
    }
}
