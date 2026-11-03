
package com.example.splitsmart;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

import com.example.splitsmart.model.Member;
import com.example.splitsmart.session.SessionManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import retrofit2.Response;

// Shared helpers for the expense screens.
public final class ExpenseUtils {

    // Server values and the labels shown to users, in the same order.
    public static final String[] CATEGORY_VALUES = {
            "FOOD", "HOUSING", "TRANSPORTATION", "ENTERTAINMENT", "OTHER"
    };

    public static final String[] CATEGORY_LABELS = {
            "Food", "Housing", "Transportation", "Entertainment", "Other"
    };

    public static final int DEFAULT_CATEGORY_INDEX = 4;

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a", Locale.US)
                    .withZone(ZoneId.systemDefault());

    private ExpenseUtils() {
    }

    public static int categoryIndex(String value) {
        for (int i = 0; i < CATEGORY_VALUES.length; i++) {
            if (CATEGORY_VALUES[i].equals(value)) {
                return i;
            }
        }
        return DEFAULT_CATEGORY_INDEX;
    }

    public static String categoryLabel(String value) {
        return CATEGORY_LABELS[categoryIndex(value)];
    }

    public static String formatMoney(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(Locale.US).format(amount);
    }

    public static String formatDate(String isoInstant) {
        try {
            return DATE_FORMAT.format(Instant.parse(isoInstant));
        } catch (Exception e) {
            return isoInstant;
        }
    }

    public static String joinNames(List<Member> members) {
        StringBuilder names = new StringBuilder();
        for (Member member : members) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(member.getName());
        }
        return names.toString();
    }

    // Returns "Bearer <token>", or sends the user to log in and returns null.
    public static String requireAuth(Activity activity) {
        String token = new SessionManager(activity).getToken();

        if (token == null) {
            Toast.makeText(activity, "Please log in again.",
                    Toast.LENGTH_SHORT).show();
            activity.startActivity(new Intent(activity, LoginActivity.class));
            activity.finish();
            return null;
        }

        return "Bearer " + token;
    }

    // Reads {"error": "..."} from a failed response.
    public static String errorMessage(Response<?> response, String fallback) {
        if (response.code() == 401) {
            return "Your session expired. Please log in again.";
        }

        try {
            if (response.errorBody() != null) {
                JsonObject body = JsonParser
                        .parseString(response.errorBody().string())
                        .getAsJsonObject();

                if (body.has("error")) {
                    return body.get("error").getAsString();
                }
            }
        } catch (Exception ignored) {
            // Fall through to the generic message
        }

        return fallback + " (HTTP " + response.code() + ")";
    }
}
