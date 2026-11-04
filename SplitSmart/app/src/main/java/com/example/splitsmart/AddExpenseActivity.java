package com.example.splitsmart;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
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
import com.example.splitsmart.model.ExpenseShare;
import com.example.splitsmart.model.Group;
import com.example.splitsmart.model.Member;
import com.example.splitsmart.model.SharePreviewResponse;
import com.example.splitsmart.model.ShareResponse;
import com.example.splitsmart.model.SplitPreviewRequest;
import com.example.splitsmart.network.RetrofitClient;

import java.math.BigDecimal;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

// Creates an expense (EXTRA_GROUP_ID) or edits one (EXTRA_GROUP_ID + EXTRA_EXPENSE_ID).
public class AddExpenseActivity extends AppCompatActivity {

    public static final String EXTRA_GROUP_ID = "group_id";
    public static final String EXTRA_EXPENSE_ID = "expense_id";
    private static final int MIN_PARTICIPANTS = 2;
    private static final String[] SPLIT_METHODS = {"Equal", "Exact amounts", "Percentages"};
    private static final String[] SPLIT_VALUES = {"EQUAL", "EXACT", "PERCENTAGE"};

    private long groupId;
    private long expenseId;
    private String auth;

    private EditText titleInput;
    private EditText amountInput;
    private EditText noteInput;
    private Spinner categorySpinner;
    private EditText customCategoryInput;
    private Uri receiptUri;
    private Button attachReceiptButton;
    private Spinner payerSpinner;
    private Spinner splitMethodSpinner;
    private LinearLayout participantsLayout;
    private LinearLayout splitValuesLayout;
    private Button saveButton;
    private Button previewButton;
    private TextView errorText;
    private TextView splitHelp;
    private TextView previewText;

    private final List<Member> members = new ArrayList<>();
    private final List<CheckBox> participantBoxes = new ArrayList<>();
    private final Map<Long, EditText> splitInputs = new LinkedHashMap<>();
    private Map<Long, BigDecimal> editingShares;
    private boolean rebuildingSplitInputs;

    private boolean isEditing() { return expenseId != -1; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_expense);
        groupId = getIntent().getLongExtra(EXTRA_GROUP_ID, -1);
        expenseId = getIntent().getLongExtra(EXTRA_EXPENSE_ID, -1);
        auth = ExpenseUtils.requireAuth(this);
        if (auth == null) return;

        titleInput = findViewById(R.id.etExpenseTitle);
        amountInput = findViewById(R.id.etExpenseAmount);
        amountInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { clearPreview(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        noteInput = findViewById(R.id.etExpenseNote);
        categorySpinner = findViewById(R.id.spCategory);
        customCategoryInput = findViewById(R.id.etCustomCategory);
        attachReceiptButton = findViewById(R.id.btnAttachReceipt);
        attachReceiptButton.setOnClickListener(v -> {
            Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            picker.addCategory(Intent.CATEGORY_OPENABLE);
            picker.setType("image/*");
            startActivityForResult(picker, 412);
        });
        payerSpinner = findViewById(R.id.spPayer);
        splitMethodSpinner = findViewById(R.id.spSplitMethod);
        participantsLayout = findViewById(R.id.layoutParticipants);
        splitValuesLayout = findViewById(R.id.layoutSplitValues);
        saveButton = findViewById(R.id.btnSaveExpense);
        previewButton = findViewById(R.id.btnPreviewSplit);
        errorText = findViewById(R.id.tvExpenseError);
        splitHelp = findViewById(R.id.tvSplitHelp);
        previewText = findViewById(R.id.tvSplitPreview);

        if (isEditing()) {
            ((TextView) findViewById(R.id.tvFormHeading)).setText("Edit Expense");
            saveButton.setText("Save Changes");
        }

        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, ExpenseUtils.CATEGORY_LABELS);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(categoryAdapter);
        categorySpinner.setSelection(ExpenseUtils.DEFAULT_CATEGORY_INDEX);
        categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                customCategoryInput.setVisibility(position == ExpenseUtils.CUSTOM_CATEGORY_INDEX
                        ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        ArrayAdapter<String> splitAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, SPLIT_METHODS);
        splitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        splitMethodSpinner.setAdapter(splitAdapter);
        splitMethodSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                renderSplitInputs();
                showSplitHelp();
                clearPreview();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        payerSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                lockPayerParticipant();
                renderSplitInputs();
                clearPreview();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        amountInput.setOnFocusChangeListener((v, focused) -> { if (!focused) clearPreview(); });
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        saveButton.setOnClickListener(v -> save());
        previewButton.setOnClickListener(v -> previewSplit());
        loadGroup();
    }

