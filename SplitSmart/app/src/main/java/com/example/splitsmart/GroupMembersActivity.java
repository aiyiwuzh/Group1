package com.example.splitsmart;

import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.splitsmart.model.AddMemberRequest;
import com.example.splitsmart.model.Group;
import com.example.splitsmart.model.Member;
import com.example.splitsmart.network.RetrofitClient;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class GroupMembersActivity extends AppCompatActivity {

    public static final String EXTRA_GROUP_ID = "group_id";
    public static final String EXTRA_GROUP_NAME = "group_name";

    private long groupId;
    private String auth;
    private LinearLayout membersContainer;
    private ProgressBar loading;
    private TextView title;
    private TextView count;
    private TextView error;
    private TextView empty;
    private Button addMemberButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_members);

        groupId = getIntent().getLongExtra(EXTRA_GROUP_ID, -1);
        auth = ExpenseUtils.requireAuth(this);
        if (auth == null) return;

        title = findViewById(R.id.tvMembersGroupName);
        count = findViewById(R.id.tvMembersCount);
        error = findViewById(R.id.tvMembersError);
        empty = findViewById(R.id.tvMembersEmpty);
        loading = findViewById(R.id.progressMembers);
        membersContainer = findViewById(R.id.layoutMembers);
        addMemberButton = findViewById(R.id.btnAddMember);

        String groupName = getIntent().getStringExtra(EXTRA_GROUP_NAME);
        if (groupName != null && !groupName.isBlank()) {
            title.setText(groupName);
        }

        findViewById(R.id.btnMembersBack).setOnClickListener(v -> finish());
        addMemberButton.setOnClickListener(v -> showAddMemberDialog());

        if (groupId <= 0) {
            showError("Could not identify this group. Go back and open it again.");
        } else {
            loadMembers();
        }
    }

    private void loadMembers() {
        loading.setVisibility(View.VISIBLE);
        error.setVisibility(View.GONE);
        empty.setVisibility(View.GONE);
        membersContainer.removeAllViews();

        RetrofitClient.getApiService().getGroup(auth, groupId)
                .enqueue(new Callback<Group>() {
                    @Override
                    public void onResponse(Call<Group> call, Response<Group> response) {
                        loading.setVisibility(View.GONE);
                        if (!response.isSuccessful() || response.body() == null) {
                            showError(ExpenseUtils.errorMessage(
                                    response, "Could not load group members"));
                            return;
                        }

                        Group group = response.body();
                        if (group.getName() != null) title.setText(group.getName());

                        List<Member> members = group.getMembers();
                        int memberCount = members == null ? 0 : members.size();
                        count.setText(memberCount == 1
                                ? "1 person in this group"
                                : memberCount + " people in this group");

                        if (memberCount == 0) {
                            empty.setVisibility(View.VISIBLE);
                            return;
                        }

                        LayoutInflater inflater = LayoutInflater.from(
                                GroupMembersActivity.this);
                        for (Member member : members) {
                            View row = inflater.inflate(
                                    R.layout.item_group_member,
                                    membersContainer,
                                    false);
                            String name = member.getName() == null
                                    ? "Unnamed member" : member.getName();
                            TextView initials = row.findViewById(R.id.tvMemberInitials);
                            TextView memberName = row.findViewById(R.id.tvMemberName);
                            TextView email = row.findViewById(R.id.tvMemberEmail);
                            initials.setText(initialsFor(name));
                            memberName.setText(name);
                            email.setText(member.getEmail() == null
                                    ? "No email available" : member.getEmail());
                            membersContainer.addView(row);
                        }
                    }

                    @Override
                    public void onFailure(Call<Group> call, Throwable t) {
                        loading.setVisibility(View.GONE);
                        showError("Could not connect to the server. Check that Docker is running.");
                    }
                });
    }

    private String initialsFor(String name) {
        String[] words = name.trim().split("\\s+");
        if (words.length == 0 || words[0].isEmpty()) return "?";
        if (words.length == 1) return words[0].substring(0, 1).toUpperCase();
        return (words[0].substring(0, 1) + words[words.length - 1].substring(0, 1))
                .toUpperCase();
    }

    private void showAddMemberDialog() {
        EditText emailInput = new EditText(this);
        emailInput.setSingleLine(true);
        emailInput.setHint("name@example.com");
        emailInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        emailInput.setPadding(padding, padding, padding, padding);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Add a person")
                .setMessage("They need a SplitSmart account first. Enter their account email.")
                .setView(emailInput)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add person", null)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String email = emailInput.getText().toString().trim();
                    if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        emailInput.setError("Enter a valid email address");
                        return;
                    }

                    addMemberButton.setEnabled(false);
                    RetrofitClient.getApiService()
                            .addGroupMember(auth, groupId, new AddMemberRequest(email))
                            .enqueue(new Callback<Group>() {
                                @Override
                                public void onResponse(
                                        Call<Group> call,
                                        Response<Group> response) {
                                    addMemberButton.setEnabled(true);
                                    if (response.isSuccessful()) {
                                        dialog.dismiss();
                                        Toast.makeText(GroupMembersActivity.this,
                                                "Person added to the group.",
                                                Toast.LENGTH_SHORT).show();
                                        loadMembers();
                                    } else {
                                        Toast.makeText(GroupMembersActivity.this,
                                                ExpenseUtils.errorMessage(response,
                                                        "Could not add this person"),
                                                Toast.LENGTH_LONG).show();
                                    }
                                }

                                @Override
                                public void onFailure(Call<Group> call, Throwable t) {
                                    addMemberButton.setEnabled(true);
                                    Toast.makeText(GroupMembersActivity.this,
                                            "Could not connect to the server.",
                                            Toast.LENGTH_SHORT).show();
                                }
                            });
                }));
        dialog.show();
    }

    private void showError(String message) {
        error.setText(message);
        error.setVisibility(View.VISIBLE);
    }
}
