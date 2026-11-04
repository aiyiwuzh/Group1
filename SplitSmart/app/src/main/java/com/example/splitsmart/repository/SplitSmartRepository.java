package com.example.splitsmart.repository;

import com.example.splitsmart.model.BalanceSummary;
import com.example.splitsmart.model.Settlement;
import com.example.splitsmart.model.SettlementRequest;
import com.example.splitsmart.network.ApiService;
import com.example.splitsmart.network.RetrofitClient;

import java.util.List;

import retrofit2.Call;

// Keeps REST access out of the Activities and ViewModels' screen logic.
public class SplitSmartRepository {

    private final ApiService apiService = RetrofitClient.getApiService();

    public Call<BalanceSummary> getBalances(String auth, long groupId) {
        return apiService.getBalances(auth, groupId);
    }

    public Call<List<Settlement>> getSettlements(String auth, long groupId) {
        return apiService.getGroupSettlements(auth, groupId);
    }

    public Call<Settlement> createSettlement(
            String auth, long groupId, SettlementRequest request) {
        return apiService.createSettlement(auth, groupId, request);
    }

    public Call<Settlement> updateSettlement(
            String auth, long settlementId, SettlementRequest request) {
        return apiService.updateSettlement(auth, settlementId, request);
    }

    public Call<Void> deleteSettlement(String auth, long settlementId) {
        return apiService.deleteSettlement(auth, settlementId);
    }
}
