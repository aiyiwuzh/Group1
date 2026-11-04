
package com.splitsmart.backend.service;

import com.splitsmart.backend.entity.SplitMethod;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

// Turns an expense total and a split method into one share per participant.
//
// Every result adds up to the total exactly, has no negative shares and
// includes the payer. Bad input throws InvalidSplitException.
//
// When the total cannot be divided evenly, the leftover cents go to
// participants picked pseudo-randomly. The pick depends only on the inputs,
// so the preview and the saved expense always show the same shares.
public class SplitCalculator {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    // splitValues: EXACT = dollars per participant, PERCENTAGE = percent per
    // participant, EQUAL = ignored (may be null). Returns user id -> share, in
    // the same order as participantIds.
    public Map<Long, BigDecimal> calculateShares(
            BigDecimal totalAmount,
            SplitMethod method,
            Long payerId,
            List<Long> participantIds,
            Map<Long, BigDecimal> splitValues) {

        validateTotal(totalAmount);
        validateParticipants(payerId, participantIds);

        if (method == null) {
            throw new InvalidSplitException("Choose a split method");
        }

        long totalCents = toCents(totalAmount);

        long[] cents = switch (method) {
            case EQUAL -> equalSplit(totalCents, participantIds);
            case EXACT -> exactSplit(totalCents, participantIds, splitValues);
            case PERCENTAGE -> percentageSplit(totalAmount, participantIds, splitValues);
        };

        Map<Long, BigDecimal> shares = new LinkedHashMap<>();
        for (int i = 0; i < cents.length; i++) {
            shares.put(participantIds.get(i), fromCents(cents[i]));
        }

        verifySum(shares, totalAmount);
        return shares;
    }

    // Final check before saving: no negative shares, at most 2 decimals, and
    // the shares add up to the total.
    public void verifySum(Map<Long, BigDecimal> shares, BigDecimal totalAmount) {
        validateTotal(totalAmount);

        if (shares == null || shares.isEmpty()) {
            throw new InvalidSplitException("The expense has no shares");
        }

        long sumCents = 0;
        for (BigDecimal share : shares.values()) {
            if (share == null) {
                throw new InvalidSplitException("Every participant needs a share");
            }
            if (share.signum() < 0) {
                throw new InvalidSplitException("Shares cannot be negative");
            }
            if (hasMoreThanTwoDecimals(share)) {
                throw new InvalidSplitException(
                        "Shares can have at most 2 decimal places");
            }
            if (share.compareTo(totalAmount) > 0) {
                throw new InvalidSplitException(
                        "A share cannot be more than the total");
            }
            sumCents += toCents(share);
        }

        long totalCents = toCents(totalAmount);
        if (sumCents != totalCents) {
            throw new InvalidSplitException("Shares add up to " + money(sumCents)
                    + " but the total is " + money(totalCents));
        }
    }

    // ---- Split methods

    private long[] equalSplit(long totalCents, List<Long> participantIds) {
        int n = participantIds.size();
        long[] cents = new long[n];
        Arrays.fill(cents, totalCents / n);

        int leftover = (int) (totalCents % n);
        List<Integer> everyone = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            everyone.add(i);
        }
        giveLeftoverCents(cents, everyone, leftover,
                randomFor(totalCents, SplitMethod.EQUAL, participantIds, null));

