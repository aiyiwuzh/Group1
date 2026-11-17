package com.example.splitsmart;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Adapter;
import android.widget.ListView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.matcher.BoundedMatcher;
import androidx.test.espresso.matcher.ViewMatchers.Visibility;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.splitsmart.model.AddMemberRequest;
import com.example.splitsmart.model.CreateGroupRequest;
import com.example.splitsmart.model.Expense;
import com.example.splitsmart.model.ExpenseRequest;
import com.example.splitsmart.model.Group;
import com.example.splitsmart.model.LoginRequest;
import com.example.splitsmart.model.LoginResponse;
import com.example.splitsmart.model.RegisterRequest;
import com.example.splitsmart.network.ApiService;
import com.example.splitsmart.network.RetrofitClient;
import com.example.splitsmart.session.SessionManager;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;

import retrofit2.Call;
import retrofit2.Response;

/**
 * Black-box Espresso tests for the expense detail screen (Mo Shirmohammadi, BB-17 to BB-21):
 * who sees the creator-only actions, marking an expense settled, and deleting an expense.
 *
 * Needs the backend running (docker compose up -d). Like HistoryScreenTest, it registers two new
 * users and their own group through the API, so it never changes the demo seed data:
 *   Creator - creates every expense, so only they may edit, delete or mark it settled.
 *   Member  - in the same group, but did not create the expenses.
 * Each test makes its own $12.00 expense split equally between them and deletes it afterwards.
 * If the backend cannot be used, the tests fail with the reason instead of passing silently.
 */
@RunWith(AndroidJUnit4.class)
public class ExpenseDetailScreenTest {

    private static final String PASSWORD = "Espresso123!";
    private static final String AMOUNT = "12.00";
    private static final long WAIT_MS = 15000;

    private static ApiService api;
    private static LoginResponse creator;
    private static LoginResponse member;
    private static long groupId;
    private static long run;
    private static String backendProblem;

    private Context context;
    private SessionManager sessionManager;
    private final List<Long> expensesToDelete = new ArrayList<>();

    // ---------- test data ----------

    @BeforeClass
    public static void createUsersAndGroup() {
        api = RetrofitClient.getApiService();
        run = System.currentTimeMillis() % 1_000_000_000L;
        try {
            creator = registerAndLogIn("Expense Creator", 7);
            member = registerAndLogIn("Expense Member", 8);
            Group group = body(api.createGroup(auth(creator),
                    new CreateGroupRequest("Expense detail test " + run, null)), "create group");
            groupId = group.getId();
            body(api.addGroupMember(auth(creator), groupId,
                    new AddMemberRequest(member.getEmail())), "add the member to the group");
        } catch (IOException e) {
            backendProblem = "cannot reach the backend at 10.0.2.2:8080: " + e;
        } catch (AssertionError e) {
            backendProblem = e.getMessage();
        }
    }

    @AfterClass
    public static void deleteGroup() throws IOException {
        if (creator != null && groupId != 0) {
            api.deleteGroup(auth(creator), groupId).execute();
        }
    }

    private static LoginResponse registerAndLogIn(String name, int n) throws IOException {
        String email = "exp" + run + n + "@usc.edu";
        String uscId = String.format("%010d", (run * 10 + n) % 10_000_000_000L);
        // Registration only needs to have happened; login checks it worked.
        api.register(new RegisterRequest(name, email, uscId, PASSWORD)).execute();
        return body(api.login(new LoginRequest(email, PASSWORD)), "log in " + name);
    }

    private static String auth(LoginResponse user) {
        return "Bearer " + user.getToken();
    }

    private static <T> T body(Call<T> call, String step) throws IOException {
        Response<T> response = call.execute();
        if (!response.isSuccessful() || response.body() == null) {
            String error = response.errorBody() == null ? "" : response.errorBody().string();
            throw new AssertionError(step + " failed: HTTP " + response.code() + " " + error);
        }
        return response.body();
    }

    private void requireBackend() {
        if (backendProblem != null) {
            throw new AssertionError("Backend not usable: " + backendProblem);
        }
    }

    /** Creates an expense as the creator, paid by the creator and split equally with the member. */
    private Expense createExpense(String title) throws IOException {
        Expense expense = body(api.createExpense(auth(creator), groupId, new ExpenseRequest(
                title, new BigDecimal(AMOUNT), "FOOD", creator.getUserId(),
                Arrays.asList(creator.getUserId(), member.getUserId()), null, "EQUAL", null)),
                "create expense " + title);
        expensesToDelete.add(expense.getId());
        return expense;
    }

    private static Response<Expense> fetchExpense(long expenseId) throws IOException {
        return api.getExpense(auth(creator), expenseId).execute();
    }

