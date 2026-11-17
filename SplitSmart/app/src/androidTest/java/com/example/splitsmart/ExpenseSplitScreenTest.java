package com.example.splitsmart;

import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withSpinnerText;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.matcher.BoundedMatcher;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.splitsmart.model.Group;
import com.example.splitsmart.model.LoginRequest;
import com.example.splitsmart.model.LoginResponse;
import com.example.splitsmart.network.RetrofitClient;
import com.example.splitsmart.session.SessionManager;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.List;

import retrofit2.Response;

/**
 * Black-box Espresso tests for the Add Expense screen.
 *
 * BB-6 and BB-7 need no backend. BB-8, BB-9 and BB-10 load the group members from the server, so the
 * Docker backend must be running with the V9 demo data. They log in as the demo user Bob, open
 * the demo "Group 1" (Bob, Alice, Carol), and only press "Preview split", so they never save an
 * expense. If the backend cannot be used, they fail with the reason instead of passing silently.
 */
@RunWith(AndroidJUnit4.class)
public class ExpenseSplitScreenTest {

    private static final String DEMO_EMAIL = "bob@usc.edu";
    private static final String DEMO_PASSWORD = "password";
    private static final String DEMO_GROUP_NAME = "Group 1";
    private static final int DEMO_GROUP_MEMBERS = 3;
    private static final long WAIT_MS = 15000;

    private Context context;
    private SessionManager sessionManager;
    private Long demoGroupId;
    private String backendProblem = "backend was not checked";

    @Before
    public void createSession() {
        context = ApplicationProvider.getApplicationContext();
        sessionManager = new SessionManager(context);
        sessionManager.saveSession("espresso-test-token", "Test Member", 3600);
    }

    @After
    public void clearSession() {
        sessionManager.logout();
    }

    /** Logs in as the demo user and finds the demo group. Records why if that fails. */
    private void connectToDemoBackend() {
        try {
            Response<LoginResponse> login = RetrofitClient.getApiService()
                    .login(new LoginRequest(DEMO_EMAIL, DEMO_PASSWORD)).execute();
            if (!login.isSuccessful() || login.body() == null) {
                backendProblem = "demo login failed with HTTP " + login.code();
                return;
            }
            LoginResponse body = login.body();
            Response<List<Group>> groups = RetrofitClient.getApiService()
                    .getMyGroups("Bearer " + body.getToken()).execute();
            if (!groups.isSuccessful() || groups.body() == null) {
                backendProblem = "loading groups failed with HTTP " + groups.code();
                return;
            }
            StringBuilder seen = new StringBuilder();
            for (Group group : groups.body()) {
                int count = group.getMembers() == null ? -1 : group.getMembers().size();
                seen.append(group.getName()).append("=").append(count).append(" ");
                if (DEMO_GROUP_NAME.equals(group.getName()) && count == DEMO_GROUP_MEMBERS) {
                    demoGroupId = group.getId();
                    sessionManager.saveSession(
                            body.getToken(), body.getName(), body.getExpiresIn());
                    return;
                }
            }
            backendProblem = "no 'Group 1' with 3 members; the app saw: " + seen;
        } catch (IOException e) {
            backendProblem = "cannot reach the backend at 10.0.2.2:8080: " + e;
        }
        android.util.Log.w("BBTEST", backendProblem);
    }

    private void requireBackend() {
        connectToDemoBackend();
        if (demoGroupId == null) {
            throw new AssertionError("Backend not usable: " + backendProblem);
        }
    }

    private ActivityScenario<AddExpenseActivity> launchAddExpense() {
        Intent intent = new Intent(context, AddExpenseActivity.class);
        intent.putExtra(AddExpenseActivity.EXTRA_GROUP_ID,
                demoGroupId == null ? 0L : demoGroupId);
        return ActivityScenario.launch(intent);
    }

    // ---------- helpers ----------

