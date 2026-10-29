
package com.example.splitsmart;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.splitsmart.model.RegisterRequest;
import com.example.splitsmart.model.RegisterResponse;
import com.example.splitsmart.network.RetrofitClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {

    private EditText nameInput, emailInput, uscIdInput, passwordInput;
    private TextView statusText;
    private Button registerButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        nameInput = findViewById(R.id.etRegisterName);
        emailInput = findViewById(R.id.etRegisterEmail);
        uscIdInput = findViewById(R.id.etRegisterUscId);
        passwordInput = findViewById(R.id.etRegisterPassword);
        statusText = findViewById(R.id.tvRegisterStatus);
        registerButton = findViewById(R.id.btnSubmitRegister);
        Button backButton = findViewById(R.id.btnBack);

        backButton.setOnClickListener(v -> finish());
        registerButton.setOnClickListener(v -> register());
    }

    private void register() {
        String name = nameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String uscId = uscIdInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        if (name.isEmpty() || email.isEmpty()
                || uscId.isEmpty() || password.isEmpty()) {
            statusText.setText("Please fill in all fields.");
            return;
        }

        if (!email.toLowerCase(java.util.Locale.ROOT)
                .matches("^[a-z0-9._%+-]+@usc\\.edu$")) {
            statusText.setText("Please enter a valid USC email.");
            return;
        }

        if (!uscId.matches("\\d{10}")) {
            statusText.setText("USC ID must be exactly 10 digits.");
            return;
        }

        registerButton.setEnabled(false);
        statusText.setText("");

        RegisterRequest request =
                new RegisterRequest(name, email, uscId, password);

        RetrofitClient.getApiService()
                .register(request)
                .enqueue(new Callback<RegisterResponse>() {

                    @Override
                    public void onResponse(
                            Call<RegisterResponse> call,
                            Response<RegisterResponse> response) {

                        registerButton.setEnabled(true);

                        if (response.code() == 201) {
                            android.widget.Toast.makeText(
                                    RegisterActivity.this,
                                    "Account created! Please log in.",
                                    android.widget.Toast.LENGTH_LONG
                            ).show();

                            startActivity(new Intent(
                                    RegisterActivity.this,
                                    LoginActivity.class
                            ));
                            finish();

                        } else if (response.code() == 409) {
                            statusText.setText(
                                    "Email or USC ID is already registered."
                            );

                        } else if (response.code() == 400) {
                            statusText.setText(
                                    "Please check your registration information."
                            );

                        } else {
                            statusText.setText(
                                    "Registration failed: HTTP " + response.code()
                            );
                        }
                    }

                    @Override
                    public void onFailure(
                            Call<RegisterResponse> call,
                            Throwable t) {

                        registerButton.setEnabled(true);
                        statusText.setText(
                                "Cannot connect to the server."
                        );
                    }
                });
    }
}