    private String uniqueTitle(String what) {
        return "Espresso " + what + " " + run;
    }

    // ---------- session ----------

    @Before
    public void createSession() {
        context = ApplicationProvider.getApplicationContext();
        sessionManager = new SessionManager(context);
        sessionManager.saveSession("espresso-test-token", "Test Member", 3600);
        // Connect UiAutomation early so the system is already sending toast events to it.
        InstrumentationRegistry.getInstrumentation().getUiAutomation();
    }

    @After
    public void clearSessionAndExpenses() throws IOException {
        sessionManager.logout();
        if (creator != null) {
            for (long id : expensesToDelete) {
                api.deleteExpense(auth(creator), id).execute(); // 404 if the test deleted it
            }
        }
    }

    private void logInAs(LoginResponse user) {
        sessionManager.saveSession(user.getToken(), user.getName(), user.getExpiresIn());
    }

    private ActivityScenario<ExpenseDetailActivity> launchDetail(long expenseId) {
        Intent intent = new Intent(context, ExpenseDetailActivity.class);
        intent.putExtra(ExpenseDetailActivity.EXTRA_EXPENSE_ID, expenseId);
        return ActivityScenario.launch(intent);
    }

    private ActivityScenario<ExpenseListActivity> launchGroupExpenses() {
        Intent intent = new Intent(context, ExpenseListActivity.class);
        intent.putExtra(ExpenseListActivity.EXTRA_GROUP_ID, groupId);
        intent.putExtra(ExpenseListActivity.EXTRA_GROUP_NAME, "Expense detail test");
        return ActivityScenario.launch(intent);
    }

    // ---------- helpers ----------

