
package com.example.splitsmart;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.splitsmart.model.LoginRequest;
import com.example.splitsmart.model.LoginResponse;
import com.example.splitsmart.network.RetrofitClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

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

        backButton.setOnClickListener(v -> finish());
        loginButton.setOnClickListener(v -> login());
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

                            // Temporary verification.
                            // Next we'll save the JWT using SessionManager.
                            errorText.setText(
                                    "Logged in as " + result.getName()
                            );
                            errorText.setTextColor(0xFF16A34A);

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
