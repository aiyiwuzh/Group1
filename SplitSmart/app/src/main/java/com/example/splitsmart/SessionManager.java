
package com.example.splitsmart.session;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {

    private static final String PREF_NAME = "splitsmart_session";
    private static final String KEY_TOKEN = "jwt_token";
    private static final String KEY_NAME = "user_name";
    private static final String KEY_EXPIRY = "token_expiry";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(
                        PREF_NAME,
                        Context.MODE_PRIVATE
                );
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

    public boolean isLoggedIn() {
        return getToken() != null;
    }

    public void logout() {
        prefs.edit().clear().apply();
    }
}