    /** Retries a check until it passes or WAIT_MS runs out. The screens load data asynchronously. */
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
                SystemClock.sleep(250);
            }
        }
    }

    /**
     * The action buttons are hidden in the layout until the expense loads, so a "hidden" check
     * only means something after the title has appeared. The title and the buttons are set in
     * the same main-thread call, so once the title shows, the buttons are final too.
     */
    private static void waitForExpenseToLoad(String title) {
        waitUntil(R.id.tvDetailTitle, withText(title));
    }

    private static Matcher<View> shown() {
        return withEffectiveVisibility(Visibility.VISIBLE);
    }

    private static Matcher<View> hidden() {
        return withEffectiveVisibility(Visibility.GONE);
    }

    private static void tapMarkSettledAndSeeTheDialog() {
        onView(withId(R.id.btnMarkSettledExpense)).perform(scrollTo(), click());
        onView(withText("Mark expense as settled?")).inRoot(isDialog())
                .check(matches(isDisplayed()));
    }

    /**
     * Runs an action and waits for a toast with the given text. On Android 11+ toasts are drawn
     * by the system, outside the app's window, so Espresso cannot see them; the system still
     * reports every toast as an accessibility event, which UiAutomation can catch.
     */
    private static void doAndExpectToast(String text, Runnable action) {
        UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            automation.executeAndWaitForEvent(action, event ->
                    event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
                            && event.getText().toString().contains(text), WAIT_MS);
        } catch (TimeoutException e) {
            throw new AssertionError("No toast saying \"" + text + "\" within " + WAIT_MS + " ms");
        }
    }

    /** Matches an Expense row in an adapter by its title. */
    private static Matcher<Object> expenseTitled(final String title) {
        return new BoundedMatcher<Object, Expense>(Expense.class) {
            @Override public void describeTo(Description description) {
                description.appendText("an expense titled '" + title + "'");
            }

            @Override protected boolean matchesSafely(Expense expense) {
                return title.equals(expense.getTitle());
            }
        };
    }

    /** Matches a ListView whose adapter holds an expense with this title. */
    private static Matcher<View> listsExpense(final String title) {
        return new BoundedMatcher<View, ListView>(ListView.class) {
            @Override public void describeTo(Description description) {
                description.appendText("a list containing the expense '" + title + "'");
            }

            @Override protected boolean matchesSafely(ListView list) {
                Adapter adapter = list.getAdapter();
                for (int i = 0; adapter != null && i < adapter.getCount(); i++) {
                    Object item = adapter.getItem(i);
                    if (item instanceof Expense && title.equals(((Expense) item).getTitle())) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    // ---------- tests ----------

    /** BB-17: the creator of an expense gets Edit, Delete and Mark settled. */
    @Test
    public void creatorSeesEditDeleteAndMarkSettledButtons() throws IOException {
        requireBackend();
        String title = uniqueTitle("creator view");
        Expense expense = createExpense(title);
        logInAs(creator);

        try (ActivityScenario<ExpenseDetailActivity> ignored = launchDetail(expense.getId())) {
            waitForExpenseToLoad(title);

            onView(withId(R.id.btnEditExpense)).check(matches(shown()));
            onView(withId(R.id.btnDeleteExpense)).check(matches(shown()));
            onView(withId(R.id.btnMarkSettledExpense)).check(matches(shown()));
        }
    }

    /** BB-18: another group member can view the same expense but gets none of those buttons. */
    @Test
    public void otherMemberDoesNotSeeCreatorOnlyButtons() throws IOException {
        requireBackend();
        String title = uniqueTitle("member view");
        Expense expense = createExpense(title);
        logInAs(member);

        try (ActivityScenario<ExpenseDetailActivity> ignored = launchDetail(expense.getId())) {
            waitForExpenseToLoad(title);
            onView(withId(R.id.tvDetailAmount)).check(matches(withText("$" + AMOUNT)));

            onView(withId(R.id.btnEditExpense)).check(matches(hidden()));
            onView(withId(R.id.btnDeleteExpense)).check(matches(hidden()));
            onView(withId(R.id.btnMarkSettledExpense)).check(matches(hidden()));
        }
    }

    /** BB-19: confirming Mark settled shows the toast and the settled label, and hides the button. */
    @Test
    public void confirmingMarkSettledShowsSettledLabelAndHidesTheButton() throws IOException {
        requireBackend();
        String title = uniqueTitle("mark settled");
        Expense expense = createExpense(title);
        logInAs(creator);

        try (ActivityScenario<ExpenseDetailActivity> ignored = launchDetail(expense.getId())) {
            waitForExpenseToLoad(title);
            onView(withId(R.id.tvDetailLocked)).check(matches(hidden()));

            tapMarkSettledAndSeeTheDialog();
            doAndExpectToast("Expense marked as settled", () ->
                    onView(withText("Mark settled")).inRoot(isDialog()).perform(click()));

            waitUntil(R.id.tvDetailLocked, shown());
            onView(withId(R.id.tvDetailLocked))
                    .check(matches(withText(startsWith("Marked settled."))));
            onView(withId(R.id.btnMarkSettledExpense)).check(matches(hidden()));
            // Settled is a label only, so the creator can still edit or delete.
            onView(withId(R.id.btnEditExpense)).check(matches(shown()));
            onView(withId(R.id.btnDeleteExpense)).check(matches(shown()));
        }

        assertTrue("the server should now say the expense is settled",
                fetchExpense(expense.getId()).body().isSettled());
    }

    /** BB-20: cancelling the Mark settled dialog changes nothing. */
    @Test
    public void cancellingMarkSettledLeavesTheExpenseUnsettled() throws IOException {
        requireBackend();
        String title = uniqueTitle("cancel settle");
        Expense expense = createExpense(title);
        logInAs(creator);

        try (ActivityScenario<ExpenseDetailActivity> ignored = launchDetail(expense.getId())) {
            waitForExpenseToLoad(title);

            tapMarkSettledAndSeeTheDialog();
            onView(withText("Cancel")).inRoot(isDialog()).perform(click());

            onView(withText("Mark expense as settled?")).check(doesNotExist());
            // Give any request that should not have been sent time to land before checking.
            SystemClock.sleep(2000);
            onView(withId(R.id.tvDetailLocked)).check(matches(hidden()));
            onView(withId(R.id.btnMarkSettledExpense)).check(matches(shown()));
        }

        assertFalse("the server should still say the expense is not settled",
                fetchExpense(expense.getId()).body().isSettled());
    }

    /** BB-21: deleting an expense from its detail screen removes it from the group's list. */
    @Test
    public void deletingAnExpenseRemovesItFromTheGroupList() throws IOException {
        requireBackend();
        String title = uniqueTitle("delete me");
        Expense expense = createExpense(title);
        logInAs(creator);

        try (ActivityScenario<ExpenseListActivity> ignored = launchGroupExpenses()) {
            waitUntil(R.id.listExpenses, listsExpense(title));

            onData(expenseTitled(title)).inAdapterView(withId(R.id.listExpenses))
                    .perform(click());
            waitForExpenseToLoad(title);

            onView(withId(R.id.btnDeleteExpense)).perform(scrollTo(), click());
            onView(withText("Delete expense?")).inRoot(isDialog())
                    .check(matches(isDisplayed()));
            onView(withText("Delete")).inRoot(isDialog()).perform(click());

            // The detail screen closes and the list reloads without the expense.
            waitUntil(R.id.listExpenses, not(listsExpense(title)));
        }

        assertEquals("the server should no longer have the expense",
                404, fetchExpense(expense.getId()).code());
    }
}
