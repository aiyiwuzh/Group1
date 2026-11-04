package com.example.splitsmart;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.splitsmart.model.CategoryTotal;
import com.example.splitsmart.model.MonthlySummary;
import com.example.splitsmart.viewmodel.HistoryViewModel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

// Spending for one month: number of expenses, total, the user's own share,
// and totals per category. Settlements are not included.
public class MonthlySummaryActivity extends AppCompatActivity {

    public static final String EXTRA_GROUP_ID = "group_id";
    public static final String EXTRA_GROUP_NAME = "group_name";

    private static final DateTimeFormatter MONTH_FORMAT =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US);

    private HistoryViewModel viewModel;

    private TextView monthText;
    private TextView totalText;
    private TextView countsText;
    private TextView errorText;
    private TextView emptyText;
    private ProgressBar progress;
    private LinearLayout categoryRows;
    private Button nextButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monthly_summary);

        long groupId = getIntent().getLongExtra(EXTRA_GROUP_ID, -1);
        String groupName = getIntent().getStringExtra(EXTRA_GROUP_NAME);
        String auth = ExpenseUtils.requireAuth(this);
        if (auth == null) return;
        if (groupId <= 0) {
            finish();
            return;
        }

        ((TextView) findViewById(R.id.tvSummaryGroupName)).setText(
                groupName == null ? "Monthly summary" : groupName + " · monthly");

        monthText = findViewById(R.id.tvSummaryMonth);
        totalText = findViewById(R.id.tvSummaryTotal);
        countsText = findViewById(R.id.tvSummaryCounts);
        errorText = findViewById(R.id.tvSummaryError);
        emptyText = findViewById(R.id.tvSummaryEmpty);
        progress = findViewById(R.id.progressSummary);
        categoryRows = findViewById(R.id.layoutCategoryRows);
        nextButton = findViewById(R.id.btnNextMonth);

        viewModel = new ViewModelProvider(this).get(HistoryViewModel.class);
        viewModel.init(auth, groupId);

        findViewById(R.id.btnSummaryBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnPreviousMonth).setOnClickListener(v -> viewModel.previousMonth());
        nextButton.setOnClickListener(v -> viewModel.nextMonth());

        viewModel.getMonth().observe(this, month -> {
            monthText.setText(MONTH_FORMAT.format(month));
            // Future months have no expenses yet.
            boolean canGoForward = !viewModel.isCurrentOrFutureMonth();
            nextButton.setEnabled(canGoForward);
            nextButton.setAlpha(canGoForward ? 1f : 0.4f);
        });
        viewModel.getSummary().observe(this, this::render);
        viewModel.getSummaryLoading().observe(this, loading ->
                progress.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.GONE));
        viewModel.getSummaryError().observe(this, message -> {
            if (message == null || message.isBlank()) {
                errorText.setVisibility(View.GONE);
            } else {
                errorText.setText(message);
                errorText.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (viewModel != null) viewModel.loadSummary();
    }

    private void render(MonthlySummary summary) {
        categoryRows.removeAllViews();

        if (summary == null) {
            totalText.setText(ExpenseUtils.formatMoney(BigDecimal.ZERO));
            countsText.setText("");
            emptyText.setVisibility(View.GONE);
            return;
        }

        totalText.setText(ExpenseUtils.formatMoney(summary.getTotalSpending()));
        int count = summary.getExpenseCount();
        countsText.setText((count == 1 ? "1 expense" : count + " expenses")
                + "  ·  Your share " + ExpenseUtils.formatMoney(summary.getYourShare()));

        List<CategoryTotal> categories = summary.getCategories();
        boolean empty = categories == null || categories.isEmpty();
        emptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) return;

        for (CategoryTotal category : categories) {
            addCategoryRow(category, summary.getTotalSpending());
        }
    }

    private void addCategoryRow(CategoryTotal category, BigDecimal monthTotal) {
        int percent = percentOf(category.getTotal(), monthTotal);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setBackground(cardBackground());

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);

        TextView name = new TextView(this);
        name.setText(ExpenseUtils.categoryLabel(category.getCategory()));
        name.setTextSize(16);
        name.setTextColor(0xFF17212B);
        header.addView(name, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView amount = new TextView(this);
        amount.setText(ExpenseUtils.formatMoney(category.getTotal()));
        amount.setTextSize(16);
        amount.setTextColor(0xFF243746);
        header.addView(amount);
        row.addView(header);

        TextView detail = new TextView(this);
        int count = category.getExpenseCount();
        detail.setText((count == 1 ? "1 expense" : count + " expenses")
                + " · " + percent + "% of the month");
        detail.setTextSize(13);
        detail.setTextColor(0xFF61717D);
        row.addView(detail);

        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(percent);
        bar.setProgressTintList(ColorStateList.valueOf(0xFF0F8B8D));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(8));
        barParams.topMargin = dp(8);
        row.addView(bar, barParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(10);
        categoryRows.addView(row, params);
    }

    // Whole-number share of the month's total, using exact decimals.
    static int percentOf(BigDecimal part, BigDecimal total) {
        if (part == null || total == null || total.signum() == 0) return 0;
        return part.multiply(BigDecimal.valueOf(100))
                .divide(total, 0, RoundingMode.HALF_UP)
                .intValue();
    }

    private GradientDrawable cardBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(16));
        background.setStroke(dp(1), 0xFFE0E7EA);
        return background;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
