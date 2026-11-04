
package com.example.splitsmart;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.splitsmart.model.Expense;
import com.example.splitsmart.network.RetrofitClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Shows one expense. Only its creator sees the available actions.
public class ExpenseDetailActivity extends AppCompatActivity {

    public static final String EXTRA_EXPENSE_ID = "expense_id";

    private long expenseId;
    private String auth;
    private Expense expense;

    private Button editButton;
    private Button deleteButton;
    private Button markSettledButton;
    private TextView errorText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_expense_detail);

        expenseId = getIntent().getLongExtra(EXTRA_EXPENSE_ID, -1);
        auth = ExpenseUtils.requireAuth(this);
        if (auth == null) {
            return;
        }

        editButton = findViewById(R.id.btnEditExpense);
        deleteButton = findViewById(R.id.btnDeleteExpense);
        markSettledButton = findViewById(R.id.btnMarkSettledExpense);
        errorText = findViewById(R.id.tvDetailError);
        Button backButton = findViewById(R.id.btnBack);

        backButton.setOnClickListener(v -> finish());

        editButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddExpenseActivity.class);
            intent.putExtra(AddExpenseActivity.EXTRA_GROUP_ID, expense.getGroupId());
            intent.putExtra(AddExpenseActivity.EXTRA_EXPENSE_ID, expenseId);
            startActivity(intent);
        });

        deleteButton.setOnClickListener(v -> confirmDelete());
        markSettledButton.setOnClickListener(v -> confirmMarkSettled());
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Reload after returning from the edit screen
        if (auth != null) {
            loadExpense();
        }
    }

    private void loadExpense() {
        RetrofitClient.getApiService()
                .getExpense(auth, expenseId)
                .enqueue(new Callback<Expense>() {

                    @Override
                    public void onResponse(Call<Expense> call,
                                           Response<Expense> response) {

                        if (response.isSuccessful() && response.body() != null) {
                            errorText.setText("");
                            showExpense(response.body());
                        } else if (response.code() == 404) {
                            Toast.makeText(ExpenseDetailActivity.this,
                                    "This expense no longer exists.",
                                    Toast.LENGTH_SHORT).show();
                            finish();
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

    private void showExpense(Expense loaded) {
        expense = loaded;

        ((TextView) findViewById(R.id.tvDetailTitle)).setText(expense.getTitle());
        ((TextView) findViewById(R.id.tvDetailAmount))
                .setText(ExpenseUtils.formatMoney(expense.getAmount()));
        ((TextView) findViewById(R.id.tvDetailCategory))
                .setText(ExpenseUtils.categoryLabel(expense.getCategory()));
        ((TextView) findViewById(R.id.tvDetailPayer))
                .setText(expense.getPayer().getName());
        ((TextView) findViewById(R.id.tvDetailParticipants))
                .setText(ExpenseUtils.joinNames(expense.getParticipants()));
        ((TextView) findViewById(R.id.tvDetailCreated))
                .setText(ExpenseUtils.formatDate(expense.getCreatedAt())
                        + " by " + expense.getCreator().getName());

        boolean hasNote = expense.getNote() != null && !expense.getNote().isEmpty();
        TextView noteText = findViewById(R.id.tvDetailNote);
        noteText.setText(hasNote ? expense.getNote() : "");
        noteText.setVisibility(hasNote ? View.VISIBLE : View.GONE);
        findViewById(R.id.tvDetailNoteLabel)
                .setVisibility(hasNote ? View.VISIBLE : View.GONE);

        findViewById(R.id.tvDetailLocked)
                .setVisibility(expense.isSettled() ? View.VISIBLE : View.GONE);

        int actions = expense.canEdit() ? View.VISIBLE : View.GONE;
        editButton.setVisibility(actions);
        deleteButton.setVisibility(actions);
        markSettledButton.setVisibility(actions);
    }

    private void confirmMarkSettled() {
        new AlertDialog.Builder(this)
                .setTitle("Mark expense as settled?")
                .setMessage("Only continue if this expense has been fully paid. "
                        + "This marks and locks the expense; it does not record a "
                        + "payment or change balances.")
                .setPositiveButton("Mark settled",
                        (dialog, which) -> markExpenseSettled())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void markExpenseSettled() {
        markSettledButton.setEnabled(false);

        RetrofitClient.getApiService()
                .markExpenseSettled(auth, expenseId)
                .enqueue(new Callback<Expense>() {

                    @Override
                    public void onResponse(Call<Expense> call,
                                           Response<Expense> response) {
                        markSettledButton.setEnabled(true);

                        if (response.isSuccessful() && response.body() != null) {
                            showExpense(response.body());
                            Toast.makeText(ExpenseDetailActivity.this,
                                    "Expense marked as settled",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            errorText.setText(ExpenseUtils.errorMessage(
                                    response, "Could not mark expense as settled"));
                        }
                    }

                    @Override
                    public void onFailure(Call<Expense> call, Throwable t) {
                        markSettledButton.setEnabled(true);
                        errorText.setText("Cannot connect to the server.");
                    }
                });
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Delete expense?")
                .setMessage("\"" + expense.getTitle() + "\" ("
                        + ExpenseUtils.formatMoney(expense.getAmount())
                        + ") will be removed for everyone in the group. "
                        + "This can't be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteExpense())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteExpense() {
        deleteButton.setEnabled(false);

        RetrofitClient.getApiService()
                .deleteExpense(auth, expenseId)
                .enqueue(new Callback<Void>() {

                    @Override
                    public void onResponse(Call<Void> call,
                                           Response<Void> response) {

                        deleteButton.setEnabled(true);

                        if (response.isSuccessful()) {
                            Toast.makeText(ExpenseDetailActivity.this,
                                    "Expense deleted",
                                    Toast.LENGTH_SHORT).show();
                            finish();
                        } else {
                            errorText.setText(ExpenseUtils.errorMessage(
                                    response, "Could not delete expense"));
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        deleteButton.setEnabled(true);
                        errorText.setText("Cannot connect to the server.");
                    }
                });
    }
}
