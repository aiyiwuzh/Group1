#!/usr/bin/env bash

# End-to-end checks for History & Monthly Summary (Section 5).
# Requires the backend running on localhost:8080 (docker compose up -d).

API="http://localhost:8080/api"
PASS=0
FAIL=0
RUN_ID="$(date +%s)"
PASSWORD="TestPassword123!"

# Register and log in a user; prints "<userId> <token> <email>"
make_user() {
    name="$1"
    n="$2"
    email="hist${RUN_ID}${n}@usc.edu"
    usc_id="$(printf '%010d' "$(( (RUN_ID * 10 + n + 5) % 10000000000 ))")"

    curl -s -o /dev/null -X POST "$API/auth/register" \
        -H "Content-Type: application/json" \
        -d "{\"name\":\"$name\",\"email\":\"$email\",\"uscId\":\"$usc_id\",\"password\":\"$PASSWORD\"}"

    login=$(curl -s -X POST "$API/auth/login" \
        -H "Content-Type: application/json" \
        -d "{\"email\":\"$email\",\"password\":\"$PASSWORD\"}")

    token=$(echo "$login" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
    user_id=$(echo "$login" | grep -o '"userId":[0-9]*' | cut -d: -f2)
    echo "$user_id $token $email"
}

# call METHOD PATH TOKEN [BODY] -> sets STATUS and BODY
call() {
    response=$(curl -s -w "\n%{http_code}" -X "$1" "$API$2" \
        -H "Authorization: Bearer $3" \
        -H "Content-Type: application/json" \
        ${4:+-d "$4"})
    STATUS=$(echo "$response" | tail -n 1)
    BODY=$(echo "$response" | sed '$d')
}

check() {
    name="$1"
    expected="$2"

    if [ "$STATUS" = "$expected" ]; then
        echo "PASS: $name ($STATUS)"
        PASS=$((PASS + 1))
    else
        echo "FAIL: $name (expected $expected, got $STATUS) $BODY"
        FAIL=$((FAIL + 1))
    fi
}

check_body() {
    name="$1"
    pattern="$2"

    if echo "$BODY" | grep -q "$pattern"; then
        echo "PASS: $name"
        PASS=$((PASS + 1))
    else
        echo "FAIL: $name (missing $pattern) $BODY"
        FAIL=$((FAIL + 1))
    fi
}

# Number of history rows in BODY
check_items() {
    name="$1"
    expected="$2"
    count=$(echo "$BODY" | grep -o '"type":"[A-Z]*"' | wc -l | tr -d ' ')

    if [ "$count" = "$expected" ]; then
        echo "PASS: $name ($count rows)"
        PASS=$((PASS + 1))
    else
        echo "FAIL: $name (expected $expected rows, got $count) $BODY"
        FAIL=$((FAIL + 1))
    fi
}

read -r ALICE ALICE_TOKEN ALICE_EMAIL <<< "$(make_user Alice 1)"
read -r BOB BOB_TOKEN BOB_EMAIL <<< "$(make_user Bob 2)"
read -r CAROL CAROL_TOKEN CAROL_EMAIL <<< "$(make_user Carol 3)"
read -r OUT OUT_TOKEN OUT_EMAIL <<< "$(make_user Outsider 4)"

if [ -z "$ALICE_TOKEN" ] || [ -z "$OUT_TOKEN" ]; then
    echo "Could not register/log in test users. Is the backend running?"
    exit 1
fi

# Alice creates a group with Bob and Carol
call POST /groups "$ALICE_TOKEN" '{"name":"History Test Group"}'
GROUP=$(echo "$BODY" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
call POST "/groups/$GROUP/members" "$ALICE_TOKEN" "{\"email\":\"$BOB_EMAIL\"}"
call POST "/groups/$GROUP/members" "$ALICE_TOKEN" "{\"email\":\"$CAROL_EMAIL\"}"

expense() {
    # title amount payerId participantIds category [note]
    note=${6:+,\"note\":\"$6\"}
    echo "{\"title\":\"$1\",\"amount\":$2,\"payerId\":$3,\"participantIds\":[$4],\"category\":\"$5\"$note}"
}

# --- Seed: three expenses, then one settlement (created last, so newest) ---
call POST "/groups/$GROUP/expenses" "$ALICE_TOKEN" \
    "$(expense "Groceries" 30 "$ALICE" "$ALICE,$BOB" FOOD)"
check "Seed: groceries" 201

call POST "/groups/$GROUP/expenses" "$ALICE_TOKEN" \
    "$(expense "Uber" 18 "$BOB" "$ALICE,$BOB" TRANSPORTATION)"
check "Seed: uber" 201
UBER=$(echo "$BODY" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)

call POST "/groups/$GROUP/expenses" "$CAROL_TOKEN" \
    "$(expense "Concert" 40 "$CAROL" "$BOB,$CAROL" ENTERTAINMENT "front row seats")"
check "Seed: concert" 201

# Bob owes Alice 15.00 for groceries and is owed 9.00 for the Uber: net 6.00
call POST "/groups/$GROUP/settlements" "$BOB_TOKEN" \
    "{\"payeeId\":$ALICE,\"amount\":5}"
check "Seed: Bob pays Alice" 201

# --- History list ---
call GET "/groups/$GROUP/history" "$BOB_TOKEN"
check "Member can view history" 200
check_items "Expenses and settlements listed together" 4
check_body "Newest first: the settlement comes first" '"items":\[{"type":"SETTLEMENT"'
check_body "Settlement title names both people" '"title":"Bob paid Alice"'
check_body "Total count before filters" '"totalCount":4'
check_body "Members included for the filter" '"members":\[{"id":'

call GET "/groups/$GROUP/history?sort=OLDEST" "$BOB_TOKEN"
check_body "Oldest first starts with groceries" '"items":\[{"type":"EXPENSE","id":[0-9]*,"title":"Groceries"'

call GET "/groups/$GROUP/history?sort=AMOUNT_HIGH" "$BOB_TOKEN"
check_body "Largest amount first" '"items":\[{"type":"EXPENSE","id":[0-9]*,"title":"Concert"'

# --- Search and filters ---
call GET "/groups/$GROUP/history?q=FRONT%20ROW" "$BOB_TOKEN"
check "Keyword search" 200
check_items "Search matches the note, ignoring case" 1
check_body "Search found the concert" '"title":"Concert"'

call GET "/groups/$GROUP/history?type=SETTLEMENT" "$BOB_TOKEN"
check_items "Type filter: settlements only" 1

call GET "/groups/$GROUP/history?type=EXPENSE" "$BOB_TOKEN"
check_items "Type filter: expenses only" 3

call GET "/groups/$GROUP/history?category=FOOD" "$BOB_TOKEN"
check_items "Category filter" 1
check_body "Category filter found groceries" '"title":"Groceries"'

call GET "/groups/$GROUP/history?memberId=$CAROL" "$BOB_TOKEN"
check_items "Member filter" 1

call GET "/groups/$GROUP/history?q=sushi" "$BOB_TOKEN"
check_items "No matches" 0

call GET "/groups/$GROUP/history?sort=SIDEWAYS" "$BOB_TOKEN"
check "Unknown sort rejected" 400

call GET "/groups/$GROUP/history?category=PETS" "$BOB_TOKEN"
check "Unknown category rejected" 400

call GET "/groups/$GROUP/history?memberId=$OUT" "$BOB_TOKEN"
check "Filtering by a non-member rejected" 400

call GET "/groups/$GROUP/history" "$OUT_TOKEN"
check "Non-member cannot view history" 403

call GET "/groups/$GROUP/history" ""
check "Logged-out user cannot view history" 401

call GET "/groups/99999999/history" "$ALICE_TOKEN"
check "Missing group returns 404" 404

# --- Deleted items disappear ---
call DELETE "/expenses/$UBER" "$ALICE_TOKEN"
check "Delete the uber expense" 204

call GET "/groups/$GROUP/history" "$BOB_TOKEN"
check_items "Deleted expense is gone from history" 3
check_body "Total count drops" '"totalCount":3'

# --- Monthly summary (UTC, so "this month" matches the server clock) ---
MONTH="$(date -u +%Y-%m)"

call GET "/groups/$GROUP/summary/monthly?month=$MONTH&timeZone=UTC" "$ALICE_TOKEN"
check "Member can view monthly summary" 200
check_body "Month echoed" "\"month\":\"$MONTH\""
check_body "Settlements are not counted" '"expenseCount":2'
check_body "Total spending" '"totalSpending":70.00'
check_body "Alice's own share" '"yourShare":15.00'
check_body "Largest category first" '"categories":\[{"category":"ENTERTAINMENT","expenseCount":1,"total":40.00}'

call GET "/groups/$GROUP/summary/monthly?timeZone=UTC" "$ALICE_TOKEN"
check_body "Month defaults to the current month" "\"month\":\"$MONTH\""

call GET "/groups/$GROUP/summary/monthly?month=2000-01&timeZone=UTC" "$ALICE_TOKEN"
check_body "Empty month" '"expenseCount":0'

call GET "/groups/$GROUP/summary/monthly?month=13-2026" "$ALICE_TOKEN"
check "Malformed month rejected" 400

call GET "/groups/$GROUP/summary/monthly?timeZone=Mars/Olympus" "$ALICE_TOKEN"
check "Unknown time zone rejected" 400

call GET "/groups/$GROUP/summary/monthly" "$OUT_TOKEN"
check "Non-member cannot view summary" 403

echo ""
echo "Results: $PASS passed, $FAIL failed"

if [ "$FAIL" -gt 0 ]; then
    exit 1
fi
