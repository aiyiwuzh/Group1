package com.example.splitsmart;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.matcher.ViewMatchers.Visibility;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.splitsmart.model.AddMemberRequest;
import com.example.splitsmart.model.CreateGroupRequest;
import com.example.splitsmart.model.ExpenseRequest;
import com.example.splitsmart.model.Group;
import com.example.splitsmart.model.LoginRequest;
import com.example.splitsmart.model.LoginResponse;
import com.example.splitsmart.model.RegisterRequest;
import com.example.splitsmart.model.SettlementRequest;
import com.example.splitsmart.network.ApiService;
import com.example.splitsmart.network.RetrofitClient;
import com.example.splitsmart.session.SessionManager;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;

import retrofit2.Call;
import retrofit2.Response;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.CoreMatchers.anything;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.startsWith;

/**
 * Black-box Espresso tests for Task 5 (Lukas Li): the history list, search,
 * type filter and monthly totals, shown with real data from the backend.
 *
 * Needs the backend running (docker compose up -d). Before the tests, it
 * registers two new users and creates their own group through the API, so the
 * expected results never depend on the demo seed data:
 *   1. "Groceries"      $30.00 FOOD, paid by Alice, split with Bob
 *   2. "Uber to campus" $20.00 TRANSPORTATION, paid by Bob, split with Alice
 *   3. Settlement: Bob pays Alice $5.00, note "Venmo for groceries"
 * Bob owes Alice $15.00 and Alice owes Bob $10.00, so the $5.00 settlement
 * clears the $5.00 net debt exactly.
 */
@RunWith(AndroidJUnit4.class)
public class HistoryScreenTest {

    private static final String PASSWORD = "Espresso123!";
    private static final long WAIT_MS = 15_000;

    private static String aliceToken;
    private static long groupId;

    private Context context;
    private SessionManager sessionManager;

    // ------------------------------------------------------------------ test data

    @BeforeClass
    public static void createGroupWithExpensesAndSettlement() throws IOException {
        ApiService api = RetrofitClient.getApiService();
        long run = System.currentTimeMillis() % 1_000_000_000L;

        LoginResponse alice = registerAndLogIn(api, "Alice History", run, 1);
        LoginResponse bob = registerAndLogIn(api, "Bob History", run, 2);
        aliceToken = "Bearer " + alice.getToken();
        String bobToken = "Bearer " + bob.getToken();

        Group group = body(api.createGroup(aliceToken,
                new CreateGroupRequest("History test " + run, null)), "create group");
        groupId = group.getId();
        body(api.addGroupMember(aliceToken, groupId, new AddMemberRequest(bob.getEmail())),
                "add Bob to the group");

        // Created one after another, so the settlement is the newest row.
        body(api.createExpense(aliceToken, groupId, new ExpenseRequest(
                "Groceries", new BigDecimal("30.00"), "FOOD", alice.getUserId(),
                Arrays.asList(alice.getUserId(), bob.getUserId()), null, "EQUAL", null)),
                "create groceries");
        body(api.createExpense(aliceToken, groupId, new ExpenseRequest(
                "Uber to campus", new BigDecimal("20.00"), "TRANSPORTATION", bob.getUserId(),
                Arrays.asList(alice.getUserId(), bob.getUserId()), null, "EQUAL", null)),
                "create uber");
        body(api.createSettlement(bobToken, groupId, new SettlementRequest(
                bob.getUserId(), alice.getUserId(), new BigDecimal("5.00"),
                "Venmo for groceries")), "record settlement");
    }

    private static LoginResponse registerAndLogIn(ApiService api, String name, long run, int n)
            throws IOException {
        String email = "hist" + run + n + "@usc.edu";
        String uscId = String.format("%010d", (run * 10 + n) % 10_000_000_000L);
        // Registration only needs to have happened; login checks it worked.
        api.register(new RegisterRequest(name, email, uscId, PASSWORD)).execute();
        return body(api.login(new LoginRequest(email, PASSWORD)), "log in " + name);
    }

    private static <T> T body(Call<T> call, String step) throws IOException {
        Response<T> response;
        try {
            response = call.execute();
        } catch (IOException e) {
            throw new IOException("Could not reach the backend at 10.0.2.2:8080 while trying to "
                    + step + ". Start it with docker compose up -d.", e);
        }
        if (!response.isSuccessful() || response.body() == null) {
            String error = response.errorBody() == null ? "" : response.errorBody().string();
            throw new AssertionError(step + " failed: HTTP " + response.code() + " " + error);
        }
        return response.body();
    }

    // ------------------------------------------------------------------ helpers

    @Before
    public void logInAsAlice() {
        context = ApplicationProvider.getApplicationContext();
        sessionManager = new SessionManager(context);
        sessionManager.saveSession(aliceToken.substring("Bearer ".length()),
                "Alice History", 3600);
    }

    @After
    public void logOut() {
        sessionManager.logout();
    }

