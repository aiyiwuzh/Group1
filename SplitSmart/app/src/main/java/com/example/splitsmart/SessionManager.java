
package com.example.splitsmart.session;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public class SessionManager {

    private static final String PREF_NAME = "splitsmart_session";
    private static final String KEY_TOKEN = "jwt_token";
    private static final String KEY_NAME = "user_name";
    private static final String KEY_EXPIRY = "token_expiry";
    // Account hints live separately so logging out clears credentials but keeps
    // the email addresses that make switching accounts quicker.
    private static final String ACCOUNT_PREF_NAME = "splitsmart_recent_accounts";
    private static final String KEY_RECENT_EMAIL_PREFIX = "email_";

    private final SharedPreferences prefs;
    private final SharedPreferences accountPrefs;

    public SessionManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(
                        PREF_NAME,
                        Context.MODE_PRIVATE
                );
        accountPrefs = context.getApplicationContext()
                .getSharedPreferences(ACCOUNT_PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveSession(
            String token, String name, long expiresIn) {

        long expiry = System.currentTimeMillis()
                + expiresIn * 1000L;

        prefs.edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_NAME, name)
                .putLong(KEY_EXPIRY, expiry)
                .apply();
    }

    public String getToken() {
        String token = prefs.getString(KEY_TOKEN, null);
        long expiry = prefs.getLong(KEY_EXPIRY, 0);

        if (token == null || System.currentTimeMillis() >= expiry) {
            logout();
            return null;
        }

        return token;
    }

    public String getName() {
        return prefs.getString(KEY_NAME, "");
    }

    public void updateName(String name) {
        prefs.edit().putString(KEY_NAME, name).apply();
    }

    public boolean isLoggedIn() {
        return getToken() != null;
    }

    public void logout() {
        prefs.edit().clear().apply();
    }

    public void rememberAccountEmail(String email) {
        if (email == null || email.trim().isEmpty()) return;
        String normalized = email.trim();
        List<String> recent = new ArrayList<>();
        recent.add(normalized);
        for (String saved : getRecentAccountEmails()) {
            if (!saved.equalsIgnoreCase(normalized) && recent.size() < 5) {
                recent.add(saved);
            }
        }
        SharedPreferences.Editor editor = accountPrefs.edit().clear();
        for (int i = 0; i < recent.size(); i++) {
            editor.putString(KEY_RECENT_EMAIL_PREFIX + i, recent.get(i));
        }
        editor.apply();
    }

    public List<String> getRecentAccountEmails() {
        List<String> emails = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String email = accountPrefs.getString(KEY_RECENT_EMAIL_PREFIX + i, null);
            if (email != null && !email.isEmpty()) emails.add(email);
        }
        return emails;
    }
}
