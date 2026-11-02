
package com.example.splitsmart;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.splitsmart.model.CurrentUser;
import com.example.splitsmart.model.Expense;
import com.example.splitsmart.model.ExpenseRequest;
import com.example.splitsmart.model.Group;
import com.example.splitsmart.model.Member;
import com.example.splitsmart.network.RetrofitClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Creates an expense (EXTRA_GROUP_ID) or edits one (EXTRA_GROUP_ID + EXTRA_EXPENSE_ID).
public class AddExpenseActivity extends AppCompatActivity {

    public static final String EXTRA_GROUP_ID = "group_id";
    public static final String EXTRA_EXPENSE_ID = "expense_id";

    private static final int MIN_PARTICIPANTS = 2;

    private long groupId;
    private long expenseId;
    private String auth;

    private EditText titleInput;
    private EditText amountInput;
    private EditText noteInput;
    private Spinner categorySpinner;
    private Spinner payerSpinner;
    private LinearLayout participantsLayout;
    private Button saveButton;
    private TextView errorText;

    private final List<Member> members = new ArrayList<>();
    private final List<CheckBox> participantBoxes = new ArrayList<>();

    private boolean isEditing() {
        return expenseId != -1;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_expense);

        groupId = getIntent().getLongExtra(EXTRA_GROUP_ID, -1);
        expenseId = getIntent().getLongExtra(EXTRA_EXPENSE_ID, -1);
        auth = ExpenseUtils.requireAuth(this);
        if (auth == null) {
            return;
        }

        titleInput = findViewById(R.id.etExpenseTitle);
        amountInput = findViewById(R.id.etExpenseAmount);
        noteInput = findViewById(R.id.etExpenseNote);
        categorySpinner = findViewById(R.id.spCategory);
        payerSpinner = findViewById(R.id.spPayer);
        participantsLayout = findViewById(R.id.layoutParticipants);
        saveButton = findViewById(R.id.btnSaveExpense);
        errorText = findViewById(R.id.tvExpenseError);
        Button backButton = findViewById(R.id.btnBack);

