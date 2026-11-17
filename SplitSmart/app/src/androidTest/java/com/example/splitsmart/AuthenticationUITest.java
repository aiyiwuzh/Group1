package com.example.splitsmart;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.core.app.ActivityScenario;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.containsString;

@RunWith(AndroidJUnit4.class)
public class AuthenticationUITest {
    // BB-22: Empty registration fields are rejected without a backend request.
    @Test public void emptyRegistrationFieldsAreRejected() {
        try (ActivityScenario<RegisterActivity> ignored = ActivityScenario.launch(RegisterActivity.class)) {
            onView(withId(R.id.btnSubmitRegister)).perform(click());
            onView(withId(R.id.tvRegisterStatus)).check(matches(withText("Please fill in all fields.")));
        }
    }

    // BB-23: A non-USC email is rejected locally.
    @Test public void invalidUscEmailIsRejected() {
        try (ActivityScenario<RegisterActivity> ignored = ActivityScenario.launch(RegisterActivity.class)) {
            onView(withId(R.id.etRegisterName)).perform(typeText("Test User"), closeSoftKeyboard());
            onView(withId(R.id.etRegisterEmail)).perform(typeText("test@gmail.com"), closeSoftKeyboard());
            onView(withId(R.id.etRegisterUscId)).perform(typeText("1234567890"), closeSoftKeyboard());
            onView(withId(R.id.etRegisterPassword)).perform(typeText("password123"), closeSoftKeyboard());
            onView(withId(R.id.btnSubmitRegister)).perform(click());
            onView(withId(R.id.tvRegisterStatus)).check(matches(withText("Please enter a valid USC email.")));
        }
    }

    // BB-24: A USC ID shorter than 10 digits is rejected locally.
    @Test public void shortUscIdIsRejected() {
        try (ActivityScenario<RegisterActivity> ignored = ActivityScenario.launch(RegisterActivity.class)) {
            onView(withId(R.id.etRegisterName)).perform(typeText("Test User"), closeSoftKeyboard());
            onView(withId(R.id.etRegisterEmail)).perform(typeText("test@usc.edu"), closeSoftKeyboard());
            onView(withId(R.id.etRegisterUscId)).perform(typeText("12345"), closeSoftKeyboard());
            onView(withId(R.id.etRegisterPassword)).perform(typeText("password123"), closeSoftKeyboard());
            onView(withId(R.id.btnSubmitRegister)).perform(click());
            onView(withId(R.id.tvRegisterStatus)).check(matches(withText("USC ID must be exactly 10 digits.")));
        }
    }

    // BB-25: Login requires both an email and a password.
    @Test public void emptyLoginFieldsAreRejected() {
        try (ActivityScenario<LoginActivity> ignored = ActivityScenario.launch(LoginActivity.class)) {
            onView(withId(R.id.btnSubmitLogin)).perform(click());
            onView(withId(R.id.tvLoginError)).check(matches(withText("Please enter your email and password.")));
        }
    }

    // BB-26: Login back button closes the screen without logging in.
    @Test public void loginBackButtonClosesScreen() {
        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class)) {
            onView(withId(R.id.btnBack)).perform(click());
            scenario.onActivity(activity -> org.junit.Assert.assertTrue(activity.isFinishing()));
        }
    }
}