    private void loadGroup() {
        RetrofitClient.getApiService().getGroup(auth, groupId).enqueue(new Callback<Group>() {
            @Override public void onResponse(Call<Group> call, Response<Group> response) {
                if (response.isSuccessful() && response.body() != null) {
                    showMembers(response.body().getMembers());
                    if (isEditing()) loadExpense(); else loadCurrentUser();
                } else errorText.setText(ExpenseUtils.errorMessage(response, "Could not load group members"));
            }
            @Override public void onFailure(Call<Group> call, Throwable t) { errorText.setText("Cannot connect to the server."); }
        });
    }

    private void loadCurrentUser() {
        RetrofitClient.getApiService().getCurrentUser(auth).enqueue(new Callback<CurrentUser>() {
            @Override public void onResponse(Call<CurrentUser> call, Response<CurrentUser> response) {
                if (response.isSuccessful() && response.body() != null) selectPayer(response.body().getUserId());
                saveButton.setEnabled(true);
                previewButton.setEnabled(true);
            }
            @Override public void onFailure(Call<CurrentUser> call, Throwable t) {
                saveButton.setEnabled(true); previewButton.setEnabled(true);
            }
        });
    }

    private void loadExpense() {
        RetrofitClient.getApiService().getExpense(auth, expenseId).enqueue(new Callback<Expense>() {
            @Override public void onResponse(Call<Expense> call, Response<Expense> response) {
                if (response.isSuccessful() && response.body() != null) {
                    fillForm(response.body());
                    saveButton.setEnabled(true); previewButton.setEnabled(true);
                } else errorText.setText(ExpenseUtils.errorMessage(response, "Could not load expense"));
            }
            @Override public void onFailure(Call<Expense> call, Throwable t) { errorText.setText("Cannot connect to the server."); }
        });
    }

