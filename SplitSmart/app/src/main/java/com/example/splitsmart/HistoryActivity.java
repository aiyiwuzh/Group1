package com.example.splitsmart;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.splitsmart.model.HistoryItem;
import com.example.splitsmart.model.Member;
import com.example.splitsmart.viewmodel.HistoryFilters;
import com.example.splitsmart.viewmodel.HistoryViewModel;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

// A group's expenses and settlements together, with search, filters and
// sorting. Open with EXTRA_GROUP_ID (and optionally EXTRA_GROUP_NAME).
public class HistoryActivity extends AppCompatActivity {

    public static final String EXTRA_GROUP_ID = "group_id";
    public static final String EXTRA_GROUP_NAME = "group_name";

    // Wait for a pause in typing before searching.
    private static final long SEARCH_DELAY_MS = 350;

    private long groupId;
    private String groupName;
    private String auth;
    private HistoryViewModel viewModel;

    private EditText searchInput;
    private ChipGroup typeChips;
    private Spinner categorySpinner;
    private Spinner memberSpinner;
    private Spinner sortSpinner;
    private View clearButton;
    private ProgressBar progress;
    private TextView countText;
    private TextView errorText;
    private TextView emptyText;
    private HistoryAdapter adapter;

    // Members shown in the member spinner, after "Everyone"
    private final List<Member> memberOptions = new ArrayList<>();

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        groupId = getIntent().getLongExtra(EXTRA_GROUP_ID, -1);
        groupName = getIntent().getStringExtra(EXTRA_GROUP_NAME);
        auth = ExpenseUtils.requireAuth(this);
        if (auth == null) return;
        if (groupId <= 0) {
            finish();
            return;
        }

        ((TextView) findViewById(R.id.tvHistoryGroupName)).setText(
                groupName == null ? "History" : groupName + " history");

        searchInput = findViewById(R.id.etHistorySearch);
        typeChips = findViewById(R.id.chipsHistoryType);
        categorySpinner = findViewById(R.id.spHistoryCategory);
        memberSpinner = findViewById(R.id.spHistoryMember);
        sortSpinner = findViewById(R.id.spHistorySort);
        clearButton = findViewById(R.id.btnHistoryClear);
        progress = findViewById(R.id.progressHistory);
        countText = findViewById(R.id.tvHistoryCount);
        errorText = findViewById(R.id.tvHistoryError);
        emptyText = findViewById(R.id.tvHistoryEmpty);
        ListView listView = findViewById(R.id.listHistory);

        viewModel = new ViewModelProvider(this).get(HistoryViewModel.class);
        viewModel.init(auth, groupId);