        if (isEditing()) {
            ((TextView) findViewById(R.id.tvFormHeading)).setText("Edit Expense");
            saveButton.setText("Save Changes");
        }

        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item,
                ExpenseUtils.CATEGORY_LABELS);
        categoryAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(categoryAdapter);
        categorySpinner.setSelection(ExpenseUtils.DEFAULT_CATEGORY_INDEX);

        payerSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent,
                                               View view, int position, long id) {
                        lockPayerParticipant();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                });

        backButton.setOnClickListener(v -> finish());
        saveButton.setOnClickListener(v -> save());

        loadGroup();
    }

    // Step 1: load the group's members for the payer and participant lists
    private void loadGroup() {
        RetrofitClient.getApiService()
                .getGroup(auth, groupId)
                .enqueue(new Callback<Group>() {

                    @Override
                    public void onResponse(Call<Group> call,
                                           Response<Group> response) {

                        if (response.isSuccessful() && response.body() != null) {
                            showMembers(response.body().getMembers());

                            if (isEditing()) {
                                loadExpense();
                            } else {
                                loadCurrentUser();
                            }
                        } else {
                            errorText.setText(ExpenseUtils.errorMessage(
                                    response, "Could not load group members"));
                        }
                    }

                    @Override
                    public void onFailure(Call<Group> call, Throwable t) {
                        errorText.setText("Cannot connect to the server.");
                    }
                });
    }

    // Step 2 (new expense): default the payer to the current user
    private void loadCurrentUser() {
        RetrofitClient.getApiService()
                .getCurrentUser(auth)
                .enqueue(new Callback<CurrentUser>() {

                    @Override
                    public void onResponse(Call<CurrentUser> call,
                                           Response<CurrentUser> response) {

                        if (response.isSuccessful() && response.body() != null) {
                            selectPayer(response.body().getUserId());
                        }
                        saveButton.setEnabled(true);
                    }

                    @Override
                    public void onFailure(Call<CurrentUser> call, Throwable t) {
                        saveButton.setEnabled(true);
                    }
                });
    }

    // Step 2 (editing): fill the form with the saved expense
    private void loadExpense() {
        RetrofitClient.getApiService()
                .getExpense(auth, expenseId)
                .enqueue(new Callback<Expense>() {

                    @Override
                    public void onResponse(Call<Expense> call,
                                           Response<Expense> response) {

                        if (response.isSuccessful() && response.body() != null) {
                            fillForm(response.body());
                            saveButton.setEnabled(true);
                        } else {
                            errorText.setText(ExpenseUtils.errorMessage(
                                    response, "Could not load expense"));
                        }
                    }

                    @Override
                    public void onFailure(Call<Expense> call, Throwable t) {
                        errorText.setText("Cannot connect to the server.");
                    }
                });
    }

    private void showMembers(List<Member> groupMembers) {
        members.clear();
        members.addAll(groupMembers);
        participantBoxes.clear();
        participantsLayout.removeAllViews();

        List<String> names = new ArrayList<>();

        for (Member member : members) {
            names.add(member.getName());

            // New expenses include everyone by default
            CheckBox box = new CheckBox(this);
            box.setText(member.getName());
            box.setTextSize(16);
            box.setChecked(true);
            participantBoxes.add(box);
            participantsLayout.addView(box);
        }

        ArrayAdapter<String> payerAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, names);
        payerAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        payerSpinner.setAdapter(payerAdapter);

        lockPayerParticipant();
    }

    private void selectPayer(Long userId) {
        for (int i = 0; i < members.size(); i++) {
            if (members.get(i).getId().equals(userId)) {
                payerSpinner.setSelection(i);
                break;
            }
        }
        lockPayerParticipant();
    }

    // The payer is always a participant and cannot be unchecked.
    private void lockPayerParticipant() {
        int payerIndex = payerSpinner.getSelectedItemPosition();

        for (int i = 0; i < participantBoxes.size(); i++) {
            CheckBox box = participantBoxes.get(i);
            String name = members.get(i).getName();

            if (i == payerIndex) {
                box.setChecked(true);
                box.setEnabled(false);
                box.setText(name + " (payer)");
            } else {
                box.setEnabled(true);
                box.setText(name);
            }
        }
    }

    private void fillForm(Expense expense) {
        titleInput.setText(expense.getTitle());
        amountInput.setText(expense.getAmount().toPlainString());
        categorySpinner.setSelection(
                ExpenseUtils.categoryIndex(expense.getCategory()));
        noteInput.setText(expense.getNote() == null ? "" : expense.getNote());

        Set<Long> participantIds = new HashSet<>();
        for (Member participant : expense.getParticipants()) {
            participantIds.add(participant.getId());
        }

        for (int i = 0; i < members.size(); i++) {
            participantBoxes.get(i).setChecked(
                    participantIds.contains(members.get(i).getId()));
        }

        selectPayer(expense.getPayer().getId());
    }

    private void save() {
        String title = titleInput.getText().toString().trim();
        String amountText = amountInput.getText().toString().trim();
        String note = noteInput.getText().toString().trim();

        if (title.isEmpty()) {
            errorText.setText("Please enter a title.");
            return;
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(amountText);
        } catch (NumberFormatException e) {
            errorText.setText("Please enter a valid amount.");
            return;
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            errorText.setText("Amount must be greater than $0.");
            return;
        }

        if (amount.stripTrailingZeros().scale() > 2) {
            errorText.setText("Amount can have at most 2 decimal places.");
            return;
        }

        int payerIndex = payerSpinner.getSelectedItemPosition();
        if (payerIndex < 0) {
            errorText.setText("Please choose who paid.");
            return;
        }

        List<Long> participantIds = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            if (participantBoxes.get(i).isChecked()) {
                participantIds.add(members.get(i).getId());
            }
        }

        if (participantIds.size() < MIN_PARTICIPANTS) {
            errorText.setText("Choose at least 2 people, including the payer.");
            return;
        }

        ExpenseRequest request = new ExpenseRequest(
                title,
                amount,
                ExpenseUtils.CATEGORY_VALUES[categorySpinner.getSelectedItemPosition()],
                members.get(payerIndex).getId(),
                participantIds,
                note.isEmpty() ? null : note);

        saveButton.setEnabled(false);
        errorText.setText("");

        Call<Expense> call = isEditing()
                ? RetrofitClient.getApiService().updateExpense(auth, expenseId, request)
                : RetrofitClient.getApiService().createExpense(auth, groupId, request);

        call.enqueue(new Callback<Expense>() {

            @Override
            public void onResponse(Call<Expense> call,
                                   Response<Expense> response) {

                saveButton.setEnabled(true);

                if (response.isSuccessful()) {
                    Toast.makeText(AddExpenseActivity.this,
                            isEditing() ? "Expense updated" : "Expense added",
                            Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    errorText.setText(ExpenseUtils.errorMessage(
                            response, "Could not save expense"));
                }
            }

            @Override
            public void onFailure(Call<Expense> call, Throwable t) {
                saveButton.setEnabled(true);
                errorText.setText("Cannot connect to the server.");
            }
        });
    }
}
