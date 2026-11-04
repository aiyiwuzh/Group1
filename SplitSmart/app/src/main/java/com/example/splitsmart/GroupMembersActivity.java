package com.example.splitsmart;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.splitsmart.model.AddMemberRequest;
import com.example.splitsmart.model.CurrentUser;
import com.example.splitsmart.model.Group;
import com.example.splitsmart.model.Member;
import com.example.splitsmart.model.UpdateGroupRequest;
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
    private TextView ownerLabel;
    private TextView error;
    private TextView empty;
    private Button addMemberButton;
    private View ownerActions;
    private Button leaveGroupButton;
    private Group loadedGroup;
    private Long currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_members);

        groupId = getIntent().getLongExtra(EXTRA_GROUP_ID, -1);
        auth = ExpenseUtils.requireAuth(this);
        if (auth == null) return;

        title = findViewById(R.id.tvMembersGroupName);
        count = findViewById(R.id.tvMembersCount);
        ownerLabel = findViewById(R.id.tvMembersOwner);
        error = findViewById(R.id.tvMembersError);
        empty = findViewById(R.id.tvMembersEmpty);
        loading = findViewById(R.id.progressMembers);
        membersContainer = findViewById(R.id.layoutMembers);
        addMemberButton = findViewById(R.id.btnAddMember);
        ownerActions = findViewById(R.id.layoutOwnerActions);
        leaveGroupButton = findViewById(R.id.btnLeaveGroup);

        String groupName = getIntent().getStringExtra(EXTRA_GROUP_NAME);
        if (groupName != null && !groupName.isBlank()) {
            title.setText(groupName);
        }

        findViewById(R.id.btnMembersBack).setOnClickListener(v -> finish());
        addMemberButton.setOnClickListener(v -> showAddMemberDialog());
        findViewById(R.id.btnRenameGroup).setOnClickListener(v -> showEditGroupDialog());
        findViewById(R.id.btnDeleteGroup).setOnClickListener(v -> confirmDeleteGroup());
        leaveGroupButton.setOnClickListener(v -> confirmLeaveGroup());

        RetrofitClient.getApiService().getCurrentUser(auth).enqueue(new Callback<CurrentUser>() {
            @Override public void onResponse(Call<CurrentUser> call, Response<CurrentUser> response) {
                if (response.isSuccessful() && response.body() != null) {
                    currentUserId = response.body().getUserId();
                    renderOwnerControls();
                    if (loadedGroup != null) loadMembers();
                }
            }
            @Override public void onFailure(Call<CurrentUser> call, Throwable t) { }
        });

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
                        loadedGroup = group;
                        renderOwnerControls();
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
                            ImageButton removeButton = row.findViewById(R.id.btnRemoveMember);
                            initials.setText(initialsFor(name));
                            memberName.setText(name);
                            email.setText(member.getEmail() == null
                                    ? "No email available" : member.getEmail());
                            boolean isOwner = currentUserId != null
                                    && currentUserId.equals(loadedGroup.getOwnerId());
                            if (isOwner && !member.getId().equals(currentUserId)) {
                                removeButton.setVisibility(View.VISIBLE);
                                removeButton.setOnClickListener(v -> confirmRemoveMember(member));
                            }
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

    private void renderOwnerControls() {
        boolean owner = loadedGroup != null && currentUserId != null
                && currentUserId.equals(loadedGroup.getOwnerId());
        if (loadedGroup != null) {
            String ownerName = loadedGroup.getMembers() == null ? null
                    : loadedGroup.getMembers().stream()
                    .filter(member -> member.getId().equals(loadedGroup.getOwnerId()))
                    .map(Member::getName).findFirst().orElse(null);
            ownerLabel.setText(ownerName == null ? "Group owner" : "Owner: " + ownerName);
        }
        addMemberButton.setVisibility(owner ? View.VISIBLE : View.GONE);
        ownerActions.setVisibility(owner ? View.VISIBLE : View.GONE);
        leaveGroupButton.setVisibility(loadedGroup != null && !owner
                ? View.VISIBLE : View.GONE);
    }

    private void confirmRemoveMember(Member member) {
        new AlertDialog.Builder(this).setTitle("Remove " + member.getName() + "?")
                .setMessage("Members with an unpaid balance cannot be removed. Their past transactions remain in history.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (dialog, which) ->
                        RetrofitClient.getApiService().removeGroupMember(auth, groupId,
                                member.getId()).enqueue(new Callback<Void>() {
                            @Override public void onResponse(Call<Void> call, Response<Void> response) {
                                if (response.isSuccessful()) {
                                    Toast.makeText(GroupMembersActivity.this, "Member removed.", Toast.LENGTH_SHORT).show();
                                    loadMembers();
                                } else Toast.makeText(GroupMembersActivity.this,
                                        ExpenseUtils.errorMessage(response, "Could not remove member"), Toast.LENGTH_LONG).show();
                            }
                            @Override public void onFailure(Call<Void> call, Throwable t) {
                                Toast.makeText(GroupMembersActivity.this, "Could not connect to the server.", Toast.LENGTH_SHORT).show();
                            }
                        })).show();
    }

    private void confirmLeaveGroup() {
        new AlertDialog.Builder(this).setTitle("Leave this group?")
                .setMessage("You can leave after paying any outstanding group balance. Past transactions remain saved.")
                .setNegativeButton("Stay", null)
                .setPositiveButton("Leave group", (dialog, which) ->
                        RetrofitClient.getApiService().leaveGroup(auth, groupId)
                                .enqueue(new Callback<Void>() {
                                    @Override public void onResponse(Call<Void> call, Response<Void> response) {
                                        if (response.isSuccessful()) {
                                            Toast.makeText(GroupMembersActivity.this, "You left the group.", Toast.LENGTH_SHORT).show();
                                            Intent home = new Intent(GroupMembersActivity.this, MainActivity.class);
                                            home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                            startActivity(home);
                                            finish();
                                        } else Toast.makeText(GroupMembersActivity.this,
                                                ExpenseUtils.errorMessage(response, "Could not leave group"), Toast.LENGTH_LONG).show();
                                    }
                                    @Override public void onFailure(Call<Void> call, Throwable t) {
                                        Toast.makeText(GroupMembersActivity.this, "Could not connect to the server.", Toast.LENGTH_SHORT).show();
                                    }
                                })).show();
    }

    private void showEditGroupDialog() {
        if (loadedGroup == null) return;
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        fields.setPadding(pad, 0, pad, 0);
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setHint("Group name");
        name.setText(loadedGroup.getName());
        EditText description = new EditText(this);
        description.setHint("Description (optional)");
        description.setMinLines(2);
        description.setText(loadedGroup.getDescription() == null ? "" : loadedGroup.getDescription());
        fields.addView(name);
        fields.addView(description);
        new AlertDialog.Builder(this)
                .setTitle("Edit group details")
                .setView(fields)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    String groupName = name.getText().toString().trim();
                    if (groupName.isEmpty()) {
                        Toast.makeText(this, "Group name cannot be empty.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    RetrofitClient.getApiService().updateGroup(auth, groupId,
                            new UpdateGroupRequest(groupName,
                                    description.getText().toString().trim()))
                            .enqueue(new Callback<Group>() {
                                @Override public void onResponse(Call<Group> call, Response<Group> response) {
                                    if (response.isSuccessful() && response.body() != null) {
                                        loadedGroup = response.body();
                                        title.setText(loadedGroup.getName());
                                        Toast.makeText(GroupMembersActivity.this, "Group updated.", Toast.LENGTH_SHORT).show();
                                    } else Toast.makeText(GroupMembersActivity.this,
                                            ExpenseUtils.errorMessage(response, "Could not update group"), Toast.LENGTH_LONG).show();
                                }
                                @Override public void onFailure(Call<Group> call, Throwable t) {
                                    Toast.makeText(GroupMembersActivity.this, "Could not connect to the server.", Toast.LENGTH_SHORT).show();
                                }
                            });
                }).show();
    }

    private void confirmDeleteGroup() {
        new AlertDialog.Builder(this)
                .setTitle("Delete this group?")
                .setMessage("This permanently removes the group, its expenses, settlements, and membership records.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete group", (dialog, which) ->
                        RetrofitClient.getApiService().deleteGroup(auth, groupId)
                                .enqueue(new Callback<Void>() {
                                    @Override public void onResponse(Call<Void> call, Response<Void> response) {
                                        if (response.isSuccessful()) {
                                            Toast.makeText(GroupMembersActivity.this, "Group deleted.", Toast.LENGTH_SHORT).show();
                                            Intent home = new Intent(GroupMembersActivity.this, MainActivity.class);
                                            home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                            startActivity(home);
                                            finish();
                                        } else Toast.makeText(GroupMembersActivity.this,
                                                ExpenseUtils.errorMessage(response, "Could not delete group"), Toast.LENGTH_LONG).show();
                                    }
                                    @Override public void onFailure(Call<Void> call, Throwable t) {
                                        Toast.makeText(GroupMembersActivity.this, "Could not connect to the server.", Toast.LENGTH_SHORT).show();
                                    }
                                }))
                .show();
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
