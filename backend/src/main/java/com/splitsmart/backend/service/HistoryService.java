package com.splitsmart.backend.service;

import com.splitsmart.backend.dto.CategoryTotalResponse;
import com.splitsmart.backend.dto.HistoryItemResponse;
import com.splitsmart.backend.dto.HistoryResponse;
import com.splitsmart.backend.dto.MemberResponse;
import com.splitsmart.backend.dto.MonthlySummaryResponse;
import com.splitsmart.backend.dto.TransactionType;
import com.splitsmart.backend.entity.Expense;
import com.splitsmart.backend.entity.ExpenseCategory;
import com.splitsmart.backend.entity.Membership;
import com.splitsmart.backend.entity.Settlement;
import com.splitsmart.backend.entity.User;
import com.splitsmart.backend.repository.ExpenseRepository;
import com.splitsmart.backend.repository.GroupRepository;
import com.splitsmart.backend.repository.MembershipRepository;
import com.splitsmart.backend.repository.SettlementRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Transaction history (search, filters, sorting) and monthly summaries.
// Reads the same expense and settlement rows the other features write, so
// edits show up immediately and deleted rows are simply gone.
@Service
public class HistoryService {

    // How the history list can be ordered. NEWEST is the default.
    public enum Sort {
        NEWEST,
        OLDEST,
        AMOUNT_HIGH,
        AMOUNT_LOW
    }

    static final int MAX_KEYWORD_LENGTH = 100;

    // Used when the app does not send its time zone.
    static final ZoneId DEFAULT_TIME_ZONE = ZoneId.of("America/Los_Angeles");

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final ExpenseRepository expenseRepository;
    private final SettlementRepository settlementRepository;
    private final GroupRepository groupRepository;
    private final MembershipRepository membershipRepository;

    public HistoryService(
            ExpenseRepository expenseRepository,
            SettlementRepository settlementRepository,
            GroupRepository groupRepository,
            MembershipRepository membershipRepository) {
        this.expenseRepository = expenseRepository;
        this.settlementRepository = settlementRepository;
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
    }

