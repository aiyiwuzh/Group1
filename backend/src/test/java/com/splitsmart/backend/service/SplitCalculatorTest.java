package com.splitsmart.backend.service;

import com.splitsmart.backend.entity.SplitMethod;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SplitCalculatorTest {

    private static final Long ALICE = 1L;
    private static final Long BOB = 2L;
    private static final Long CAROL = 3L;

    private final SplitCalculator calculator = new SplitCalculator();

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    // Builds {id -> value} from alternating id, value arguments.
    private static Map<Long, BigDecimal> values(Object... idAndValue) {
        Map<Long, BigDecimal> map = new HashMap<>();
        for (int i = 0; i < idAndValue.length; i += 2) {
            map.put((Long) idAndValue[i], d((String) idAndValue[i + 1]));
        }
        return map;
    }

    private static BigDecimal sum(Map<Long, BigDecimal> shares) {
        return shares.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<Long, BigDecimal> equal(String total, Long payer, Long... people) {
        return calculator.calculateShares(
                d(total), SplitMethod.EQUAL, payer, List.of(people), null);
    }

    private Map<Long, BigDecimal> exact(
            String total, List<Long> people, Map<Long, BigDecimal> amounts) {
        return calculator.calculateShares(
                d(total), SplitMethod.EXACT, people.get(0), people, amounts);
    }

    private Map<Long, BigDecimal> percent(
            String total, List<Long> people, Map<Long, BigDecimal> percents) {
        return calculator.calculateShares(
                d(total), SplitMethod.PERCENTAGE, people.get(0), people, percents);
    }

    private void assertInvalid(Runnable action) {
        assertThrows(InvalidSplitException.class, action::run);
    }

    private static void assertShare(String expected, Map<Long, BigDecimal> shares, Long userId) {
        assertEquals(0, d(expected).compareTo(shares.get(userId)));
    }

    // ---- Equal split

    @Test
    void equalSplitOfEvenAmountGivesEveryoneTheSame() {
        Map<Long, BigDecimal> shares = equal("30.00", ALICE, ALICE, BOB, CAROL);

        assertShare("10.00", shares, ALICE);
        assertShare("10.00", shares, BOB);
        assertShare("10.00", shares, CAROL);
    }

    @Test
    void equalSplitGivesTheExtraCentToOnePerson() {
        // $10.00 / 3: one person pays $3.34, the other two $3.33
        Map<Long, BigDecimal> shares = equal("10.00", ALICE, ALICE, BOB, CAROL);

        assertEquals(1, shares.values().stream()
                .filter(s -> s.compareTo(d("3.34")) == 0).count());
        assertEquals(2, shares.values().stream()
                .filter(s -> s.compareTo(d("3.33")) == 0).count());
        assertEquals(0, d("10.00").compareTo(sum(shares)));
    }

    @Test
    void equalSplitGivesTwoLeftoverCentsToTwoDifferentPeople() {
        Map<Long, BigDecimal> shares = equal("10.02", ALICE, ALICE, BOB, CAROL, 4L);

        assertEquals(2, shares.values().stream()
                .filter(s -> s.compareTo(d("2.51")) == 0).count());
        assertEquals(2, shares.values().stream()
                .filter(s -> s.compareTo(d("2.50")) == 0).count());
    }

    @Test
    void equalSplitOfOneCentLeavesTheOtherPersonAtZero() {
        Map<Long, BigDecimal> shares = equal("0.01", ALICE, ALICE, BOB);

        assertEquals(0, d("0.01").compareTo(sum(shares)));
        assertTrue(shares.values().stream().anyMatch(s -> s.signum() == 0));
    }

    @Test
    void equalSplitIgnoresAnyValuesSent() {
        Map<Long, BigDecimal> shares = calculator.calculateShares(
                d("20.00"), SplitMethod.EQUAL, ALICE, List.of(ALICE, BOB),
                values(ALICE, "19.00", BOB, "1.00"));

        assertShare("10.00", shares, ALICE);
        assertShare("10.00", shares, BOB);
    }

    @Test
    void equalSplitAlwaysAddsUpAndStaysWithinOneCent() {
        for (int totalCents = 1; totalCents <= 1000; totalCents += 7) {
            BigDecimal total = BigDecimal.valueOf(totalCents, 2);

            for (int n = 2; n <= 9; n++) {
                List<Long> people = new ArrayList<>();
                for (long id = 1; id <= n; id++) {
                    people.add(id);
                }

                Map<Long, BigDecimal> shares = calculator.calculateShares(
                        total, SplitMethod.EQUAL, 1L, people, null);

                assertEquals(0, total.compareTo(sum(shares)));
                BigDecimal min = shares.values().stream().min(BigDecimal::compareTo).get();
                BigDecimal max = shares.values().stream().max(BigDecimal::compareTo).get();
                assertTrue(max.subtract(min).compareTo(d("0.01")) <= 0);
            }
        }
    }

    @Test
    void extraCentIsNotAlwaysGivenToTheSamePerson() {
        // Totals of $10.00, $10.03, $10.06 ... each leave one cent over
        Set<Long> recipients = new HashSet<>();
        for (int k = 0; k < 60; k++) {
            BigDecimal total = d("10.00").add(BigDecimal.valueOf(3L * k, 2));
            Map<Long, BigDecimal> shares =
                    equal(total.toPlainString(), ALICE, ALICE, BOB, CAROL);

            BigDecimal max = shares.values().stream().max(BigDecimal::compareTo).get();
            shares.forEach((id, share) -> {
                if (share.compareTo(max) == 0) {
                    recipients.add(id);
                }
            });
        }

        assertEquals(Set.of(ALICE, BOB, CAROL), recipients);
    }

    @Test
    void sameInputsAlwaysGiveTheSameShares() {
        // The preview and the saved expense must match
        assertEquals(equal("10.00", ALICE, ALICE, BOB, CAROL),
                equal("10.00", ALICE, ALICE, BOB, CAROL));

        Map<Long, BigDecimal> pcts = values(ALICE, "33.33", BOB, "33.33", CAROL, "33.34");
        List<Long> people = List.of(ALICE, BOB, CAROL);
        assertEquals(percent("10.00", people, pcts), percent("10.00", people, pcts));
    }

    @Test
    void equalSplitAcceptsWholeAndTrailingZeroAmounts() {
        assertEquals(0, d("30").compareTo(sum(equal("30", ALICE, ALICE, BOB, CAROL))));
        assertEquals(0, d("30").compareTo(sum(equal("30.000", ALICE, ALICE, BOB, CAROL))));
    }

    // ---- Exact amounts

    @Test
    void exactAmountsAreUsedAsEntered() {
        Map<Long, BigDecimal> shares = exact("45.50", List.of(ALICE, BOB, CAROL),
                values(ALICE, "20.00", BOB, "15.25", CAROL, "10.25"));

        assertShare("20.00", shares, ALICE);
        assertShare("15.25", shares, BOB);
        assertShare("10.25", shares, CAROL);
    }

    @Test
    void exactAmountOfZeroIsAllowed() {
        Map<Long, BigDecimal> shares = exact("20.00", List.of(ALICE, BOB, CAROL),
                values(ALICE, "20.00", BOB, "0.00", CAROL, "0"));

        assertShare("0.00", shares, BOB);
        assertShare("0.00", shares, CAROL);
    }

    @Test
    void exactSplitStillListsThePayerWhenTheirShareIsZero() {
        Map<Long, BigDecimal> shares = exact("20.00", List.of(ALICE, BOB),
                values(ALICE, "0.00", BOB, "20.00"));

        assertEquals(2, shares.size());
        assertShare("0.00", shares, ALICE);
    }

    @Test
    void exactAmountsThatAddUpToTooLittleOrTooMuchAreRejected() {
        List<Long> people = List.of(ALICE, BOB);

        assertInvalid(() -> exact("20.00", people, values(ALICE, "10.00", BOB, "9.99")));
        assertInvalid(() -> exact("20.00", people, values(ALICE, "10.00", BOB, "10.01")));
    }

    @Test
    void exactSumMessageShowsBothAmounts() {
        InvalidSplitException e = assertThrows(InvalidSplitException.class,
                () -> exact("20.00", List.of(ALICE, BOB),
                        values(ALICE, "10.00", BOB, "5.00")));

        assertTrue(e.getMessage().contains("$15.00"));
        assertTrue(e.getMessage().contains("$20.00"));
    }

    @Test
    void exactSplitRejectsBlankAmounts() {
        List<Long> people = List.of(ALICE, BOB);

        assertInvalid(() -> exact("20.00", people, values(ALICE, "20.00")));

        Map<Long, BigDecimal> nullBob = values(ALICE, "20.00");
        nullBob.put(BOB, null);
        assertInvalid(() -> exact("20.00", people, nullBob));

        assertInvalid(() -> exact("20.00", people, null));
    }

    @Test
    void exactSplitRejectsNegativeAmountsEvenWhenTheySumToTheTotal() {
        assertInvalid(() -> exact("20.00", List.of(ALICE, BOB),
                values(ALICE, "30.00", BOB, "-10.00")));
    }

    @Test
    void exactSplitRejectsMoreThanTwoDecimalsEvenWhenTheySumToTheTotal() {
        assertInvalid(() -> exact("20.00", List.of(ALICE, BOB),
                values(ALICE, "10.005", BOB, "9.995")));
    }

    @Test
    void exactSplitRejectsValuesForPeopleNotInTheExpense() {
        assertInvalid(() -> exact("20.00", List.of(ALICE, BOB),
                values(ALICE, "10.00", BOB, "10.00", CAROL, "0.00")));
    }

    // ---- Percentages

    @Test
    void percentageSplitWithWholePercents() {
        Map<Long, BigDecimal> shares = percent("100.00", List.of(ALICE, BOB, CAROL),
                values(ALICE, "50", BOB, "30", CAROL, "20"));

        assertShare("50.00", shares, ALICE);
        assertShare("30.00", shares, BOB);
        assertShare("20.00", shares, CAROL);
    }

    @Test
    void percentageSplitHandlesDecimalPercents() {
        Map<Long, BigDecimal> shares = percent("200.00", List.of(ALICE, BOB),
                values(ALICE, "12.5", BOB, "87.5"));

        assertShare("25.00", shares, ALICE);
        assertShare("175.00", shares, BOB);
    }

    @Test
    void percentageRoundingStillAddsUpToTheTotal() {
        // $10.00 at 33.33 / 33.33 / 33.34 rounds down to $9.99, so one cent is handed out
        Map<Long, BigDecimal> shares = percent("10.00", List.of(ALICE, BOB, CAROL),
                values(ALICE, "33.33", BOB, "33.33", CAROL, "33.34"));

        assertEquals(0, d("10.00").compareTo(sum(shares)));
        shares.values().forEach(share -> {
            assertTrue(share.compareTo(d("3.33")) >= 0);
            assertTrue(share.compareTo(d("3.34")) <= 0);
        });
    }

    @Test
    void zeroPercentIsAllowedAndNeverGetsALeftoverCent() {
        for (int k = 0; k < 50; k++) {
            BigDecimal total = d("10.01").add(BigDecimal.valueOf(k, 2));

            Map<Long, BigDecimal> shares = percent(total.toPlainString(),
                    List.of(ALICE, BOB, CAROL),
                    values(ALICE, "50", BOB, "50", CAROL, "0"));

            assertShare("0.00", shares, CAROL);
            assertEquals(0, total.compareTo(sum(shares)));
        }
    }

    @Test
    void oneHundredPercentToOnePerson() {
        Map<Long, BigDecimal> shares = percent("19.99", List.of(ALICE, BOB),
                values(ALICE, "0", BOB, "100"));

        assertShare("0.00", shares, ALICE);
        assertShare("19.99", shares, BOB);
    }

    @Test
    void percentageSplitAlwaysAddsUpAcrossManyTotals() {
        List<Long> people = List.of(ALICE, BOB, CAROL);
        Map<Long, BigDecimal> pcts =
                values(ALICE, "33.33", BOB, "33.33", CAROL, "33.34");

        for (int totalCents = 1; totalCents <= 2000; totalCents += 13) {
            BigDecimal total = BigDecimal.valueOf(totalCents, 2);

            assertEquals(0, total.compareTo(sum(
                    percent(total.toPlainString(), people, pcts))));
        }
    }

    @Test
    void percentagesThatDoNotAddUpTo100AreRejected() {
        List<Long> people = List.of(ALICE, BOB);

        assertInvalid(() -> percent("10.00", people, values(ALICE, "50", BOB, "49.99")));
        assertInvalid(() -> percent("10.00", people, values(ALICE, "50", BOB, "50.01")));
    }

    @Test
    void percentageSplitRejectsBlankPercentages() {
        List<Long> people = List.of(ALICE, BOB);

        assertInvalid(() -> percent("10.00", people, values(ALICE, "100")));

        Map<Long, BigDecimal> nullBob = values(ALICE, "100");
        nullBob.put(BOB, null);
        assertInvalid(() -> percent("10.00", people, nullBob));

        assertInvalid(() -> percent("10.00", people, null));
    }

    @Test
    void percentageSplitRejectsNegativePercentsEvenWhenTheySumTo100() {
        assertInvalid(() -> percent("10.00", List.of(ALICE, BOB),
                values(ALICE, "150", BOB, "-50")));
    }

    @Test
    void percentageSplitRejectsValuesForPeopleNotInTheExpense() {
        assertInvalid(() -> percent("10.00", List.of(ALICE, BOB),
                values(ALICE, "50", BOB, "50", CAROL, "0")));
    }

    // ---- Total, participants and payer

    @Test
    void totalMustBeGreaterThanZeroWithAtMostTwoDecimals() {
        List<Long> people = List.of(ALICE, BOB);

        for (String bad : List.of("0", "0.00", "-5.00", "10.005", "0.001")) {
            assertInvalid(() -> calculator.calculateShares(
                    d(bad), SplitMethod.EQUAL, ALICE, people, null));
        }
        assertInvalid(() -> calculator.calculateShares(
                null, SplitMethod.EQUAL, ALICE, people, null));
    }

    @Test
    void invalidTotalIsRejectedForEverySplitMethod() {
        for (SplitMethod method : SplitMethod.values()) {
            assertInvalid(() -> calculator.calculateShares(
                    d("0"), method, ALICE, List.of(ALICE, BOB),
                    values(ALICE, "0", BOB, "0")));
        }
    }

    @Test
    void payerIsIncludedInTheSharesForEverySplitMethod() {
        // The payer is not first in the list, to be sure it is not just the first entry
        List<Long> people = List.of(BOB, ALICE, CAROL);
        Map<SplitMethod, Map<Long, BigDecimal>> input = new LinkedHashMap<>();
        input.put(SplitMethod.EQUAL, null);
        input.put(SplitMethod.EXACT, values(ALICE, "5.00", BOB, "3.00", CAROL, "2.00"));
        input.put(SplitMethod.PERCENTAGE, values(ALICE, "50", BOB, "30", CAROL, "20"));

        input.forEach((method, splitValues) -> {
            Map<Long, BigDecimal> shares = calculator.calculateShares(
                    d("10.00"), method, ALICE, people, splitValues);

            assertTrue(shares.containsKey(ALICE));
            assertEquals(3, shares.size());
            assertEquals(0, d("10.00").compareTo(sum(shares)));
        });
    }

    @Test
    void sharesKeepTheOrderOfTheParticipants() {
        Map<Long, BigDecimal> shares = equal("30.00", BOB, CAROL, BOB, ALICE);

        assertEquals(List.of(CAROL, BOB, ALICE), new ArrayList<>(shares.keySet()));
    }

    @Test
    void payerMustBeOneOfTheParticipants() {
        for (SplitMethod method : SplitMethod.values()) {
            assertInvalid(() -> calculator.calculateShares(
                    d("10.00"), method, CAROL, List.of(ALICE, BOB),
                    values(ALICE, "5", BOB, "5")));
        }
        assertInvalid(() -> calculator.calculateShares(
                d("10.00"), SplitMethod.EQUAL, null, List.of(ALICE, BOB), null));
    }

    @Test
    void needsAtLeastTwoDistinctParticipants() {
        assertInvalid(() -> equal("10.00", ALICE, ALICE));
        assertInvalid(() -> calculator.calculateShares(
                d("10.00"), SplitMethod.EQUAL, ALICE, List.of(), null));
        assertInvalid(() -> calculator.calculateShares(
                d("10.00"), SplitMethod.EQUAL, ALICE, null, null));
        assertInvalid(() -> equal("10.00", ALICE, ALICE, BOB, BOB));
    }

    @Test
    void splitMethodIsRequired() {
        assertInvalid(() -> calculator.calculateShares(
                d("10.00"), null, ALICE, List.of(ALICE, BOB), null));
    }

    // ---- verifySum

    @Test
    void verifySumAcceptsSharesThatAddUpToTheTotal() {
        Map<Long, BigDecimal> shares = new LinkedHashMap<>();
        shares.put(ALICE, d("3.34"));
        shares.put(BOB, d("3.33"));
        shares.put(CAROL, d("3.33"));

        assertDoesNotThrow(() -> calculator.verifySum(shares, d("10.00")));
    }

    @Test
    void verifySumRejectsSharesThatAreOffByOneCent() {
        Map<Long, BigDecimal> low = values(ALICE, "3.33", BOB, "3.33", CAROL, "3.33");
        Map<Long, BigDecimal> high = values(ALICE, "3.34", BOB, "3.34", CAROL, "3.33");

        assertInvalid(() -> calculator.verifySum(low, d("10.00")));
        assertInvalid(() -> calculator.verifySum(high, d("10.00")));
    }

    @Test
    void verifySumRejectsNegativeOrOverPreciseOrMissingShares() {
        assertInvalid(() -> calculator.verifySum(
                values(ALICE, "15.00", BOB, "-5.00"), d("10.00")));
        assertInvalid(() -> calculator.verifySum(
                values(ALICE, "5.005", BOB, "4.995"), d("10.00")));

        Map<Long, BigDecimal> nullShare = values(ALICE, "10.00");
        nullShare.put(BOB, null);
        assertInvalid(() -> calculator.verifySum(nullShare, d("10.00")));

        assertInvalid(() -> calculator.verifySum(null, d("10.00")));
        assertInvalid(() -> calculator.verifySum(Map.of(), d("10.00")));
    }

    @Test
    void verifySumRejectsABadTotal() {
        Map<Long, BigDecimal> shares = values(ALICE, "5.00", BOB, "5.00");

        assertInvalid(() -> calculator.verifySum(shares, d("0")));
        assertInvalid(() -> calculator.verifySum(shares, null));
    }

    @Test
    void verifySumAcceptsEverythingCalculateSharesProduces() {
        List<Long> people = List.of(ALICE, BOB, CAROL);

        calculator.verifySum(equal("10.00", ALICE, ALICE, BOB, CAROL), d("10.00"));
        calculator.verifySum(percent("10.00", people,
                values(ALICE, "33.33", BOB, "33.33", CAROL, "33.34")), d("10.00"));
        calculator.verifySum(exact("10.00", people,
                values(ALICE, "1.00", BOB, "2.00", CAROL, "7.00")), d("10.00"));
    }
}
