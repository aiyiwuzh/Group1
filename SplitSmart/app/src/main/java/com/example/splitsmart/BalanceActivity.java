package com.example.splitsmart;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.splitsmart.model.BalanceSummary;
import com.example.splitsmart.model.PairwiseBalance;
import com.example.splitsmart.model.Settlement;
import com.example.splitsmart.viewmodel.BalanceViewModel;
import com.example.splitsmart.viewmodel.SettlementViewModel;

import java.math.BigDecimal;
import java.util.List;

public class BalanceActivity extends AppCompatActivity {

    public static final String EXTRA_GROUP_ID = "group_id";
    public static final String EXTRA_GROUP_NAME = "group_name";

    private long groupId;
    private String groupName;
    private String auth;
    private BalanceViewModel balanceViewModel;
    private SettlementViewModel settlementViewModel;
    private TextView totals;
    private TextView balanceError;
    private TextView settlementError;
    private TextView noBalances;
    private TextView noSettlements;
    private LinearLayout balanceRows;
    private LinearLayout settlementRows;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_balance);

        groupId = getIntent().getLongExtra(EXTRA_GROUP_ID, -1);
        groupName = getIntent().getStringExtra(EXTRA_GROUP_NAME);
        auth = ExpenseUtils.requireAuth(this);
        if (auth == null) return;
        if (groupId <= 0) {
            finish();
            return;
        }

        ((TextView) findViewById(R.id.tvBalanceGroupName)).setText(
                groupName == null ? "Group balances" : groupName + " balances");
        totals = findViewById(R.id.tvBalanceTotals);
        balanceError = findViewById(R.id.tvBalanceError);
        settlementError = findViewById(R.id.tvSettlementError);
        noBalances = findViewById(R.id.tvNoBalances);
        noSettlements = findViewById(R.id.tvNoSettlements);
        balanceRows = findViewById(R.id.layoutBalanceRows);
        settlementRows = findViewById(R.id.layoutSettlementRows);

        findViewById(R.id.btnBalanceBack).setOnClickListener(v -> finish());

        balanceViewModel = new ViewModelProvider(this).get(BalanceViewModel.class);
        settlementViewModel = new ViewModelProvider(this).get(SettlementViewModel.class);

        balanceViewModel.getSummary().observe(this, this::renderBalances);
        balanceViewModel.getError().observe(this,
                message -> showMessage(balanceError, message));
        settlementViewModel.getSettlements().observe(this, this::renderSettlements);
        settlementViewModel.getError().observe(this,
                message -> showMessage(settlementError, message));
        settlementViewModel.getOperationSucceeded().observe(this, succeeded -> {
            if (Boolean.TRUE.equals(succeeded)) {
                loadData();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (auth != null && balanceViewModel != null) loadData();
    }

    private void loadData() {
        balanceViewModel.loadBalances(auth, groupId);
        settlementViewModel.loadSettlements(auth, groupId);
    }

    private void renderBalances(BalanceSummary summary) {
        if (summary == null) return;
        totals.setText("You owe " + ExpenseUtils.formatMoney(summary.getTotalOwed())
                + "  ·  You are owed "
                + ExpenseUtils.formatMoney(summary.getTotalReceivable()));
        balanceRows.removeAllViews();

        List<PairwiseBalance> balances = summary.getBalances();
        noBalances.setVisibility(balances == null || balances.isEmpty()
                ? View.VISIBLE : View.GONE);
        if (balances == null) return;

        for (PairwiseBalance balance : balances) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(12, 12, 12, 12);
            row.setBackgroundColor(0xFFFFFFFF);

            TextView description = new TextView(this);
            String amount = ExpenseUtils.formatMoney(balance.getAmount());
            String text;
            if ("YOU_OWE".equals(balance.getStatus())) {
                text = "You owe " + balance.getMemberName() + " " + amount;
            } else if ("OWES_YOU".equals(balance.getStatus())) {
                text = balance.getMemberName() + " owes you " + amount;
            } else {
                text = "You and " + balance.getMemberName() + " are settled up";
            }
            description.setText(text);
            description.setTextSize(16);
            description.setTextColor(0xFF1E293B);
            row.addView(description);

            if ("YOU_OWE".equals(balance.getStatus())) {
                Button payButton = new Button(this);
                payButton.setText("Record payment");
                payButton.setOnClickListener(v -> openSettlement(
                        balance.getMemberId(), balance.getMemberName(),
                        balance.getAmount(), null));
                row.addView(payButton);
            }

            addRowDivider(balanceRows);
            balanceRows.addView(row);
        }
    }

    private void renderSettlements(List<Settlement> settlements) {
        if (settlements == null) return;
        settlementRows.removeAllViews();
        noSettlements.setVisibility(settlements.isEmpty()
                ? View.VISIBLE : View.GONE);

        for (Settlement settlement : settlements) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(12, 12, 12, 12);
            row.setBackgroundColor(0xFFFFFFFF);

            TextView summary = new TextView(this);
            summary.setText(settlement.getPayer().getName() + " paid "
                    + settlement.getPayee().getName() + " "
                    + ExpenseUtils.formatMoney(settlement.getAmount())
                    + " · " + ExpenseUtils.formatDate(settlement.getCreatedAt()));
            summary.setTextSize(15);
            summary.setTextColor(0xFF1E293B);
            row.addView(summary);

            if (settlement.canEdit()) {
                LinearLayout actions = new LinearLayout(this);
                actions.setOrientation(LinearLayout.HORIZONTAL);

                Button edit = new Button(this);
                edit.setText("Edit");
                edit.setOnClickListener(v -> openSettlement(
                        settlement.getPayee().getId(),
                        settlement.getPayee().getName(),
                        null, settlement));
                actions.addView(edit, new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

                Button delete = new Button(this);
                delete.setText("Delete");
                delete.setOnClickListener(v -> confirmDelete(settlement));
                actions.addView(delete, new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                row.addView(actions);
            }

            addRowDivider(settlementRows);
            settlementRows.addView(row);
        }
    }

    private void openSettlement(
            Long payeeId, String payeeName, BigDecimal maxAmount,
            Settlement existing) {
        Intent intent = new Intent(this, SettleUpActivity.class);
        intent.putExtra(SettleUpActivity.EXTRA_GROUP_ID, groupId);
        intent.putExtra(SettleUpActivity.EXTRA_GROUP_NAME, groupName);
        intent.putExtra(SettleUpActivity.EXTRA_PAYEE_ID, payeeId);
        intent.putExtra(SettleUpActivity.EXTRA_PAYEE_NAME, payeeName);
        if (maxAmount != null) {
            intent.putExtra(SettleUpActivity.EXTRA_MAX_AMOUNT,
                    maxAmount.toPlainString());
        }
        if (existing != null) {
            intent.putExtra(SettleUpActivity.EXTRA_SETTLEMENT_ID,
                    existing.getId());
            intent.putExtra(SettleUpActivity.EXTRA_AMOUNT,
                    existing.getAmount().toPlainString());
        }
        startActivity(intent);
    }

    private void confirmDelete(Settlement settlement) {
        new AlertDialog.Builder(this)
                .setTitle("Delete settlement?")
                .setMessage("This removes the recorded payment and recalculates the balance.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) ->
                        settlementViewModel.deleteSettlement(
                                auth, settlement.getId()))
                .show();
    }

    private static void addRowDivider(LinearLayout parent) {
        View divider = new View(parent.getContext());
        divider.setBackgroundColor(0xFFE2E8F0);
        parent.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
    }

    private static void showMessage(TextView view, String message) {
        if (message == null || message.isBlank()) {
            view.setVisibility(View.GONE);
        } else {
            view.setText(message);
            view.setVisibility(View.VISIBLE);
        }
    }
}
