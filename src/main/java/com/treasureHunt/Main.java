package com.treasureHunt;

import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

import java.util.HexFormat;
import java.util.List;

public class Main {
    static byte[] secretCode;

    public static void main(String[] args) {
        runTests();
        secretCode = HexFormat.ofDelimiter(" ")
                .parseHex("f3 4b ad 28 51 48 ce 29 4d b5 52 70 2c 2d 2e 29 4a cc c9 4c 04 00");

        Inflate decompressor = new Inflate();
        String result = decompressor.uncompress(secretCode);

        System.out.println("\n\u001B[36m" + result);
    }



    private static void runTests() {
        JUnitCore junit = new JUnitCore();
        Result unitResult = junit.run(InflateTest.class);

        System.out.println("Tests run: " + unitResult.getRunCount());
        System.out.println("\u001B[32mPasses: " + (unitResult.getRunCount() - unitResult.getFailureCount()));
        System.out.println("\u001B[31mFailures: " + unitResult.getFailureCount() + "\u001B[0m");
        if (unitResult.getFailureCount() != 0) {
            System.out.println("\u001B[31m+++++++++++++++++++++ FAILURES!!! +++++++++++++++++++++\u001B[0m");

            List<Failure> fails = unitResult.getFailures();
            for (Failure fail : fails) {
                System.out.println("On: " + fail.getDescription());
                System.out.println("Reason: " + fail.getMessage() + "\n");
            }
        }
    }
}
