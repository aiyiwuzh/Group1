package com.example.splitsmart;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.splitsmart.model.CreateGroupRequest;
import com.example.splitsmart.model.Group;
import com.example.splitsmart.network.RetrofitClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CreateGroupActivity extends AppCompatActivity {

    private EditText nameInput;
    private EditText descriptionInput;
    private TextView statusText;
    private Button createButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_group);

        nameInput = findViewById(R.id.etGroupName);
        descriptionInput = findViewById(R.id.etGroupDescription);
        statusText = findViewById(R.id.tvCreateGroupStatus);
        createButton = findViewById(R.id.btnSubmitCreateGroup);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        createButton.setOnClickListener(v -> createGroup());
    }

    private void createGroup() {
        String name = nameInput.getText().toString().trim();
        String description = descriptionInput.getText().toString().trim();

        if (name.isEmpty()) {
            nameInput.setError("Enter a group name");
            nameInput.requestFocus();
            return;
        }

        String auth = ExpenseUtils.requireAuth(this);
        if (auth == null) {
            return;
        }

        createButton.setEnabled(false);
        statusText.setTextColor(getColor(R.color.text_secondary));
        statusText.setText("Creating group...");

        CreateGroupRequest request = new CreateGroupRequest(
                name,
                description.isEmpty() ? null : description);

        RetrofitClient.getApiService().createGroup(auth, request)
                .enqueue(new Callback<Group>() {
                    @Override
                    public void onResponse(
                            Call<Group> call,
                            Response<Group> response) {
                        createButton.setEnabled(true);

                        Group group = response.body();
                        if (!response.isSuccessful() || group == null
                                || group.getId() == null) {
                            statusText.setTextColor(getColor(R.color.error));
                            statusText.setText(ExpenseUtils.errorMessage(
                                    response, "Could not create group"));
                            return;
                        }

                        Toast.makeText(CreateGroupActivity.this,
                                "Group created", Toast.LENGTH_SHORT).show();

                        Intent intent = new Intent(
                                CreateGroupActivity.this,
                                ExpenseListActivity.class);
                        intent.putExtra(
                                ExpenseListActivity.EXTRA_GROUP_ID,
                                group.getId());
                        intent.putExtra(
                                ExpenseListActivity.EXTRA_GROUP_NAME,
                                group.getName());
                        startActivity(intent);
                        finish();
                    }

                    @Override
                    public void onFailure(Call<Group> call, Throwable t) {
                        createButton.setEnabled(true);
                        statusText.setTextColor(getColor(R.color.error));
                        statusText.setText(
                                "Could not connect to the server. Check that Docker is running.");
                    }
                });
    }
}
