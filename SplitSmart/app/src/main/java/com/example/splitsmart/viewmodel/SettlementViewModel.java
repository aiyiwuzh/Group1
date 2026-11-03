package com.example.splitsmart.viewmodel;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.splitsmart.ExpenseUtils;
import com.example.splitsmart.model.Settlement;
import com.example.splitsmart.model.SettlementRequest;
import com.example.splitsmart.repository.SplitSmartRepository;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SettlementViewModel extends ViewModel {

    private final SplitSmartRepository repository = new SplitSmartRepository();
    private final MutableLiveData<List<Settlement>> settlements = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> operationSucceeded = new MutableLiveData<>(false);

    public MutableLiveData<List<Settlement>> getSettlements() { return settlements; }
    public MutableLiveData<String> getError() { return error; }
    public MutableLiveData<Boolean> getOperationSucceeded() { return operationSucceeded; }

    public void loadSettlements(String auth, long groupId) {
        error.setValue(null);
        repository.getSettlements(auth, groupId).enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<List<Settlement>> call,
                                   Response<List<Settlement>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    settlements.setValue(response.body());
                } else {
                    error.setValue(ExpenseUtils.errorMessage(
                            response, "Could not load settlements"));
                }
            }

            @Override
            public void onFailure(Call<List<Settlement>> call, Throwable throwable) {
                error.setValue("Cannot connect to the server.");
            }
        });
    }

    public void createSettlement(
            String auth, long groupId, SettlementRequest request) {
        operationSucceeded.setValue(false);
        error.setValue(null);
        repository.createSettlement(auth, groupId, request)
                .enqueue(settlementCallback());
    }

    public void updateSettlement(
            String auth, long settlementId, SettlementRequest request) {
        operationSucceeded.setValue(false);
        error.setValue(null);
        repository.updateSettlement(auth, settlementId, request)
                .enqueue(settlementCallback());
    }

    public void deleteSettlement(String auth, long settlementId) {
        operationSucceeded.setValue(false);
        error.setValue(null);
        repository.deleteSettlement(auth, settlementId).enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    operationSucceeded.setValue(true);
                } else {
                    error.setValue(ExpenseUtils.errorMessage(
                            response, "Could not delete settlement"));
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable throwable) {
                error.setValue("Cannot connect to the server.");
            }
        });
    }

    private Callback<Settlement> settlementCallback() {
        return new Callback<>() {
            @Override
            public void onResponse(Call<Settlement> call,
                                   Response<Settlement> response) {
                if (response.isSuccessful() && response.body() != null) {
                    operationSucceeded.setValue(true);
                } else {
                    error.setValue(ExpenseUtils.errorMessage(
                            response, "Could not save settlement"));
                }
            }

            @Override
            public void onFailure(Call<Settlement> call, Throwable throwable) {
                error.setValue("Cannot connect to the server.");
            }
        };
    }
}
