
package com.example.splitsmart.network;

import com.example.splitsmart.model.CurrentUser;
import com.example.splitsmart.model.BalanceSummary;
import com.example.splitsmart.model.Expense;
import com.example.splitsmart.model.ExpenseRequest;
import com.example.splitsmart.model.Group;
import com.example.splitsmart.model.LoginRequest;
import com.example.splitsmart.model.LoginResponse;
import com.example.splitsmart.model.RegisterRequest;
import com.example.splitsmart.model.RegisterResponse;
import com.example.splitsmart.model.Settlement;
import com.example.splitsmart.model.SettlementRequest;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface ApiService {

    @GET("api/health")
    Call<String> checkHealth();

    @POST("api/auth/login")
    Call<LoginResponse> login(@Body LoginRequest request);

    @POST("api/auth/register")
    Call<RegisterResponse> register(@Body RegisterRequest request);

    @GET("api/auth/me")
    Call<CurrentUser> getCurrentUser(@Header("Authorization") String auth);

    @GET("api/groups")
    Call<List<Group>> getMyGroups(@Header("Authorization") String auth);

    @GET("api/groups/{groupId}")
    Call<Group> getGroup(
            @Header("Authorization") String auth,
            @Path("groupId") long groupId);

    // Expenses

    @GET("api/groups/{groupId}/expenses")
    Call<List<Expense>> getGroupExpenses(
            @Header("Authorization") String auth,
            @Path("groupId") long groupId);

    @POST("api/groups/{groupId}/expenses")
    Call<Expense> createExpense(
            @Header("Authorization") String auth,
            @Path("groupId") long groupId,
            @Body ExpenseRequest request);

    @GET("api/expenses/{id}")
    Call<Expense> getExpense(
            @Header("Authorization") String auth,
            @Path("id") long expenseId);

    @PUT("api/expenses/{id}")
    Call<Expense> updateExpense(
            @Header("Authorization") String auth,
            @Path("id") long expenseId,
            @Body ExpenseRequest request);

    @PUT("api/expenses/{id}/settled")
    Call<Expense> markExpenseSettled(
            @Header("Authorization") String auth,
            @Path("id") long expenseId);

    @DELETE("api/expenses/{id}")
    Call<Void> deleteExpense(
            @Header("Authorization") String auth,
            @Path("id") long expenseId);

    // Balances and settlements

    @GET("api/groups/{groupId}/balances")
    Call<BalanceSummary> getBalances(
            @Header("Authorization") String auth,
            @Path("groupId") long groupId);

    @GET("api/groups/{groupId}/settlements")
    Call<List<Settlement>> getGroupSettlements(
            @Header("Authorization") String auth,
            @Path("groupId") long groupId);

    @POST("api/groups/{groupId}/settlements")
    Call<Settlement> createSettlement(
            @Header("Authorization") String auth,
            @Path("groupId") long groupId,
            @Body SettlementRequest request);

    @PUT("api/settlements/{id}")
    Call<Settlement> updateSettlement(
            @Header("Authorization") String auth,
            @Path("id") long settlementId,
            @Body SettlementRequest request);

    @DELETE("api/settlements/{id}")
    Call<Void> deleteSettlement(
            @Header("Authorization") String auth,
            @Path("id") long settlementId);
}
