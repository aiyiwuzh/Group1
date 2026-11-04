package com.example.splitsmart;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.splitsmart.model.CurrentUser;
import com.example.splitsmart.model.ProfileUpdateRequest;
import com.example.splitsmart.network.RetrofitClient;
import com.example.splitsmart.session.SessionManager;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileActivity extends AppCompatActivity {
    private String auth;
    private EditText nameInput;
    private TextView emailText;
    private TextView uscIdText;
    private TextView errorText;
    private Button saveButton;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        auth = ExpenseUtils.requireAuth(this);
        if (auth == null) return;

        nameInput = findViewById(R.id.etProfileName);
        emailText = findViewById(R.id.tvProfileEmail);
        uscIdText = findViewById(R.id.tvProfileUscId);
        errorText = findViewById(R.id.tvProfileError);
        saveButton = findViewById(R.id.btnSaveProfile);
        findViewById(R.id.btnProfileBack).setOnClickListener(v -> finish());
        saveButton.setOnClickListener(v -> saveProfile());
        loadProfile();
    }

    private void loadProfile() {
        saveButton.setEnabled(false);
        RetrofitClient.getApiService().getCurrentUser(auth).enqueue(new Callback<CurrentUser>() {
            @Override public void onResponse(Call<CurrentUser> call, Response<CurrentUser> response) {
                saveButton.setEnabled(true);
                CurrentUser profile = response.body();
                if (!response.isSuccessful() || profile == null) {
                    showError(ExpenseUtils.errorMessage(response, "Could not load your profile"));
                    return;
                }
                nameInput.setText(profile.getName());
                emailText.setText(profile.getEmail());
                uscIdText.setText(profile.getUscId());
            }
            @Override public void onFailure(Call<CurrentUser> call, Throwable t) {
                saveButton.setEnabled(true);
                showError("Cannot connect to the server.");
            }
        });
    }

    private void saveProfile() {
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) {
            nameInput.setError("Enter your name");
            return;
        }
        saveButton.setEnabled(false);
        errorText.setVisibility(View.GONE);
        RetrofitClient.getApiService().updateCurrentUser(auth, new ProfileUpdateRequest(name))
                .enqueue(new Callback<CurrentUser>() {
                    @Override public void onResponse(Call<CurrentUser> call, Response<CurrentUser> response) {
                        saveButton.setEnabled(true);
                        if (response.isSuccessful() && response.body() != null) {
                            new SessionManager(ProfileActivity.this).updateName(response.body().getName());
                            Toast.makeText(ProfileActivity.this, "Profile updated", Toast.LENGTH_SHORT).show();
                            finish();
                        } else showError(ExpenseUtils.errorMessage(response, "Could not update your profile"));
                    }
                    @Override public void onFailure(Call<CurrentUser> call, Throwable t) {
                        saveButton.setEnabled(true);
                        showError("Cannot connect to the server.");
                    }
                });
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }
}
