
package com.example.splitsmart;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.splitsmart.network.RetrofitClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import android.view.View;
import com.example.splitsmart.session.SessionManager;


public class MainActivity extends AppCompatActivity {

    private TextView connectionStatus;
    private Button loginButton;
    private Button registerButton;

    private Button logoutButton;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        connectionStatus = findViewById(R.id.tvConnectionStatus);
        loginButton = findViewById(R.id.btnLogin);
        registerButton = findViewById(R.id.btnRegister);
        sessionManager = new SessionManager(this);
        logoutButton = findViewById(R.id.btnLogout);

        logoutButton.setOnClickListener(v -> {
            sessionManager.logout();

            Toast.makeText(
                    this,
                    "Logged out successfully",
                    Toast.LENGTH_SHORT
            ).show();

            updateSessionUI();
        });

        updateSessionUI();

        // Open login screen
        loginButton.setOnClickListener(v -> {
            Intent intent = new Intent(
                    MainActivity.this,
                    LoginActivity.class
            );
            startActivity(intent);
        });

        // Registration screen will be implemented next
        registerButton.setOnClickListener(v -> {
            Intent intent = new Intent(
                    MainActivity.this,
                    RegisterActivity.class
            );
            startActivity(intent);
        });
    }
    @Override
    protected void onResume() {
        super.onResume();

        if (sessionManager != null) {
            updateSessionUI();
        }
    }
    private void updateSessionUI() {

        boolean loggedIn = sessionManager.isLoggedIn();

        loginButton.setVisibility(
                loggedIn ? View.GONE : View.VISIBLE
        );

        registerButton.setVisibility(
                loggedIn ? View.GONE : View.VISIBLE
        );

        logoutButton.setVisibility(
                loggedIn ? View.VISIBLE : View.GONE
        );

        if (loggedIn) {
            connectionStatus.setText(
                    "Welcome, " + sessionManager.getName()
            );
            connectionStatus.setTextColor(0xFF16A34A);
        } else {
            checkBackendConnection();
        }
    }
    private void checkBackendConnection() {

        RetrofitClient.getApiService()
                .checkHealth()
                .enqueue(new Callback<String>() {

                    @Override
                    public void onResponse(
                            Call<String> call,
                            Response<String> response) {

                        if (response.isSuccessful()) {
                            connectionStatus.setText(
                                    "Backend connected"
                            );
                            connectionStatus.setTextColor(
                                    0xFF16A34A
                            );
                        } else {
                            connectionStatus.setText(
                                    "Backend error: " + response.code()
                            );
                            connectionStatus.setTextColor(
                                    0xFFDC2626
                            );
                        }
                    }

                    @Override
                    public void onFailure(
                            Call<String> call,
                            Throwable t) {

                        connectionStatus.setText(
                                "Backend unavailable"
                        );
                        connectionStatus.setTextColor(
                                0xFFDC2626
                        );
                    }
                });
    }
}
