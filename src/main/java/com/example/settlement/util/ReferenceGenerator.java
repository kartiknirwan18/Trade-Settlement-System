package com.example.settlement.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Produces human readable, collision-resistant business references.
 * Uniqueness is ultimately guaranteed by database unique constraints and the
 * retry loop in the services that use this utility.
 */
public final class ReferenceGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private ReferenceGenerator() {
    }

    public static String trade() {
        return "TRD-" + LocalDate.now().format(DAY) + "-" + randomSuffix(6);
    }

    public static String invoice() {
        return "INV-" + LocalDate.now().format(DAY) + "-" + randomSuffix(6);
    }

    public static String payment() {
        return "PAY-" + LocalDate.now().format(DAY) + "-" + randomSuffix(6);
    }

    private static String randomSuffix(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(ThreadLocalRandom.current().nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
