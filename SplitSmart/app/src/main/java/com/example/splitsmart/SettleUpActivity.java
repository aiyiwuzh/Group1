package com.example.splitsmart;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.splitsmart.model.SettlementRequest;
import com.example.splitsmart.viewmodel.SettlementViewModel;

import java.math.BigDecimal;

public class SettleUpActivity extends AppCompatActivity {

    public static final String EXTRA_GROUP_ID = "group_id";
    public static final String EXTRA_GROUP_NAME = "group_name";
    public static final String EXTRA_PAYEE_ID = "payee_id";
    public static final String EXTRA_PAYEE_NAME = "payee_name";
    public static final String EXTRA_PAYER_ID = "payer_id";
    public static final String EXTRA_PAYER_NAME = "payer_name";
    public static final String EXTRA_MAX_AMOUNT = "max_amount";
    public static final String EXTRA_SETTLEMENT_ID = "settlement_id";
    public static final String EXTRA_AMOUNT = "amount";
    public static final String EXTRA_NOTE = "note";

    private long groupId;
    private long payeeId;
    private long payerId;
    private long settlementId;
    private BigDecimal maxAmount;
    private String auth;
    private EditText amountInput;
    private EditText noteInput;
    private TextView errorText;
    private SettlementViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settlement);

        groupId = getIntent().getLongExtra(EXTRA_GROUP_ID, -1);
        payeeId = getIntent().getLongExtra(EXTRA_PAYEE_ID, -1);
        payerId = getIntent().getLongExtra(EXTRA_PAYER_ID, -1);
        settlementId = getIntent().getLongExtra(EXTRA_SETTLEMENT_ID, -1);
        auth = ExpenseUtils.requireAuth(this);
        if (auth == null) return;
        if (groupId <= 0 || payeeId <= 0 || payerId <= 0) {
            finish();
            return;
        }

        String groupName = getIntent().getStringExtra(EXTRA_GROUP_NAME);
        String payeeName = getIntent().getStringExtra(EXTRA_PAYEE_NAME);
        String payerName = getIntent().getStringExtra(EXTRA_PAYER_NAME);
        boolean editing = settlementId > 0;
        ((TextView) findViewById(R.id.tvSettlementTitle)).setText(
                editing ? "Edit settlement" : "Record a payment");
        ((TextView) findViewById(R.id.tvSettlementPayee)).setText(
                "Group: " + (groupName == null ? "SplitSmart" : groupName)
                        + "\nFrom: " + (payerName == null ? "member" : payerName)
                        + "\nTo: " + (payeeName == null ? "member" : payeeName));

        String maxText = getIntent().getStringExtra(EXTRA_MAX_AMOUNT);
        TextView maximumText = findViewById(R.id.tvSettlementMaximum);
        if (maxText != null) {
            try {
                maxAmount = new BigDecimal(maxText);
                maximumText.setText("Current amount owed: "
                        + ExpenseUtils.formatMoney(maxAmount));
            } catch (NumberFormatException ignored) {
                maxAmount = null;
            }
        } else if (editing) {
            maximumText.setText("The updated amount will be checked against the current debt.");
        } else {
            maximumText.setText("The amount must not exceed the current debt between these members.");
        }

        amountInput = findViewById(R.id.etSettlementAmount);
        amountInput.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        String initialAmount = getIntent().getStringExtra(EXTRA_AMOUNT);
        if (initialAmount != null) {
            amountInput.setText(initialAmount);
        } else if (maxAmount != null) {
            amountInput.setText(maxAmount.toPlainString());
        }
        noteInput = findViewById(R.id.etSettlementNote);
        String initialNote = getIntent().getStringExtra(EXTRA_NOTE);
        if (initialNote != null) noteInput.setText(initialNote);

        errorText = findViewById(R.id.tvSettlementError);
        Button saveButton = findViewById(R.id.btnSaveSettlement);
        saveButton.setText(editing ? "Save changes" : "Record payment");
        saveButton.setOnClickListener(v -> saveSettlement());
        findViewById(R.id.btnSettlementBack).setOnClickListener(v -> finish());

        viewModel = new ViewModelProvider(this).get(SettlementViewModel.class);
        viewModel.getError().observe(this, message -> {
            if (message == null || message.isBlank()) {
                errorText.setVisibility(View.GONE);
            } else {
                errorText.setText(message);
                errorText.setVisibility(View.VISIBLE);
            }
        });
        viewModel.getOperationSucceeded().observe(this, succeeded -> {
            if (Boolean.TRUE.equals(succeeded)) {
                Toast.makeText(this,
                        editing ? "Settlement updated" : "Payment recorded",
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void saveSettlement() {
        String text = amountInput.getText().toString().trim();
        if (text.isEmpty()) {
            showLocalError("Enter an amount.");
            return;
        }

        final BigDecimal amount;
        try {
            amount = new BigDecimal(text);
        } catch (NumberFormatException exception) {
            showLocalError("Enter a valid amount.");
            return;
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            showLocalError("The amount must be greater than zero.");
            return;
        }
        if (maxAmount != null && amount.compareTo(maxAmount) > 0) {
            showLocalError("The amount cannot exceed the current amount owed.");
            return;
        }

        errorText.setVisibility(View.GONE);
        String note = noteInput.getText().toString().trim();
        SettlementRequest request = new SettlementRequest(
                payerId, payeeId, amount, note.isEmpty() ? null : note);
        if (settlementId > 0) {
            viewModel.updateSettlement(auth, settlementId, request);
        } else {
            viewModel.createSettlement(auth, groupId, request);
        }
    }

    private void showLocalError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }
}
