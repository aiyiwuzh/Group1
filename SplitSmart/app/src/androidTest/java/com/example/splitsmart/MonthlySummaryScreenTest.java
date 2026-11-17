package com.example.splitsmart;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.splitsmart.session.SessionManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.CoreMatchers.not;

/**
 * Black-box Espresso tests for Task 5 (Lukas Li): choosing the month on the
 * monthly summary screen. Month selection happens on the device, so these
 * tests do not need the backend.
 */
@RunWith(AndroidJUnit4.class)
public class MonthlySummaryScreenTest {

    private static final DateTimeFormatter MONTH =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US);

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

    private ActivityScenario<MonthlySummaryActivity> launchSummary() {
        Intent intent = new Intent(context, MonthlySummaryActivity.class);
        intent.putExtra(MonthlySummaryActivity.EXTRA_GROUP_ID, 10L);
        intent.putExtra(MonthlySummaryActivity.EXTRA_GROUP_NAME, "Demo group");
        return ActivityScenario.launch(intent);
    }

    @Test
    public void opensOnTheCurrentMonthAndBlocksFutureMonths() {
        try (ActivityScenario<MonthlySummaryActivity> ignored = launchSummary()) {
            onView(withId(R.id.tvSummaryMonth))
                    .check(matches(withText(MONTH.format(YearMonth.now()))));
            onView(withId(R.id.btnNextMonth)).check(matches(not(isEnabled())));
        }
    }

    @Test
    public void previousAndNextChangeTheSelectedMonth() {
        YearMonth now = YearMonth.now();
        try (ActivityScenario<MonthlySummaryActivity> ignored = launchSummary()) {
            onView(withId(R.id.btnPreviousMonth)).perform(scrollTo(), click());
            onView(withId(R.id.btnPreviousMonth)).perform(scrollTo(), click());
            onView(withId(R.id.tvSummaryMonth))
                    .check(matches(withText(MONTH.format(now.minusMonths(2)))));
            onView(withId(R.id.btnNextMonth)).check(matches(isEnabled()));

            onView(withId(R.id.btnNextMonth)).perform(scrollTo(), click());
            onView(withId(R.id.btnNextMonth)).perform(scrollTo(), click());
            onView(withId(R.id.tvSummaryMonth))
                    .check(matches(withText(MONTH.format(now))));
            onView(withId(R.id.btnNextMonth)).check(matches(not(isEnabled())));
        }
    }
}
