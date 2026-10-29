
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


public class MainActivity extends AppCompatActivity {

    private TextView connectionStatus;
    private Button loginButton;
    private Button registerButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        connectionStatus = findViewById(R.id.tvConnectionStatus);
        loginButton = findViewById(R.id.btnLogin);
        registerButton = findViewById(R.id.btnRegister);

        // Check connection to Spring Boot backend
        checkBackendConnection();

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
