package com.example.splitsmart.viewmodel;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.splitsmart.ExpenseUtils;
import com.example.splitsmart.model.BalanceSummary;
import com.example.splitsmart.repository.SplitSmartRepository;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class BalanceViewModel extends ViewModel {

    private final SplitSmartRepository repository = new SplitSmartRepository();
    private final MutableLiveData<BalanceSummary> summary = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public MutableLiveData<BalanceSummary> getSummary() { return summary; }
    public MutableLiveData<String> getError() { return error; }

    public void loadBalances(String auth, long groupId) {
        error.setValue(null);
        repository.getBalances(auth, groupId).enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<BalanceSummary> call,
                                   Response<BalanceSummary> response) {
                if (response.isSuccessful() && response.body() != null) {
                    summary.setValue(response.body());
                } else {
                    error.setValue(ExpenseUtils.errorMessage(
                            response, "Could not load balances"));
                }
            }

            @Override
            public void onFailure(Call<BalanceSummary> call, Throwable throwable) {
                error.setValue("Cannot connect to the server.");
            }
        });
    }
}