    /** Retries a check until it passes or WAIT_MS runs out. The screen loads data asynchronously. */
    private static void waitUntil(int viewId, Matcher<View> condition) {
        long deadline = System.currentTimeMillis() + WAIT_MS;
        while (true) {
            try {
                onView(withId(viewId)).check(matches(condition));
                return;
            } catch (Throwable failure) {
                if (System.currentTimeMillis() > deadline) {
                    if (failure instanceof RuntimeException) throw (RuntimeException) failure;
                    if (failure instanceof Error) throw (Error) failure;
                    throw new AssertionError(failure);
                }
                android.os.SystemClock.sleep(250);
            }
        }
    }

    /** The Preview button turns on only after the group members and current user have loaded. */
    private static void waitForFormToLoad() {
        waitUntil(R.id.btnPreviewSplit, isEnabled());
    }

    private static void typeTitleAndAmount(String title, String amount) {
        onView(withId(R.id.etExpenseTitle)).perform(scrollTo(), replaceText(title));
        onView(withId(R.id.etExpenseAmount)).perform(scrollTo(), replaceText(amount));
        closeSoftKeyboard();
    }

    private static void chooseSplitMethod(String label) {
        onView(withId(R.id.spSplitMethod)).perform(scrollTo(), click());
        onData(allOf(is(instanceOf(String.class)), is(label))).perform(click());
    }

    private static void tapPreview() {
        onView(withId(R.id.btnPreviewSplit)).perform(scrollTo(), click());
    }

    /** Waits until the screen has finished its start-up calls, so a late message cannot overwrite ours. */
    private static void waitForScreenToSettle() {
        waitForFormToLoad();
        try {
            waitUntil(R.id.tvExpenseError, not(withText("")));
        } catch (Throwable noStartupMessage) {
            // nothing was shown at start-up, which is fine
        }
    }

    private static void checkError(String message) {
        onView(withId(R.id.tvExpenseError)).perform(scrollTo()).check(matches(withText(message)));
    }

    /** Puts one value into each person's split box, in the order the boxes are shown. */
    private static ViewAction setSplitInputs(final String... values) {
        return new ViewAction() {
            @Override public Matcher<View> getConstraints() {
                return isAssignableFrom(LinearLayout.class);
            }

            @Override public String getDescription() {
                return "type one value into each split input";
            }

            @Override public void perform(UiController uiController, View view) {
                LinearLayout rows = (LinearLayout) view;
                if (rows.getChildCount() != values.length) {
                    throw new AssertionError("Expected " + values.length
                            + " split boxes but found " + rows.getChildCount());
                }
                for (int i = 0; i < values.length; i++) {
                    LinearLayout row = (LinearLayout) rows.getChildAt(i);
                    ((EditText) row.getChildAt(1)).setText(values[i]);
                }
                uiController.loopMainThreadUntilIdle();
            }
        };
    }

    /** Matches a TextView whose text contains `needle` exactly `times` times. */
    private static Matcher<View> containsTimes(final String needle, final int times) {
        return new BoundedMatcher<View, TextView>(TextView.class) {
            @Override public void describeTo(Description description) {
                description.appendText("text containing '" + needle + "' " + times + " time(s)");
            }

            @Override protected boolean matchesSafely(TextView view) {
                String text = view.getText().toString();
                int count = 0;
                int from = 0;
                while ((from = text.indexOf(needle, from)) != -1) {
                    count++;
                    from += needle.length();
                }
                return count == times;
            }
        };
    }

    /** Matches a LinearLayout with exactly `count` child views. */
    private static Matcher<View> hasChildCount(final int count) {
        return new BoundedMatcher<View, LinearLayout>(LinearLayout.class) {
            @Override public void describeTo(Description description) {
                description.appendText("a layout with " + count + " child view(s)");
            }

            @Override protected boolean matchesSafely(LinearLayout view) {
                return view.getChildCount() == count;
            }
        };
    }