    private void showMembers(List<Member> groupMembers) {
        members.clear(); members.addAll(groupMembers);
        participantBoxes.clear(); participantsLayout.removeAllViews();
        List<String> names = new ArrayList<>();
        for (Member member : members) {
            names.add(member.getName());
            CheckBox box = new CheckBox(this);
            box.setText(member.getName());
            box.setTextSize(16);
            box.setChecked(true);
            box.setOnCheckedChangeListener((button, checked) -> {
                if (!rebuildingSplitInputs) { renderSplitInputs(); clearPreview(); }
            });
            participantBoxes.add(box);
            participantsLayout.addView(box);
        }
        ArrayAdapter<String> payerAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, names);
        payerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        payerSpinner.setAdapter(payerAdapter);
        lockPayerParticipant();
        renderSplitInputs();
    }

    private void selectPayer(Long userId) {
        for (int i = 0; i < members.size(); i++) {
            if (members.get(i).getId().equals(userId)) { payerSpinner.setSelection(i); break; }
        }
        lockPayerParticipant();
    }

    private void lockPayerParticipant() {
        int payerIndex = payerSpinner.getSelectedItemPosition();
        for (int i = 0; i < participantBoxes.size(); i++) {
            CheckBox box = participantBoxes.get(i);
            if (i == payerIndex) {
                box.setChecked(true); box.setEnabled(false);
                box.setText(members.get(i).getName() + " (payer)");
            } else {
                box.setEnabled(true); box.setText(members.get(i).getName());
            }
        }
    }

    private void fillForm(Expense expense) {
        titleInput.setText(expense.getTitle());
        amountInput.setText(expense.getAmount().toPlainString());
        int categoryIndex = ExpenseUtils.categoryIndex(expense.getCategory());
        boolean customCategory = expense.getCategory() != null
                && !ExpenseUtils.CATEGORY_VALUES[categoryIndex].equalsIgnoreCase(expense.getCategory());
        categorySpinner.setSelection(customCategory
                ? ExpenseUtils.CUSTOM_CATEGORY_INDEX : categoryIndex);
        if (customCategory) customCategoryInput.setText(expense.getCategory());
        noteInput.setText(expense.getNote() == null ? "" : expense.getNote());
        Set<Long> participantIds = new HashSet<>();
        for (Member participant : expense.getParticipants()) participantIds.add(participant.getId());
        rebuildingSplitInputs = true;
        for (int i = 0; i < members.size(); i++) participantBoxes.get(i).setChecked(participantIds.contains(members.get(i).getId()));
        rebuildingSplitInputs = false;
        selectPayer(expense.getPayer().getId());

        String savedMethod = expense.getSplitMethod();
        if ("PERCENTAGE".equals(savedMethod)) {
            splitMethodSpinner.setSelection(2);
            editingShares = expense.getSplitValues() == null
                    ? new HashMap<>() : new HashMap<>(expense.getSplitValues());
        } else if ("EXACT".equals(savedMethod)) {
            splitMethodSpinner.setSelection(1);
            editingShares = new HashMap<>();
            if (expense.getShares() != null) {
                for (ExpenseShare share : expense.getShares()) editingShares.put(share.getUserId(), share.getAmount());
            }
        } else {
            splitMethodSpinner.setSelection(0);
            editingShares = null;
        }
        renderSplitInputs();
        showSplitHelp();
    }

    private List<Long> selectedParticipantIds() {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) if (participantBoxes.get(i).isChecked()) ids.add(members.get(i).getId());
        return ids;
    }

    private void renderSplitInputs() {
        if (splitValuesLayout == null || splitMethodSpinner.getSelectedItemPosition() <= 0) {
            if (splitValuesLayout != null) splitValuesLayout.removeAllViews();
            splitInputs.clear(); return;
        }
        Map<Long, String> oldValues = new HashMap<>();
        for (Map.Entry<Long, EditText> entry : splitInputs.entrySet()) oldValues.put(entry.getKey(), entry.getValue().getText().toString());
        splitValuesLayout.removeAllViews(); splitInputs.clear();
        String method = selectedSplitMethod();
        for (int i = 0; i < members.size(); i++) {
            if (!participantBoxes.get(i).isChecked()) continue;
            Member member = members.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            TextView name = new TextView(this);
            name.setText(member.getName() + (i == payerSpinner.getSelectedItemPosition() ? " (payer)" : ""));
            name.setTextSize(14); name.setTextColor(0xFF17212B);
            row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            EditText input = new EditText(this);
            input.setSingleLine(true);
            input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            input.setHint(method.equals("EXACT") ? "0.00" : "0");
            input.setTextSize(16);
            input.setEms(7);
            String old = oldValues.get(member.getId());
            if (old != null) input.setText(old);
            else if (editingShares != null && editingShares.containsKey(member.getId())) input.setText(editingShares.get(member.getId()).toPlainString());
            input.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { clearPreview(); }
                @Override public void afterTextChanged(Editable s) { }
            });
            splitInputs.put(member.getId(), input);
            row.addView(input);
            splitValuesLayout.addView(row);
        }
        showSplitHelp();
    }

    private String selectedSplitMethod() {
        int pos = splitMethodSpinner.getSelectedItemPosition();
        return SPLIT_VALUES[Math.max(0, Math.min(pos, SPLIT_VALUES.length - 1))];
    }

    private void showSplitHelp() {
        if (splitHelp == null || splitMethodSpinner.getSelectedItemPosition() < 0) return;
        switch (selectedSplitMethod()) {
            case "EXACT": splitHelp.setText("Enter each person's share. Shares must add up to the total."); break;
            case "PERCENTAGE": splitHelp.setText("Enter each person's percentage. Percentages must add up to 100%."); break;
            default: splitHelp.setText("The total is divided evenly; any extra cents are distributed fairly.");
        }
    }

    private Map<Long, BigDecimal> readSplitValues() throws IllegalArgumentException {
        if (selectedSplitMethod().equals("EQUAL")) return null;
        Map<Long, BigDecimal> values = new LinkedHashMap<>();
        for (Map.Entry<Long, EditText> entry : splitInputs.entrySet()) {
            String text = entry.getValue().getText().toString().trim();
            if (text.isEmpty()) throw new IllegalArgumentException("Enter a value for every selected person.");
            BigDecimal value;
            try { value = new BigDecimal(text); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("Enter valid split values."); }
            if (value.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("Split values cannot be negative.");
            if (selectedSplitMethod().equals("EXACT") && value.stripTrailingZeros().scale() > 2) throw new IllegalArgumentException("Exact amounts can have at most 2 decimal places.");
            values.put(entry.getKey(), value);
        }
        BigDecimal total = new BigDecimal(amountInput.getText().toString().trim());
        BigDecimal sum = values.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expected = selectedSplitMethod().equals("EXACT") ? total : new BigDecimal("100");
        if (sum.compareTo(expected) != 0) {
            throw new IllegalArgumentException(selectedSplitMethod().equals("EXACT")
                    ? "Exact shares must add up to " + ExpenseUtils.formatMoney(total) + "."
                    : "Percentages must add up to 100%.");
        }
        return values;
    }

    private ExpenseForm readForm() {
        String title = titleInput.getText().toString().trim();
        if (title.isEmpty()) throw new IllegalArgumentException("Please enter a title.");
        String amountText = amountInput.getText().toString().trim();
        BigDecimal amount;
        try { amount = new BigDecimal(amountText); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Please enter a valid amount."); }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("Amount must be greater than $0.");
        if (amount.stripTrailingZeros().scale() > 2) throw new IllegalArgumentException("Amount can have at most 2 decimal places.");
        int payerIndex = payerSpinner.getSelectedItemPosition();
        if (payerIndex < 0) throw new IllegalArgumentException("Please choose who paid.");
        List<Long> ids = selectedParticipantIds();
        if (ids.size() < MIN_PARTICIPANTS) throw new IllegalArgumentException("Choose at least 2 people, including the payer.");
        return new ExpenseForm(title, amount, ids, members.get(payerIndex).getId(), readSplitValues());
    }

    private void previewSplit() {
        final ExpenseForm form;
        try { form = readForm(); } catch (IllegalArgumentException e) { errorText.setText(e.getMessage()); return; }
        errorText.setText(""); previewButton.setEnabled(false);
        SplitPreviewRequest request = new SplitPreviewRequest(form.amount, form.payerId, form.participantIds, selectedSplitMethod(), form.splitValues);
        RetrofitClient.getApiService().previewExpenseSplit(auth, groupId, request).enqueue(new Callback<SharePreviewResponse>() {
            @Override public void onResponse(Call<SharePreviewResponse> call, Response<SharePreviewResponse> response) {
                previewButton.setEnabled(true);
                if (!response.isSuccessful() || response.body() == null) {
                    errorText.setText(ExpenseUtils.errorMessage(response, "Could not preview this split")); return;
                }
                StringBuilder text = new StringBuilder("Preview · ").append(ExpenseUtils.formatMoney(response.body().getAmount())).append(" total\n");
                List<ShareResponse> shares = response.body().getShares();
                if (shares != null) for (ShareResponse share : shares) {
                    text.append("\n").append(share.getName()).append(share.isPayer() ? " (payer)" : " owes")
                            .append(": ").append(ExpenseUtils.formatMoney(share.getAmount()));
                }
                previewText.setText(text); previewText.setVisibility(View.VISIBLE);
            }
            @Override public void onFailure(Call<SharePreviewResponse> call, Throwable t) {
                previewButton.setEnabled(true); errorText.setText("Cannot connect to the server.");
            }
        });
    }

    private void clearPreview() { if (previewText != null) previewText.setVisibility(View.GONE); }

    private void save() {
        final ExpenseForm form;
        try { form = readForm(); } catch (IllegalArgumentException e) { errorText.setText(e.getMessage()); return; }
        String note = noteInput.getText().toString().trim();
        String category = categorySpinner.getSelectedItemPosition() == ExpenseUtils.CUSTOM_CATEGORY_INDEX
                ? customCategoryInput.getText().toString().trim()
                : ExpenseUtils.CATEGORY_VALUES[categorySpinner.getSelectedItemPosition()];
        if (category.isEmpty()) {
            errorText.setText("Enter a name for the custom category.");
            return;
        }
        ExpenseRequest request = new ExpenseRequest(form.title, form.amount,
                category,
                form.payerId, form.participantIds, note.isEmpty() ? null : note,
                selectedSplitMethod(), form.splitValues);
        saveButton.setEnabled(false); errorText.setText("");
        Call<Expense> call = isEditing()
                ? RetrofitClient.getApiService().updateExpense(auth, expenseId, request)
                : RetrofitClient.getApiService().createExpense(auth, groupId, request);
        call.enqueue(new Callback<Expense>() {
            @Override public void onResponse(Call<Expense> call, Response<Expense> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (receiptUri != null) uploadReceipt(response.body().getId());
                    else {
                        saveButton.setEnabled(true);
                        Toast.makeText(AddExpenseActivity.this, isEditing() ? "Expense updated" : "Expense added", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                } else {
                    saveButton.setEnabled(true);
                    errorText.setText(ExpenseUtils.errorMessage(response, "Could not save expense"));
                }
            }
            @Override public void onFailure(Call<Expense> call, Throwable t) { saveButton.setEnabled(true); errorText.setText("Cannot connect to the server."); }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 412 && resultCode == RESULT_OK && data != null
                && data.getData() != null) {
            receiptUri = data.getData();
            attachReceiptButton.setText("Receipt photo selected · tap to replace");
        }
    }

    private void uploadReceipt(Long savedExpenseId) {
        try (InputStream input = getContentResolver().openInputStream(receiptUri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new java.io.IOException("Cannot read selected image");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
                if (output.size() > 5 * 1024 * 1024) {
                    throw new java.io.IOException("Receipt photo must be 5 MB or smaller.");
                }
            }
            String type = getContentResolver().getType(receiptUri);
            if (type == null) type = "image/jpeg";
            RequestBody body = RequestBody.create(MediaType.parse(type), output.toByteArray());
            MultipartBody.Part photo = MultipartBody.Part.createFormData("file", "receipt", body);
            RetrofitClient.getApiService().uploadReceipt(auth, savedExpenseId, photo)
                    .enqueue(new Callback<Void>() {
                        @Override public void onResponse(Call<Void> call, Response<Void> response) {
                            if (response.isSuccessful()) {
                                Toast.makeText(AddExpenseActivity.this, "Expense and receipt photo saved.", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(AddExpenseActivity.this,
                                        ExpenseUtils.errorMessage(response, "Expense saved, but receipt upload failed."), Toast.LENGTH_LONG).show();
                            }
                            finish();
                        }
                        @Override public void onFailure(Call<Void> call, Throwable t) {
                            Toast.makeText(AddExpenseActivity.this, "Expense saved, but receipt upload failed. You can attach it from Edit Expense.", Toast.LENGTH_LONG).show();
                            finish();
                        }
                    });
        } catch (Exception e) {
            Toast.makeText(this, "Expense saved, but the photo could not be read: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private static class ExpenseForm {
        final String title; final BigDecimal amount; final List<Long> participantIds;
        final Long payerId; final Map<Long, BigDecimal> splitValues;
        ExpenseForm(String title, BigDecimal amount, List<Long> participantIds, Long payerId, Map<Long, BigDecimal> splitValues) {
            this.title = title; this.amount = amount; this.participantIds = participantIds; this.payerId = payerId; this.splitValues = splitValues;
        }
    }
}
