
package com.example.splitsmart;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;

import androidx.appcompat.app.AppCompatActivity;

import com.example.splitsmart.model.LoginRequest;
import com.example.splitsmart.model.LoginResponse;
import com.example.splitsmart.network.RetrofitClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import android.widget.Toast;
import com.example.splitsmart.session.SessionManager;

public class LoginActivity extends AppCompatActivity {

    private EditText emailInput;
    private EditText passwordInput;
    private TextView errorText;
    private Button loginButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        emailInput = findViewById(R.id.etEmail);
        passwordInput = findViewById(R.id.etPassword);
        errorText = findViewById(R.id.tvLoginError);
        loginButton = findViewById(R.id.btnSubmitLogin);
        Button backButton = findViewById(R.id.btnBack);
        Button recentButton = findViewById(R.id.btnRecentAccounts);

        backButton.setOnClickListener(v -> finish());
        loginButton.setOnClickListener(v -> login());
        recentButton.setOnClickListener(v -> chooseRecentAccount());
        recentButton.setVisibility(new SessionManager(this).getRecentAccountEmails().isEmpty()
                ? android.view.View.GONE : android.view.View.VISIBLE);
    }

    private void chooseRecentAccount() {
        java.util.List<String> emails = new SessionManager(this).getRecentAccountEmails();
        if (emails.isEmpty()) return;
        String[] choices = emails.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Choose an account")
                .setItems(choices, (dialog, which) -> {
                    emailInput.setText(choices[which]);
                    passwordInput.setText("");
                    passwordInput.requestFocus();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void login() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        if (email.isEmpty() || password.isEmpty()) {
            errorText.setText("Please enter your email and password.");
            return;
        }

        loginButton.setEnabled(false);
        errorText.setText("");

        LoginRequest request = new LoginRequest(email, password);

        RetrofitClient.getApiService().login(request)
                .enqueue(new Callback<LoginResponse>() {
                    @Override
                    public void onResponse(
                            Call<LoginResponse> call,
                            Response<LoginResponse> response) {

                        loginButton.setEnabled(true);

                        if (response.isSuccessful()
                                && response.body() != null
                                && response.body().getToken() != null) {

                            LoginResponse result = response.body();

                            SessionManager sessionManager =
                                    new SessionManager(LoginActivity.this);

                            sessionManager.saveSession(
                                    result.getToken(),
                                    result.getName(),
                                    result.getExpiresIn()
                            );
                            sessionManager.rememberAccountEmail(email);

                            Toast.makeText(
                                    LoginActivity.this,
                                    "Welcome, " + result.getName(),
                                    Toast.LENGTH_SHORT
                            ).show();

                            Intent intent = new Intent(
                                    LoginActivity.this,
                                    MainActivity.class
                            );

                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

                            startActivity(intent);
                            finish();

                        } else if (response.code() == 401) {
                            errorText.setText("Invalid email or password.");
                        } else {
                            errorText.setText(
                                    "Login failed (HTTP " +
                                            response.code() + ")."
                            );
                        }
                    }

                    @Override
                    public void onFailure(
                            Call<LoginResponse> call,
                            Throwable t) {

                        loginButton.setEnabled(true);
                        errorText.setText(
                                "Cannot connect to the server."
                        );
                    }
                });
    }
}