    private static void checkPreviewShown() {
        waitUntil(R.id.tvSplitPreview,
                withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE));
    }

    // ---------- tests ----------

    @Test
    public void splitMethodSelectorShowsGuidanceForEachMethod() {
        try (ActivityScenario<AddExpenseActivity> ignored = launchAddExpense()) {
            waitUntil(R.id.tvSplitHelp, withText(
                    "The total is divided evenly; any extra cents are distributed fairly."));

            chooseSplitMethod("Exact amounts");
            waitUntil(R.id.tvSplitHelp, withText(
                    "Enter each person's share. Shares must add up to the total."));

            chooseSplitMethod("Percentages");
            waitUntil(R.id.tvSplitHelp, withText(
                    "Enter each person's percentage. Percentages must add up to 100%."));

            chooseSplitMethod("Equal");
            waitUntil(R.id.tvSplitHelp, withText(
                    "The total is divided evenly; any extra cents are distributed fairly."));
        }
    }

    @Test
    public void expenseWithoutATitleIsRejected() {

        requireBackend();

        try (ActivityScenario<AddExpenseActivity> ignored =
                     launchAddExpense()) {

            waitForScreenToSettle();

            onView(withId(R.id.etExpenseAmount))
                    .perform(scrollTo(), replaceText("20.00"));

            closeSoftKeyboard();

            tapPreview();

            checkError("Please enter a title.");
        }
    }

    @Test
    public void exactSharesThatDoNotAddUpToTheTotalAreRejected() {
        requireBackend();
        try (ActivityScenario<AddExpenseActivity> ignored = launchAddExpense()) {
            waitForFormToLoad();
            typeTitleAndAmount("Espresso exact", "30.00");
            chooseSplitMethod("Exact amounts");
            onView(withId(R.id.layoutSplitValues))
                    .perform(scrollTo(), setSplitInputs("10.00", "10.00", "5.00"));
            closeSoftKeyboard();

            tapPreview();

            onView(withId(R.id.tvExpenseError)).perform(scrollTo())
                    .check(matches(withText("Exact shares must add up to $30.00.")));
        }
    }

    @Test
    public void exactMethodShowsOneBoxPerSelectedPerson() {
        requireBackend();
        try (ActivityScenario<AddExpenseActivity> ignored = launchAddExpense()) {
            waitForFormToLoad();

            chooseSplitMethod("Exact amounts");
            onView(withId(R.id.layoutSplitValues)).check(matches(hasChildCount(3)));

            onView(allOf(withText("Carol"), isAssignableFrom(CheckBox.class)))
                    .perform(scrollTo(), click());
            onView(withId(R.id.layoutSplitValues)).check(matches(hasChildCount(2)));

            chooseSplitMethod("Equal");
            onView(withId(R.id.layoutSplitValues)).check(matches(hasChildCount(0)));
        }
    }

    @Test
    public void payerStaysSelectedAndTheExpenseNeedsAtLeastTwoPeople() {
        requireBackend();
        try (ActivityScenario<AddExpenseActivity> ignored = launchAddExpense()) {
            waitForFormToLoad();
            Matcher<View> payerBox = allOf(withText("Bob (payer)"),
                    isAssignableFrom(CheckBox.class));
            onView(payerBox).perform(scrollTo())
                    .check(matches(allOf(isChecked(), not(isEnabled()))));

            onView(allOf(withText("Alice"), isAssignableFrom(CheckBox.class)))
                    .perform(scrollTo(), click());
            onView(allOf(withText("Carol"), isAssignableFrom(CheckBox.class)))
                    .perform(scrollTo(), click());
            typeTitleAndAmount("Espresso payer", "12.00");
            tapPreview();

            onView(withId(R.id.tvExpenseError)).perform(scrollTo()).check(matches(
                    withText("Choose at least 2 people, including the payer.")));
        }
    }
}