    private ActivityScenario<HistoryActivity> launchHistory() {
        Intent intent = new Intent(context, HistoryActivity.class);
        intent.putExtra(HistoryActivity.EXTRA_GROUP_ID, groupId);
        intent.putExtra(HistoryActivity.EXTRA_GROUP_NAME, "History test");
        return ActivityScenario.launch(intent);
    }

    // The screens load from the network; retry an assertion until it passes.
    private static void eventually(Runnable assertion) {
        long deadline = SystemClock.uptimeMillis() + WAIT_MS;
        while (true) {
            try {
                assertion.run();
                return;
            } catch (RuntimeException | AssertionError e) {
                if (SystemClock.uptimeMillis() > deadline) throw e;
                SystemClock.sleep(250);
            }
        }
    }

    private static void checkRow(int position, int viewId, org.hamcrest.Matcher<String> text) {
        onData(anything()).inAdapterView(withId(R.id.listHistory)).atPosition(position)
                .onChildView(withId(viewId)).check(matches(withText(text)));
    }

    // ------------------------------------------------------------------ tests

    @Test
    public void historyListsExpensesAndSettlementNewestFirst() {
        try (ActivityScenario<HistoryActivity> ignored = launchHistory()) {
            eventually(() -> onView(withId(R.id.tvHistoryCount))
                    .check(matches(withText("3 transactions"))));

            checkRow(0, R.id.tvHistoryType, startsWith("SETTLEMENT"));
            checkRow(0, R.id.tvHistoryTitle, startsWith("Bob History paid Alice History"));
            checkRow(0, R.id.tvHistoryAmount, startsWith("$5.00"));
            checkRow(1, R.id.tvHistoryTitle, startsWith("Uber to campus"));
            checkRow(1, R.id.tvHistoryMeta, containsString("Paid by Bob History"));
            checkRow(2, R.id.tvHistoryTitle, startsWith("Groceries"));
            checkRow(2, R.id.tvHistoryType, startsWith("EXPENSE"));
            // Dates are shown as YYYY-MM-DD in the phone's time zone; the data was made today.
            checkRow(2, R.id.tvHistoryMeta, containsString("\n" + LocalDate.now()));
        }
    }

    @Test
    public void keywordSearchIsCaseSensitive() {
        try (ActivityScenario<HistoryActivity> ignored = launchHistory()) {
            eventually(() -> onView(withId(R.id.tvHistoryCount))
                    .check(matches(withText("3 transactions"))));

            onView(withId(R.id.etHistorySearch)).perform(replaceText("Uber"));
            Espresso.closeSoftKeyboard();
            eventually(() -> onView(withId(R.id.tvHistoryCount))
                    .check(matches(withText("1 transaction"))));
            checkRow(0, R.id.tvHistoryTitle, startsWith("Uber to campus"));

            onView(withId(R.id.etHistorySearch)).perform(replaceText("uber"));
            Espresso.closeSoftKeyboard();
            eventually(() -> onView(withId(R.id.tvHistoryEmpty))
                    .check(matches(isDisplayed()))
                    .check(matches(withText("Nothing matches these filters."))));
        }
    }

    @Test
    public void settlementsChipShowsOnlyTheSettlementWithItsNote() {
        try (ActivityScenario<HistoryActivity> ignored = launchHistory()) {
            eventually(() -> onView(withId(R.id.tvHistoryCount))
                    .check(matches(withText("3 transactions"))));

            onView(withId(R.id.chipHistorySettlements)).perform(click());
            eventually(() -> onView(withId(R.id.tvHistoryCount))
                    .check(matches(withText("1 transaction"))));
            checkRow(0, R.id.tvHistoryType, startsWith("SETTLEMENT"));

            // Opening the row shows the settlement's details, including its note.
            onData(anything()).inAdapterView(withId(R.id.listHistory)).atPosition(0)
                    .perform(click());
            onView(withText(containsString("Note: Venmo for groceries")))
                    .check(matches(isDisplayed()));
            onView(withText("Close")).perform(click());
        }
    }

    @Test
    public void monthlySummaryCountsExpensesAndExcludesTheSettlement() {
        Intent intent = new Intent(context, MonthlySummaryActivity.class);
        intent.putExtra(MonthlySummaryActivity.EXTRA_GROUP_ID, groupId);
        intent.putExtra(MonthlySummaryActivity.EXTRA_GROUP_NAME, "History test");
        try (ActivityScenario<MonthlySummaryActivity> ignored = ActivityScenario.launch(intent)) {
            // $30.00 Food + $20.00 Transportation; the $5.00 settlement is not counted.
            eventually(() -> onView(withId(R.id.tvSummaryTotal))
                    .check(matches(withText("$50.00"))));
            onView(withId(R.id.tvSummaryCounts))
                    .check(matches(withText(startsWith("2 expenses"))));
            onView(withId(R.id.tvSummaryCounts))
                    .check(matches(withText(containsString("Your share $25.00"))));
            // One row per category; the rows may be below the fold on a small screen.
            onView(withText("Food")).check(matches(withEffectiveVisibility(Visibility.VISIBLE)));
            onView(withText("Transportation"))
                    .check(matches(withEffectiveVisibility(Visibility.VISIBLE)));
        }
    }
}
