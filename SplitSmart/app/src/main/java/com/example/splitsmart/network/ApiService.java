
package com.example.splitsmart.network;

import com.example.splitsmart.model.CurrentUser;
import com.example.splitsmart.model.ProfileUpdateRequest;
import com.example.splitsmart.model.UpdateGroupRequest;
import com.example.splitsmart.model.CreateGroupRequest;
import com.example.splitsmart.model.BalanceSummary;
import com.example.splitsmart.model.AddMemberRequest;
import com.example.splitsmart.model.Expense;
import com.example.splitsmart.model.ExpenseRequest;
import com.example.splitsmart.model.Group;
import com.example.splitsmart.model.HistoryResponse;
import com.example.splitsmart.model.LoginRequest;
import com.example.splitsmart.model.LoginResponse;
import com.example.splitsmart.model.MonthlySummary;
import com.example.splitsmart.model.RegisterRequest;
import com.example.splitsmart.model.RegisterResponse;
import com.example.splitsmart.model.Settlement;
import com.example.splitsmart.model.SettlementRequest;
import com.example.splitsmart.model.SharePreviewResponse;
import com.example.splitsmart.model.SplitPreviewRequest;
import com.example.splitsmart.model.SuggestedPayment;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Multipart;
import retrofit2.http.Part;
import okhttp3.MultipartBody;
import okhttp3.ResponseBody;

public interface ApiService {

    @GET("api/health")
    Call<String> checkHealth();

    @POST("api/auth/login")
    Call<LoginResponse> login(@Body LoginRequest request);

    @POST("api/auth/register")
    Call<RegisterResponse> register(@Body RegisterRequest request);

    @GET("api/auth/me")
    Call<CurrentUser> getCurrentUser(@Header("Authorization") String auth);

    @PUT("api/auth/me")
    Call<CurrentUser> updateCurrentUser(
            @Header("Authorization") String auth,
            @Body ProfileUpdateRequest request);

    @GET("api/groups")
    Call<List<Group>> getMyGroups(@Header("Authorization") String auth);

    @POST("api/groups")
    Call<Group> createGroup(
            @Header("Authorization") String auth,
            @Body CreateGroupRequest request);

    @POST("api/groups/{groupId}/members")
    Call<Group> addGroupMember(
            @Header("Authorization") String auth,
            @Path("groupId") long groupId,
            @Body AddMemberRequest request);

    @DELETE("api/groups/{groupId}/members/{memberId}")
    Call<Void> removeGroupMember(@Header("Authorization") String auth,
            @Path("groupId") long groupId, @Path("memberId") long memberId);

    @POST("api/groups/{groupId}/leave")
    Call<Void> leaveGroup(@Header("Authorization") String auth,
            @Path("groupId") long groupId);

    @GET("api/groups/{groupId}")
    Call<Group> getGroup(
            @Header("Authorization") String auth,
            @Path("groupId") long groupId);

    @PUT("api/groups/{groupId}")
    Call<Group> updateGroup(
            @Header("Authorization") String auth,
            @Path("groupId") long groupId,
            @Body UpdateGroupRequest request);

    @DELETE("api/groups/{groupId}")
    Call<Void> deleteGroup(
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

    @POST("api/groups/{groupId}/expenses/preview")
    Call<SharePreviewResponse> previewExpenseSplit(
            @Header("Authorization") String auth,
            @Path("groupId") long groupId,
            @Body SplitPreviewRequest request);

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

    @Multipart
    @POST("api/expenses/{id}/receipt")
    Call<Void> uploadReceipt(@Header("Authorization") String auth,
            @Path("id") long expenseId, @Part MultipartBody.Part file);

    @GET("api/expenses/{id}/receipt")
    Call<ResponseBody> getReceipt(@Header("Authorization") String auth,
            @Path("id") long expenseId);

    // Balances and settlements

    @GET("api/groups/{groupId}/balances")
    Call<BalanceSummary> getBalances(
            @Header("Authorization") String auth,
            @Path("groupId") long groupId);

    @GET("api/groups/{groupId}/balances/suggestions")
    Call<List<SuggestedPayment>> getSuggestedPayments(
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

    // History and monthly summaries. Null query values are left out of the URL.

    @GET("api/groups/{groupId}/history")
    Call<HistoryResponse> getHistory(
            @Header("Authorization") String auth,
            @Path("groupId") long groupId,
            @Query("q") String keyword,
            @Query("type") String type,
            @Query("category") String category,
            @Query("memberId") Long memberId,
            @Query("sort") String sort);

    @GET("api/groups/{groupId}/summary/monthly")
    Call<MonthlySummary> getMonthlySummary(
            @Header("Authorization") String auth,
            @Path("groupId") long groupId,
            @Query("month") String month,
            @Query("timeZone") String timeZone);
}
