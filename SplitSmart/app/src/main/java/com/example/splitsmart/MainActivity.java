
package com.example.splitsmart;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.splitsmart.model.Group;
import com.example.splitsmart.network.RetrofitClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import android.view.View;
import androidx.appcompat.app.AlertDialog;
import com.example.splitsmart.session.SessionManager;

import java.util.List;


public class MainActivity extends AppCompatActivity {

    private TextView connectionStatus;
    private Button loginButton;
    private Button registerButton;

    private Button logoutButton;
    private Button groupsButton;
    private Button createGroupButton;
    private Button profileButton;
    private Button switchUserButton;
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
        groupsButton = findViewById(R.id.btnGroups);
        createGroupButton = findViewById(R.id.btnCreateGroup);
        profileButton = findViewById(R.id.btnProfile);
        switchUserButton = findViewById(R.id.btnSwitchUser);

        groupsButton.setOnClickListener(v -> chooseGroup());
        createGroupButton.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this,
                        CreateGroupActivity.class)));
        profileButton.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, ProfileActivity.class)));

        switchUserButton.setOnClickListener(v -> {
            sessionManager.logout();
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

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

        groupsButton.setVisibility(
                loggedIn ? View.VISIBLE : View.GONE
        );

        createGroupButton.setVisibility(
                loggedIn ? View.VISIBLE : View.GONE
        );
        profileButton.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        switchUserButton.setVisibility(loggedIn ? View.VISIBLE : View.GONE);

        if (loggedIn) {
            connectionStatus.setText(
                    "Welcome, " + sessionManager.getName()
            );
            connectionStatus.setTextColor(0xFF16A34A);
        } else {
            checkBackendConnection();
        }
    }
    // Pick a group, then open its expenses
    private void chooseGroup() {
        String auth = ExpenseUtils.requireAuth(this);
        if (auth == null) {
            return;
        }

        RetrofitClient.getApiService()
                .getMyGroups(auth)
                .enqueue(new Callback<List<Group>>() {

                    @Override
                    public void onResponse(
                            Call<List<Group>> call,
                            Response<List<Group>> response) {

                        if (!response.isSuccessful() || response.body() == null) {
                            Toast.makeText(MainActivity.this,
                                    ExpenseUtils.errorMessage(
                                            response, "Could not load groups"),
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        List<Group> groups = response.body();

                        if (groups.isEmpty()) {
                            Toast.makeText(MainActivity.this,
                                    "You're not in any groups yet.",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        String[] names = new String[groups.size()];
                        for (int i = 0; i < groups.size(); i++) {
                            names[i] = groups.get(i).getName();
                        }

                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Choose a group")
                                .setItems(names, (dialog, which) -> {
                                    Intent intent = new Intent(
                                            MainActivity.this,
                                            ExpenseListActivity.class);
                                    intent.putExtra(
                                            ExpenseListActivity.EXTRA_GROUP_ID,
                                            groups.get(which).getId());
                                    intent.putExtra(
                                            ExpenseListActivity.EXTRA_GROUP_NAME,
                                            groups.get(which).getName());
                                    startActivity(intent);
                                })
                                .show();
                    }

                    @Override
                    public void onFailure(Call<List<Group>> call, Throwable t) {
                        Toast.makeText(MainActivity.this,
                                "Cannot connect to the server.",
                                Toast.LENGTH_SHORT).show();
                    }
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
