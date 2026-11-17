package com.example.splitsmart;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.espresso.Espresso;

import com.example.splitsmart.session.SessionManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

/** Black-box Espresso tests for the record-payment screen. */
@RunWith(AndroidJUnit4.class)
public class SettlementScreenTest {

    private Context context;
    private SessionManager sessionManager;

    @Before
    public void createTestSession() {
        context = ApplicationProvider.getApplicationContext();
        sessionManager = new SessionManager(context);
        sessionManager.saveSession("espresso-test-token", "Test Member", 3600);
    }

    @After
    public void clearTestSession() {
        sessionManager.logout();
    }

    private ActivityScenario<SettleUpActivity> launchSettlementScreen() {
        Intent intent = new Intent(context, SettleUpActivity.class);
        intent.putExtra(SettleUpActivity.EXTRA_GROUP_ID, 10L);
        intent.putExtra(SettleUpActivity.EXTRA_GROUP_NAME, "Demo group");
        intent.putExtra(SettleUpActivity.EXTRA_PAYER_ID, 1L);
        intent.putExtra(SettleUpActivity.EXTRA_PAYER_NAME, "Alice");
        intent.putExtra(SettleUpActivity.EXTRA_PAYEE_ID, 2L);
        intent.putExtra(SettleUpActivity.EXTRA_PAYEE_NAME, "Bob");
        intent.putExtra(SettleUpActivity.EXTRA_MAX_AMOUNT, "20.00");
        return ActivityScenario.launch(intent);
    }

    @Test
    public void displaysGroupPayerPayeeAndDebtLimit() {
        try (ActivityScenario<SettleUpActivity> ignored = launchSettlementScreen()) {
            onView(withId(R.id.tvSettlementTitle))
                    .check(matches(withText("Record a payment")));
            onView(withId(R.id.tvSettlementPayee))
                    .check(matches(withText("Group: Demo group\nFrom: Alice\nTo: Bob")));
            onView(withId(R.id.tvSettlementMaximum))
                    .check(matches(withText("Current amount owed: $20.00")));
        }
    }

    @Test
    public void prefillsThePaymentWithTheCurrentDebtLimit() {
        try (ActivityScenario<SettleUpActivity> ignored = launchSettlementScreen()) {
            onView(withId(R.id.etSettlementAmount))
                    .check(matches(withText("20.00")));
        }
    }

    @Test
    public void emptyAmountShowsRequiredFieldMessage() {
        try (ActivityScenario<SettleUpActivity> ignored = launchSettlementScreen()) {
            onView(withId(R.id.etSettlementAmount)).perform(clearText());
            Espresso.closeSoftKeyboard();
            onView(withId(R.id.btnSaveSettlement)).perform(scrollTo(), click());
            onView(withId(R.id.tvSettlementError))
                    .check(matches(isDisplayed()))
                    .check(matches(withText("Enter an amount.")));
        }
    }

    @Test
    public void zeroAmountIsRejected() {
        try (ActivityScenario<SettleUpActivity> ignored = launchSettlementScreen()) {
            onView(withId(R.id.etSettlementAmount)).perform(replaceText("0.00"));
            Espresso.closeSoftKeyboard();
            onView(withId(R.id.btnSaveSettlement)).perform(scrollTo(), click());
            onView(withId(R.id.tvSettlementError))
                    .check(matches(isDisplayed()))
                    .check(matches(withText("The amount must be greater than zero.")));
        }
    }

    @Test
    public void amountAboveCurrentDebtIsRejected() {
        try (ActivityScenario<SettleUpActivity> ignored = launchSettlementScreen()) {
            onView(withId(R.id.etSettlementAmount)).perform(replaceText("20.01"));
            Espresso.closeSoftKeyboard();
            onView(withId(R.id.btnSaveSettlement)).perform(scrollTo(), click());
            onView(withId(R.id.tvSettlementError))
                    .check(matches(isDisplayed()))
                    .check(matches(withText(
                            "The amount cannot exceed the current amount owed.")));
        }
    }
}
