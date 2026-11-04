package com.example.splitsmart.viewmodel;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.splitsmart.ExpenseUtils;
import com.example.splitsmart.model.HistoryItem;
import com.example.splitsmart.model.HistoryResponse;
import com.example.splitsmart.model.Member;
import com.example.splitsmart.model.MonthlySummary;
import com.example.splitsmart.repository.SplitSmartRepository;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// State for the History and Monthly Summary screens. Searching and filtering
// run on the server, so every filter change asks for the list again.
public class HistoryViewModel extends ViewModel {

    private final SplitSmartRepository repository = new SplitSmartRepository();

    private final MutableLiveData<HistoryFilters> filters =
            new MutableLiveData<>(HistoryFilters.none());
    private final MutableLiveData<List<HistoryItem>> items = new MutableLiveData<>();
    private final MutableLiveData<List<Member>> members =
            new MutableLiveData<>(new ArrayList<>());
    // True when the group has transactions but none match the filters
    private final MutableLiveData<Boolean> filteredToEmpty = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    private final MutableLiveData<YearMonth> month =
            new MutableLiveData<>(YearMonth.now());
    private final MutableLiveData<MonthlySummary> summary = new MutableLiveData<>();
    private final MutableLiveData<Boolean> summaryLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> summaryError = new MutableLiveData<>();

    // Responses to an older request are ignored, so typing quickly in the
    // search box never leaves stale results on screen.
    private int historyRequest;
    private int summaryRequest;

    private String auth;
    private long groupId;

    public void init(String auth, long groupId) {
        this.auth = auth;
        this.groupId = groupId;
    }

    public MutableLiveData<HistoryFilters> getFilters() { return filters; }
    public MutableLiveData<List<HistoryItem>> getItems() { return items; }
    public MutableLiveData<List<Member>> getMembers() { return members; }
    public MutableLiveData<Boolean> getFilteredToEmpty() { return filteredToEmpty; }
    public MutableLiveData<Boolean> getLoading() { return loading; }
    public MutableLiveData<String> getError() { return error; }

    public MutableLiveData<YearMonth> getMonth() { return month; }
    public MutableLiveData<MonthlySummary> getSummary() { return summary; }
    public MutableLiveData<Boolean> getSummaryLoading() { return summaryLoading; }
    public MutableLiveData<String> getSummaryError() { return summaryError; }

    private HistoryFilters currentFilters() {
        HistoryFilters current = filters.getValue();
        return current == null ? HistoryFilters.none() : current;
    }

    // ------------------------------------------------------------------ history

    public void setKeyword(String keyword) { apply(currentFilters().withKeyword(keyword)); }
    public void setType(String type) { apply(currentFilters().withType(type)); }
    public void setCategory(String category) { apply(currentFilters().withCategory(category)); }
    public void setMemberId(Long memberId) { apply(currentFilters().withMemberId(memberId)); }
    public void setSort(String sort) { apply(currentFilters().withSort(sort)); }
    public void clearFilters() { apply(currentFilters().cleared()); }

    private void apply(HistoryFilters next) {
        if (next.equals(currentFilters())) {
            return;
        }
        filters.setValue(next);
        loadHistory();
    }

    // Also called from onResume, so edits and deletes on other screens show up.
    public void loadHistory() {
        if (auth == null) return;
        final int request = ++historyRequest;
        loading.setValue(true);
        error.setValue(null);

        repository.getHistory(auth, groupId, currentFilters()).enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<HistoryResponse> call,
                                   Response<HistoryResponse> response) {
                if (request != historyRequest) return;
                loading.setValue(false);
                HistoryResponse body = response.body();
                if (response.isSuccessful() && body != null) {
                    List<HistoryItem> rows = body.getItems() == null
                            ? new ArrayList<>() : body.getItems();
                    if (body.getMembers() != null) {
                        members.setValue(body.getMembers());
                    }
                    filteredToEmpty.setValue(rows.isEmpty() && body.getTotalCount() > 0);
                    items.setValue(rows);
                } else {
                    error.setValue(ExpenseUtils.errorMessage(
                            response, "Could not load history"));
                }
            }

            @Override
            public void onFailure(Call<HistoryResponse> call, Throwable throwable) {
                if (request != historyRequest) return;
                loading.setValue(false);
                error.setValue("Cannot connect to the server.");
            }
        });
    }

    // ------------------------------------------------------------------ monthly summary

    public void previousMonth() { setMonth(currentMonth().minusMonths(1)); }
    public void nextMonth() { setMonth(currentMonth().plusMonths(1)); }

    public boolean isCurrentOrFutureMonth() {
        return !currentMonth().isBefore(YearMonth.now());
    }

    private YearMonth currentMonth() {
        YearMonth value = month.getValue();
        return value == null ? YearMonth.now() : value;
    }

    private void setMonth(YearMonth value) {
        month.setValue(value);
        loadSummary();
    }

    public void loadSummary() {
        if (auth == null) return;
        final int request = ++summaryRequest;
        summaryLoading.setValue(true);
        summaryError.setValue(null);

        // The phone's time zone decides where the month starts and ends.
        repository.getMonthlySummary(auth, groupId, currentMonth().toString(),
                        ZoneId.systemDefault().getId())
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(Call<MonthlySummary> call,
                                           Response<MonthlySummary> response) {
                        if (request != summaryRequest) return;
                        summaryLoading.setValue(false);
                        if (response.isSuccessful() && response.body() != null) {
                            summary.setValue(response.body());
                        } else {
                            summary.setValue(null);
                            summaryError.setValue(ExpenseUtils.errorMessage(
                                    response, "Could not load the monthly summary"));
                        }
                    }

                    @Override
                    public void onFailure(Call<MonthlySummary> call, Throwable throwable) {
                        if (request != summaryRequest) return;
                        summaryLoading.setValue(false);
                        summary.setValue(null);
                        summaryError.setValue("Cannot connect to the server.");
                    }
                });
    }
}