        adapter = new HistoryAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) ->
                openItem(adapter.getItem(position)));

        findViewById(R.id.btnHistoryBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnMonthlySummary).setOnClickListener(v -> {
            Intent intent = new Intent(this, MonthlySummaryActivity.class);
            intent.putExtra(MonthlySummaryActivity.EXTRA_GROUP_ID, groupId);
            intent.putExtra(MonthlySummaryActivity.EXTRA_GROUP_NAME, groupName);
            startActivity(intent);
        });
        clearButton.setOnClickListener(v -> clearFilters());

        setUpSearch();
        setUpTypeChips();
        setUpSpinners();
        observe();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Expenses and settlements may have changed on other screens.
        if (viewModel != null) viewModel.loadHistory();
    }

    @Override
    protected void onDestroy() {
        searchHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ------------------------------------------------------------------ controls

    // Controls start from the ViewModel's filters, so they survive rotation.
    // Setting them fires their listeners, but unchanged filters send no request.

    private void setUpSearch() {
        HistoryFilters filters = viewModel.getFilters().getValue();
        if (filters != null && filters.getKeyword() != null) {
            searchInput.setText(filters.getKeyword());
        }

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                if (pendingSearch != null) {
                    searchHandler.removeCallbacks(pendingSearch);
                }
                String text = s.toString();
                pendingSearch = () -> viewModel.setKeyword(text);
                searchHandler.postDelayed(pendingSearch, SEARCH_DELAY_MS);
            }
        });
    }

    private void setUpTypeChips() {
        HistoryFilters filters = viewModel.getFilters().getValue();
        String type = filters == null ? null : filters.getType();
        if (HistoryFilters.TYPE_EXPENSE.equals(type)) {
            typeChips.check(R.id.chipHistoryExpenses);
        } else if (HistoryFilters.TYPE_SETTLEMENT.equals(type)) {
            typeChips.check(R.id.chipHistorySettlements);
        }

        typeChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            int checked = checkedIds.isEmpty() ? R.id.chipHistoryAll : checkedIds.get(0);
            if (checked == R.id.chipHistoryExpenses) {
                viewModel.setType(HistoryFilters.TYPE_EXPENSE);
            } else if (checked == R.id.chipHistorySettlements) {
                viewModel.setType(HistoryFilters.TYPE_SETTLEMENT);
            } else {
                viewModel.setType(null);
            }
        });
    }

    private void setUpSpinners() {
        HistoryFilters filters = viewModel.getFilters().getValue();

        List<String> categories = new ArrayList<>();
        categories.add("All categories");
        for (int i = 0; i < ExpenseUtils.CATEGORY_VALUES.length; i++) {
            categories.add(ExpenseUtils.CATEGORY_LABELS[i]);
        }
        categorySpinner.setAdapter(spinnerAdapter(categories));
        if (filters != null && filters.getCategory() != null) {
            categorySpinner.setSelection(ExpenseUtils.categoryIndex(filters.getCategory()) + 1);
        }
        categorySpinner.setOnItemSelectedListener(onSelected(position ->
                viewModel.setCategory(position == 0
                        ? null : ExpenseUtils.CATEGORY_VALUES[position - 1])));

        renderMemberOptions(new ArrayList<>());
        memberSpinner.setOnItemSelectedListener(onSelected(position ->
                viewModel.setMemberId(position == 0 || position > memberOptions.size()
                        ? null : memberOptions.get(position - 1).getId())));

        List<String> sorts = new ArrayList<>();
        for (String label : HistoryFilters.SORT_LABELS) {
            sorts.add(label);
        }
        sortSpinner.setAdapter(spinnerAdapter(sorts));
        if (filters != null) {
            sortSpinner.setSelection(HistoryFilters.sortIndex(filters.getSort()));
        }
        sortSpinner.setOnItemSelectedListener(onSelected(position ->
                viewModel.setSort(HistoryFilters.SORT_VALUES[position])));
    }

    // Rebuilds the member spinner only when the group's members change, and
    // keeps the current choice selected.
    private void renderMemberOptions(List<Member> members) {
        if (!memberOptions.isEmpty() && sameIds(memberOptions, members)) return;

        memberOptions.clear();
        memberOptions.addAll(members);

        List<String> labels = new ArrayList<>();
        labels.add("Everyone");
        for (Member member : members) {
            labels.add(member.getName());
        }
        memberSpinner.setAdapter(spinnerAdapter(labels));

        HistoryFilters filters = viewModel.getFilters().getValue();
        Long selected = filters == null ? null : filters.getMemberId();
        for (int i = 0; selected != null && i < members.size(); i++) {
            if (selected.equals(members.get(i).getId())) {
                memberSpinner.setSelection(i + 1);
            }
        }
    }

    private static boolean sameIds(List<Member> a, List<Member> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).getId().equals(b.get(i).getId())) return false;
        }
        return true;
    }

    private void clearFilters() {
        // Clear in the ViewModel first so the controls' listeners see no change.
        viewModel.clearFilters();
        if (pendingSearch != null) {
            searchHandler.removeCallbacks(pendingSearch);
        }
        searchInput.setText("");
        typeChips.check(R.id.chipHistoryAll);
        categorySpinner.setSelection(0);
        memberSpinner.setSelection(0);
    }

    // ------------------------------------------------------------------ state

    private void observe() {
        viewModel.getItems().observe(this, items -> {
            if (items == null) return;
            adapter.clear();
            adapter.addAll(items);
            updateEmptyState();
        });

        viewModel.getMembers().observe(this, members -> {
            if (members != null) renderMemberOptions(members);
        });

        viewModel.getFilteredToEmpty().observe(this, empty -> updateEmptyState());

        viewModel.getFilters().observe(this, filters ->
                clearButton.setVisibility(filters != null && filters.hasActiveFilters()
                        ? View.VISIBLE : View.GONE));

        viewModel.getLoading().observe(this, loading ->
                progress.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.GONE));

        viewModel.getError().observe(this, message -> {
            if (message == null || message.isBlank()) {
                errorText.setVisibility(View.GONE);
            } else {
                errorText.setText(message);
                errorText.setVisibility(View.VISIBLE);
            }
        });
    }

    private void updateEmptyState() {
        List<HistoryItem> items = viewModel.getItems().getValue();
        if (items == null) return;

        boolean filteredToEmpty = Boolean.TRUE.equals(viewModel.getFilteredToEmpty().getValue());
        emptyText.setText(filteredToEmpty
                ? "Nothing matches these filters."
                : "No transactions yet. Expenses and settlements will show up here.");
        emptyText.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);

        countText.setText(items.size() == 1 ? "1 transaction" : items.size() + " transactions");
    }

    // ------------------------------------------------------------------ detail

    private void openItem(HistoryItem item) {
        if (item.isExpense()) {
            // The existing expense screen shows shares and the creator's actions.
            Intent intent = new Intent(this, ExpenseDetailActivity.class);
            intent.putExtra(ExpenseDetailActivity.EXTRA_EXPENSE_ID, item.getId());
            startActivity(intent);
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(item.getTitle())
                .setMessage(ExpenseUtils.formatMoney(item.getAmount())
                        + "\nRecorded " + ExpenseUtils.formatDate(item.getCreatedAt())
                        + (item.getNote() == null || item.getNote().isBlank()
                        ? "" : "\nNote: " + item.getNote())
                        + "\n\nThis repayment was made outside SplitSmart. "
                        + "The person who recorded it can edit or delete it on the balances screen.")
                .setNegativeButton("Close", null)
                .setPositiveButton("Open balances", (dialog, which) -> {
                    Intent intent = new Intent(this, BalanceActivity.class);
                    intent.putExtra(BalanceActivity.EXTRA_GROUP_ID, groupId);
                    intent.putExtra(BalanceActivity.EXTRA_GROUP_NAME, groupName);
                    startActivity(intent);
                })
                .show();
    }

    // ------------------------------------------------------------------ helpers

    private ArrayAdapter<String> spinnerAdapter(List<String> labels) {
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, labels);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return spinnerAdapter;
    }

    private interface PositionListener {
        void onSelected(int position);
    }

    private static AdapterView.OnItemSelectedListener onSelected(PositionListener listener) {
        return new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                listener.onSelected(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        };
    }

    private class HistoryAdapter extends ArrayAdapter<HistoryItem> {

        HistoryAdapter() {
            super(HistoryActivity.this, 0, new ArrayList<>());
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            View row = convertView != null ? convertView
                    : LayoutInflater.from(getContext())
                    .inflate(R.layout.item_history, parent, false);

            HistoryItem item = getItem(position);
            TextView badge = row.findViewById(R.id.tvHistoryType);
            String meta;

            if (item.isExpense()) {
                badge.setText("EXPENSE");
                badge.setTextColor(getResources().getColor(R.color.brand_teal_dark, null));
                badge.setBackgroundResource(R.drawable.bg_status_chip);
                meta = ExpenseUtils.categoryLabel(item.getCategory())
                        + " · Paid by " + item.getPayer().getName()
                        + " · " + (item.getParticipants() == null
                        ? item.getParticipantCount() + " people"
                        : ExpenseUtils.joinNames(item.getParticipants()));
                if (item.isSettled()) {
                    meta += " · Settled";
                }
            } else {
                badge.setText("SETTLEMENT");
                badge.setTextColor(getResources().getColor(R.color.brand_ink, null));
                badge.setBackgroundResource(R.drawable.bg_history_settlement_chip);
                meta = "Recorded settlement";
            }

            ((TextView) row.findViewById(R.id.tvHistoryTitle)).setText(item.getTitle());
            ((TextView) row.findViewById(R.id.tvHistoryAmount))
                    .setText(ExpenseUtils.formatMoney(item.getAmount()));
            ((TextView) row.findViewById(R.id.tvHistoryMeta))
                    .setText(meta + "\n" + ExpenseUtils.formatDate(item.getCreatedAt()));

            return row;
        }
    }
}
