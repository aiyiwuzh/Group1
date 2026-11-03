
package com.example.splitsmart;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.splitsmart.model.Expense;
import com.example.splitsmart.network.RetrofitClient;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Lists a group's expenses. Open with EXTRA_GROUP_ID (and optionally EXTRA_GROUP_NAME).
public class ExpenseListActivity extends AppCompatActivity {

    public static final String EXTRA_GROUP_ID = "group_id";
    public static final String EXTRA_GROUP_NAME = "group_name";

    private long groupId;
    private String auth;

    private TextView errorText;
    private TextView emptyText;
    private ExpenseAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_expense_list);

        groupId = getIntent().getLongExtra(EXTRA_GROUP_ID, -1);
        auth = ExpenseUtils.requireAuth(this);
        if (auth == null) {
            return;
        }

        String groupName = getIntent().getStringExtra(EXTRA_GROUP_NAME);
        if (groupName != null) {
            ((TextView) findViewById(R.id.tvGroupName)).setText(groupName);
        }

        errorText = findViewById(R.id.tvListError);
        emptyText = findViewById(R.id.tvEmpty);
        ListView listView = findViewById(R.id.listExpenses);
        Button backButton = findViewById(R.id.btnBack);
        Button addButton = findViewById(R.id.btnAddExpense);

        adapter = new ExpenseAdapter();
        listView.setAdapter(adapter);

        backButton.setOnClickListener(v -> finish());

        addButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddExpenseActivity.class);
            intent.putExtra(AddExpenseActivity.EXTRA_GROUP_ID, groupId);
            startActivity(intent);
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            Intent intent = new Intent(this, ExpenseDetailActivity.class);
            intent.putExtra(ExpenseDetailActivity.EXTRA_EXPENSE_ID,
                    adapter.getItem(position).getId());
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Reload after adding, editing or deleting an expense
        if (auth != null) {
            loadExpenses();
        }
    }

    private void loadExpenses() {
        RetrofitClient.getApiService()
                .getGroupExpenses(auth, groupId)
                .enqueue(new Callback<List<Expense>>() {

                    @Override
                    public void onResponse(
                            Call<List<Expense>> call,
                            Response<List<Expense>> response) {

                        if (response.isSuccessful() && response.body() != null) {
                            errorText.setVisibility(View.GONE);
                            adapter.clear();
                            adapter.addAll(response.body());
                            emptyText.setVisibility(response.body().isEmpty()
                                    ? View.VISIBLE : View.GONE);
                        } else {
                            showError(ExpenseUtils.errorMessage(
                                    response, "Could not load expenses"));
                        }
                    }

                    @Override
                    public void onFailure(
                            Call<List<Expense>> call,
                            Throwable t) {

                        showError("Cannot connect to the server.");
                    }
                });
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }

    private class ExpenseAdapter extends ArrayAdapter<Expense> {

        ExpenseAdapter() {
            super(ExpenseListActivity.this, 0, new ArrayList<>());
        }

        @NonNull
        @Override
        public View getView(int position, View convertView,
                            @NonNull ViewGroup parent) {

            View row = convertView != null ? convertView
                    : LayoutInflater.from(getContext())
                    .inflate(R.layout.item_expense, parent, false);

            Expense expense = getItem(position);

            ((TextView) row.findViewById(R.id.tvItemTitle))
                    .setText(expense.getTitle());

            ((TextView) row.findViewById(R.id.tvItemAmount))
                    .setText(ExpenseUtils.formatMoney(expense.getAmount()));

            String meta = ExpenseUtils.categoryLabel(expense.getCategory())
                    + " · Paid by " + expense.getPayer().getName()
                    + " · " + ExpenseUtils.formatDate(expense.getCreatedAt());

            if (expense.isSettled()) {
                meta += " · Settled";
            }

            ((TextView) row.findViewById(R.id.tvItemMeta)).setText(meta);

            return row;
        }
    }
}