        return cents;
    }

    private long[] exactSplit(
            long totalCents,
            List<Long> participantIds,
            Map<Long, BigDecimal> amounts) {

        requireValuesForParticipants(participantIds, amounts, "an amount");

        long[] cents = new long[participantIds.size()];
        long sumCents = 0;

        for (int i = 0; i < cents.length; i++) {
            BigDecimal amount = amounts.get(participantIds.get(i));

            if (amount == null) {
                throw new InvalidSplitException(
                        "Enter an amount for every participant ($0.00 is allowed)");
            }
            if (amount.signum() < 0) {
                throw new InvalidSplitException("Amounts cannot be negative");
            }
            if (hasMoreThanTwoDecimals(amount)) {
                throw new InvalidSplitException(
                        "Amounts can have at most 2 decimal places");
            }
            // Also stops absurdly large numbers before they overflow
            if (amount.compareTo(fromCents(totalCents)) > 0) {
                throw new InvalidSplitException(
                        "An amount cannot be more than the total of " + money(totalCents));
            }

            cents[i] = toCents(amount);
            sumCents += cents[i];
        }

        if (sumCents != totalCents) {
            throw new InvalidSplitException("Amounts add up to " + money(sumCents)
                    + " but the total is " + money(totalCents));
        }

        return cents;
    }

    // Percentages must add up to exactly 100. Each share is rounded down to the
    // cent, then the leftover cents go to participants whose exact share was
    // not a whole number of cents, so someone at 0% never gets a cent.
    private long[] percentageSplit(
            BigDecimal totalAmount,
            List<Long> participantIds,
            Map<Long, BigDecimal> percents) {

        requireValuesForParticipants(participantIds, percents, "a percentage");

        BigDecimal percentSum = BigDecimal.ZERO;
        for (Long id : participantIds) {
            BigDecimal percent = percents.get(id);

            if (percent == null) {
                throw new InvalidSplitException(
                        "Enter a percentage for every participant (0% is allowed)");
            }
            if (percent.signum() < 0) {
                throw new InvalidSplitException("Percentages cannot be negative");
            }
            if (percent.stripTrailingZeros().scale() > 8) {
                throw new InvalidSplitException(
                        "Percentages can have at most 8 decimal places");
            }
            percentSum = percentSum.add(percent);
        }

        if (percentSum.compareTo(ONE_HUNDRED) != 0) {
            throw new InvalidSplitException("Percentages add up to "
                    + percentSum.stripTrailingZeros().toPlainString()
                    + "% but must add up to 100%");
        }

        int n = participantIds.size();
        long[] cents = new long[n];
        List<Integer> hasFraction = new ArrayList<>();
        long flooredSum = 0;

        for (int i = 0; i < n; i++) {
            // Dividing by 100 always terminates, so no rounding mode is needed here.
            BigDecimal exact = totalAmount
                    .multiply(percents.get(participantIds.get(i)))
                    .divide(ONE_HUNDRED);
            BigDecimal floored = exact.setScale(2, RoundingMode.DOWN);

            cents[i] = toCents(floored);
            flooredSum += cents[i];

            if (exact.compareTo(floored) != 0) {
                hasFraction.add(i);
            }
        }

        long leftover = toCents(totalAmount) - flooredSum;
        if (leftover < 0 || leftover > hasFraction.size()) {
            throw new IllegalStateException("Could not round the percentage split");
        }

        giveLeftoverCents(cents, hasFraction, (int) leftover,
                randomFor(toCents(totalAmount), SplitMethod.PERCENTAGE,
                        participantIds, percents));

        return cents;
    }

    // Adds one cent to `count` different people chosen from `candidates`.
    private static void giveLeftoverCents(
            long[] cents, List<Integer> candidates, int count, Random random) {

        List<Integer> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled, random);

        for (int i = 0; i < count; i++) {
            cents[shuffled.get(i)]++;
        }
    }

    // Same inputs always give the same Random, so the same people get the leftover cents.
    private static Random randomFor(
            long totalCents,
            SplitMethod method,
            List<Long> participantIds,
            Map<Long, BigDecimal> splitValues) {

        long seed = totalCents;
        seed = seed * 31 + method.ordinal();

        for (Long id : participantIds) {
            seed = seed * 31 + id;

            BigDecimal value = splitValues == null ? null : splitValues.get(id);
            if (value != null) {
                seed = seed * 31
                        + value.stripTrailingZeros().toPlainString().hashCode();
            }
        }

        return new Random(seed);
    }

    // ---- Validation

    private static void validateTotal(BigDecimal totalAmount) {
        if (totalAmount == null || totalAmount.signum() <= 0) {
            throw new InvalidSplitException("Amount must be greater than 0");
        }
        if (hasMoreThanTwoDecimals(totalAmount)) {
            throw new InvalidSplitException(
                    "Amount can have at most 2 decimal places");
        }
    }

    private static void validateParticipants(
            Long payerId, List<Long> participantIds) {

        if (participantIds == null || participantIds.size() < 2) {
            throw new InvalidSplitException(
                    "An expense needs at least 2 participants, including the payer");
        }

        Set<Long> unique = new HashSet<>();
        for (Long id : participantIds) {
            if (id == null) {
                throw new InvalidSplitException("Every participant must be valid");
            }
            if (!unique.add(id)) {
                throw new InvalidSplitException(
                        "A participant cannot be listed more than once");
            }
        }

        if (payerId == null) {
            throw new InvalidSplitException("Payer is required");
        }
        if (!unique.contains(payerId)) {
            throw new InvalidSplitException(
                    "The payer must be included as a participant");
        }
    }

    // The values must exist and may only be for people in this expense.
    private static void requireValuesForParticipants(
            List<Long> participantIds, Map<Long, BigDecimal> values, String what) {

        if (values == null) {
            throw new InvalidSplitException(
                    "Enter " + what + " for every participant");
        }

        Set<Long> participants = new HashSet<>(participantIds);
        for (Long id : values.keySet()) {
            if (!participants.contains(id)) {
                throw new InvalidSplitException(
                        "A value was entered for someone who is not a participant");
            }
        }
    }

    // ---- Money helpers

    private static boolean hasMoreThanTwoDecimals(BigDecimal value) {
        return value.stripTrailingZeros().scale() > 2;
    }

    // Only called for values with at most 2 decimal places.
    private static long toCents(BigDecimal dollars) {
        return dollars.movePointRight(2)
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact();
    }

    private static BigDecimal fromCents(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }

    private static String money(long cents) {
        return "$" + fromCents(cents).toPlainString();
    }
}
