package com.dalalx.core.model;

/**
 * Formats money amounts, stored as {@code long} paise, as Indian rupee strings.
 *
 * <p>All prices in DalalX are whole numbers of paise (₹1 = 100 paise), never
 * {@code double}. Binary floating point can't represent values like 0.10
 * exactly, so sums drift (0.1 + 0.2 != 0.3), which isn't acceptable for money.
 * Integer paise are exact. This class is the single place that turns paise into
 * human-readable text.
 *
 * <p>Indian digit grouping puts a comma after the last three digits, then after
 * every two: 2,97,780 (not 297,780) and 1,00,00,000 for one crore.
 *
 * <p>It's a utility class: {@code final}, with a private constructor and only
 * static methods, because it has no state.
 */
public final class Money {

    private static final int PAISE_PER_RUPEE = 100;

    private Money() {
        // Utility class: not meant to be instantiated.
    }

    /**
     * Formats paise as rupees, e.g. {@code 29778000 → "₹2,97,780.00"},
     * {@code -10250 → "-₹102.50"}.
     *
     * @param paise amount in paise (may be negative)
     * @return formatted rupee string
     */
    public static String format(long paise) {
        // Split before taking the sign off. Math.abs(Long.MIN_VALUE) overflows,
        // but the quotient and remainder are always small enough to negate safely.
        long rupees = Math.abs(paise / PAISE_PER_RUPEE);
        long fraction = Math.abs(paise % PAISE_PER_RUPEE);
        String sign = paise < 0 ? "-" : "";
        return "%s₹%s.%02d".formatted(sign, groupIndian(rupees), fraction);
    }

    /**
     * Converts a rupee-and-paise amount to paise, e.g. {@code (102, 50) → 10250}.
     *
     * @param rupees whole rupees (&gt;= 0)
     * @param paise  extra paise, 0–99
     * @return total paise
     */
    public static long ofRupees(long rupees, int paise) {
        if (rupees < 0 || paise < 0 || paise >= PAISE_PER_RUPEE) {
            throw new IllegalArgumentException("invalid amount: " + rupees + " rupees " + paise + " paise");
        }
        return Math.addExact(Math.multiplyExact(rupees, PAISE_PER_RUPEE), paise);
    }

    /** Inserts commas in Indian style: last 3 digits, then groups of 2. */
    private static String groupIndian(long rupees) {
        String digits = Long.toString(rupees);
        if (digits.length() <= 3) {
            return digits;
        }
        String lastThree = digits.substring(digits.length() - 3);
        String rest = digits.substring(0, digits.length() - 3);
        StringBuilder grouped = new StringBuilder();
        // An odd-length rest starts with a one-digit group ("1" in 1,00,000);
        // after that, every group has two digits.
        int firstGroup = rest.length() % 2 == 0 ? 2 : 1;
        grouped.append(rest, 0, firstGroup);
        for (int i = firstGroup; i < rest.length(); i += 2) {
            grouped.append(',').append(rest, i, i + 2);
        }
        return grouped.append(',').append(lastThree).toString();
    }
}