    private void requireGroup(Long groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found");
        }
    }

    private void requireMember(Long groupId, Long userId) {
        if (!membershipRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "You are not a member of this group");
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    // ------------------------------------------------------------------ history

    // Every argument after groupId is optional (null or blank = no filter).
    //   keyword  - matched against expense titles and notes, and against the
    //              names in a settlement ("Bob paid Alice"); case-insensitive
    //   type     - EXPENSE, SETTLEMENT or ALL
    //   category - an expense category; settlements have none, so they are hidden
    //   memberId - an expense's payer or participant, a settlement's payer or payee
    //   sort     - NEWEST (default), OLDEST, AMOUNT_HIGH, AMOUNT_LOW
    @Transactional(readOnly = true)
    public HistoryResponse getHistory(
            Long userId, Long groupId, String keyword, String type,
            String category, Long memberId, String sort) {

        requireGroup(groupId);
        requireMember(groupId, userId);

        String search = parseKeyword(keyword);
        TransactionType onlyType = parseType(type);
        ExpenseCategory onlyCategory = parseCategory(category);
        Sort order = parseSort(sort);

        List<MemberResponse> members = membershipRepository.findByGroupId(groupId)
                .stream()
                .map(Membership::getUser)
                .map(HistoryService::toMember)
                .sorted(Comparator.comparing(MemberResponse::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(MemberResponse::id))
                .toList();

        if (memberId != null && members.stream().noneMatch(m -> m.id().equals(memberId))) {
            throw badRequest("That member is not in this group");
        }

        List<Expense> expenses =
                expenseRepository.findByGroupIdOrderByCreatedAtDescIdDesc(groupId);
        List<Settlement> settlements =
                settlementRepository.findByGroupIdOrderByCreatedAtDescIdDesc(groupId);

        List<HistoryItemResponse> items = new ArrayList<>();

        if (onlyType != TransactionType.SETTLEMENT) {
            for (Expense expense : expenses) {
                if (matches(expense, search, onlyCategory, memberId)) {
                    items.add(toItem(expense));
                }
            }
        }

        // A category filter only applies to expenses.
        if (onlyType != TransactionType.EXPENSE && onlyCategory == null) {
            for (Settlement settlement : settlements) {
                if (matches(settlement, search, memberId)) {
                    items.add(toItem(settlement));
                }
            }
        }

        items.sort(comparator(order));

        return new HistoryResponse(items, expenses.size() + settlements.size(), members);
    }

    private static boolean matches(
            Expense expense, String search, ExpenseCategory category, Long memberId) {

        if (category != null && expense.getCategory() != category) {
            return false;
        }

        if (memberId != null
                && !expense.getPayer().getId().equals(memberId)
                && expense.getParticipants().stream()
                        .noneMatch(p -> p.getId().equals(memberId))) {
            return false;
        }

        return search == null || contains(search, expense.getTitle(), expense.getNote());
    }

    private static boolean matches(Settlement settlement, String search, Long memberId) {
        if (memberId != null
                && !settlement.getPayer().getId().equals(memberId)
                && !settlement.getPayee().getId().equals(memberId)) {
            return false;
        }

        return search == null || contains(search, settlementTitle(settlement));
    }

    private static boolean contains(String search, String... fields) {
        for (String field : fields) {
            if (field != null && field.toLowerCase(Locale.ROOT).contains(search)) {
                return true;
            }
        }
        return false;
    }

    // Newest first means latest createdAt; ties fall back to type, then id,
    // so the order never changes between refreshes.
    private static final Comparator<HistoryItemResponse> OLDEST_FIRST =
            Comparator.comparing(HistoryItemResponse::createdAt)
                    .thenComparing(HistoryItemResponse::type)
                    .thenComparing(HistoryItemResponse::id);

    static Comparator<HistoryItemResponse> comparator(Sort sort) {
        Comparator<HistoryItemResponse> newestFirst = OLDEST_FIRST.reversed();
        Comparator<HistoryItemResponse> byAmount =
                Comparator.comparing(HistoryItemResponse::amount);

        return switch (sort) {
            case NEWEST -> newestFirst;
            case OLDEST -> OLDEST_FIRST;
            case AMOUNT_HIGH -> byAmount.reversed().thenComparing(newestFirst);
            case AMOUNT_LOW -> byAmount.thenComparing(newestFirst);
        };
    }

    // ------------------------------------------------------------------ monthly summary

    // month is YYYY-MM and defaults to the current month; timeZone is an IANA
    // id such as America/Los_Angeles and decides where the month starts and ends.
    @Transactional(readOnly = true)
    public MonthlySummaryResponse getMonthlySummary(
            Long userId, Long groupId, String month, String timeZone) {

        requireGroup(groupId);
        requireMember(groupId, userId);

        ZoneId zone = parseZone(timeZone);
        YearMonth yearMonth = parseMonth(month, zone);

        Instant start = yearMonth.atDay(1).atStartOfDay(zone).toInstant();
        Instant end = yearMonth.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();

        // Settlements move money between members; they are not new spending.
        List<Expense> expenses = expenseRepository
                .findByGroupIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        groupId, start, end);

        BigDecimal total = ZERO;
        BigDecimal yourShare = ZERO;
        Map<ExpenseCategory, BigDecimal> totals = new EnumMap<>(ExpenseCategory.class);
        Map<ExpenseCategory, Integer> counts = new EnumMap<>(ExpenseCategory.class);

        for (Expense expense : expenses) {
            total = total.add(expense.getAmount());
            totals.merge(expense.getCategory(), expense.getAmount(), BigDecimal::add);
            counts.merge(expense.getCategory(), 1, Integer::sum);

            BigDecimal share = expense.getShares().get(userId);
            if (share != null) {
                yourShare = yourShare.add(share);
            }
        }

        // EnumMap iterates in enum order, so equal totals keep a fixed order.
        List<CategoryTotalResponse> categories = new ArrayList<>();
        for (Map.Entry<ExpenseCategory, BigDecimal> entry : totals.entrySet()) {
            categories.add(new CategoryTotalResponse(
                    entry.getKey(), counts.get(entry.getKey()), entry.getValue()));
        }
        categories.sort(Comparator.comparing(CategoryTotalResponse::total).reversed());

        return new MonthlySummaryResponse(
                yearMonth.toString(), zone.getId(), expenses.size(),
                total, yourShare, categories);
    }

    // ------------------------------------------------------------------ parsing

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    static String parseKeyword(String keyword) {
        if (isBlank(keyword)) {
            return null;
        }
        String trimmed = keyword.trim();
        if (trimmed.length() > MAX_KEYWORD_LENGTH) {
            throw badRequest("Search text can be at most "
                    + MAX_KEYWORD_LENGTH + " characters");
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    static TransactionType parseType(String type) {
        if (isBlank(type) || type.trim().equalsIgnoreCase("ALL")) {
            return null;
        }
        try {
            return TransactionType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw badRequest("type must be EXPENSE, SETTLEMENT or ALL");
        }
    }

    static ExpenseCategory parseCategory(String category) {
        if (isBlank(category)) {
            return null;
        }
        try {
            return ExpenseCategory.valueOf(category.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw badRequest("category must be FOOD, HOUSING, TRANSPORTATION, "
                    + "ENTERTAINMENT or OTHER");
        }
    }

    static Sort parseSort(String sort) {
        if (isBlank(sort)) {
            return Sort.NEWEST;
        }
        try {
            return Sort.valueOf(sort.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw badRequest("sort must be NEWEST, OLDEST, AMOUNT_HIGH or AMOUNT_LOW");
        }
    }

    static ZoneId parseZone(String timeZone) {
        if (isBlank(timeZone)) {
            return DEFAULT_TIME_ZONE;
        }
        try {
            return ZoneId.of(timeZone.trim());
        } catch (DateTimeException exception) {
            throw badRequest("timeZone must be a time zone id such as America/Los_Angeles");
        }
    }

    static YearMonth parseMonth(String month, ZoneId zone) {
        if (isBlank(month)) {
            return YearMonth.now(zone);
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException exception) {
            throw badRequest("month must look like 2026-11");
        }
    }

    // ------------------------------------------------------------------ mapping

    private static MemberResponse toMember(User user) {
        return new MemberResponse(user.getId(), user.getName(), user.getEmail());
    }

    static String settlementTitle(Settlement settlement) {
        return settlement.getPayer().getName() + " paid " + settlement.getPayee().getName();
    }

    private static HistoryItemResponse toItem(Expense expense) {
        return new HistoryItemResponse(
                TransactionType.EXPENSE,
                expense.getId(),
                expense.getTitle(),
                expense.getAmount(),
                expense.getCategory(),
                toMember(expense.getPayer()),
                null,
                expense.getParticipants().size(),
                expense.getNote(),
                expense.getCreatedAt(),
                expense.isSettled());
    }

    private static HistoryItemResponse toItem(Settlement settlement) {
        return new HistoryItemResponse(
                TransactionType.SETTLEMENT,
                settlement.getId(),
                settlementTitle(settlement),
                settlement.getAmount(),
                null,
                toMember(settlement.getPayer()),
                toMember(settlement.getPayee()),
                2,
                null,
                settlement.getCreatedAt(),
                false);
    }
}
