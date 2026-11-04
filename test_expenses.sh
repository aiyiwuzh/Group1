#!/usr/bin/env bash

# End-to-end checks for Expense Management (Section 2).
# Requires the backend running on localhost:8080 (docker compose up -d).

API="http://localhost:8080/api"
PASS=0
FAIL=0
RUN_ID="$(date +%s)"
PASSWORD="TestPassword123!"

# Register and log in a user; prints "<userId> <token>"
make_user() {
    name="$1"
    n="$2"
    email="exp${RUN_ID}${n}@usc.edu"
    usc_id="$(printf '%010d' "$(( (RUN_ID * 10 + n) % 10000000000 ))")"

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

read -r ALICE ALICE_TOKEN ALICE_EMAIL <<< "$(make_user Alice 1)"
read -r BOB BOB_TOKEN BOB_EMAIL <<< "$(make_user Bob 2)"
read -r CAROL CAROL_TOKEN CAROL_EMAIL <<< "$(make_user Carol 3)"
read -r OUT OUT_TOKEN OUT_EMAIL <<< "$(make_user Outsider 4)"

if [ -z "$ALICE_TOKEN" ] || [ -z "$OUT_TOKEN" ]; then
    echo "Could not register/log in test users. Is the backend running?"
    exit 1
fi

# Alice creates a group with Bob and Carol
call POST /groups "$ALICE_TOKEN" '{"name":"Expense Test Group"}'
GROUP=$(echo "$BODY" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
call POST "/groups/$GROUP/members" "$ALICE_TOKEN" "{\"email\":\"$BOB_EMAIL\"}"
call POST "/groups/$GROUP/members" "$ALICE_TOKEN" "{\"email\":\"$CAROL_EMAIL\"}"

expense() {
    # title amount payerId participantIds [category]
    category=${5:+,\"category\":\"$5\"}
    echo "{\"title\":\"$1\",\"amount\":$2,\"payerId\":$3,\"participantIds\":[$4]$category}"
}

# --- Create ---
call POST "/groups/$GROUP/expenses" "$ALICE_TOKEN" \
    "$(expense "Groceries" 42.5 "$ALICE" "$ALICE,$BOB,$CAROL" FOOD)"
check "Create expense" 201
EXPENSE=$(echo "$BODY" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
CREATED_AT=$(echo "$BODY" | sed -n 's/.*"createdAt":"\([^"]*\)".*/\1/p')
check_body "Amount stored with 2 decimals" '"amount":42.50'

call POST "/groups/$GROUP/expenses" "$ALICE_TOKEN" \
    "$(expense "Uber" 18 "$BOB" "$BOB,$CAROL")"
check "Creator records another payer without participating" 201
check_body "Category defaults to OTHER" '"category":"OTHER"'

call POST "/groups/$GROUP/expenses" "$ALICE_TOKEN" "$(expense "Zero" 0 "$ALICE" "$ALICE,$BOB")"
check "Amount of 0 rejected" 400

call POST "/groups/$GROUP/expenses" "$ALICE_TOKEN" "$(expense "Neg" -5 "$ALICE" "$ALICE,$BOB")"
check "Negative amount rejected" 400

call POST "/groups/$GROUP/expenses" "$ALICE_TOKEN" "$(expense "Cents" 10.005 "$ALICE" "$ALICE,$BOB")"
check "Three decimal places rejected" 400

call POST "/groups/$GROUP/expenses" "$ALICE_TOKEN" "$(expense "Solo" 10 "$ALICE" "$ALICE")"
check "Single participant rejected" 400

call POST "/groups/$GROUP/expenses" "$ALICE_TOKEN" "$(expense "NoPayer" 10 "$ALICE" "$BOB,$CAROL")"
check "Payer missing from participants rejected" 400
check_body "Error message returned" "payer must be included"

call POST "/groups/$GROUP/expenses" "$ALICE_TOKEN" "$(expense "Outside" 10 "$OUT" "$OUT,$ALICE")"
check "Non-member payer rejected" 400

call POST "/groups/$GROUP/expenses" "$ALICE_TOKEN" "$(expense "Outside" 10 "$ALICE" "$ALICE,$OUT")"
check "Non-member participant rejected" 400

call POST "/groups/$GROUP/expenses" "$ALICE_TOKEN" "$(expense "   " 10 "$ALICE" "$ALICE,$BOB")"
check "Blank title rejected" 400

call POST "/groups/$GROUP/expenses" "$OUT_TOKEN" "$(expense "Sneaky" 10 "$ALICE" "$ALICE,$BOB")"
check "Non-member cannot create" 403

call POST "/groups/$GROUP/expenses" "" "$(expense "NoAuth" 10 "$ALICE" "$ALICE,$BOB")"
check "Logged-out user cannot create" 401

# --- View ---
call GET "/groups/$GROUP/expenses" "$BOB_TOKEN"
check "Member can list group expenses" 200
check_body "Newest expense listed first" '^\[{"id":[0-9]*,"groupId":[0-9]*,"title":"Uber"'

call GET "/expenses/$EXPENSE" "$BOB_TOKEN"
check "Member can view expense" 200
check_body "Non-creator cannot edit" '"canEdit":false'

call GET "/expenses/$EXPENSE" "$OUT_TOKEN"
check "Non-member cannot view expense" 403

call GET "/expenses/99999999" "$ALICE_TOKEN"
check "Missing expense returns 404" 404

# --- Edit ---
call PUT "/expenses/$EXPENSE" "$BOB_TOKEN" "$(expense "Hacked" 1 "$BOB" "$ALICE,$BOB")"
check "Non-creator cannot edit" 403

call PUT "/expenses/$EXPENSE" "$ALICE_TOKEN" \
    "$(expense "Groceries + snacks" 55.25 "$CAROL" "$ALICE,$CAROL" FOOD)"
check "Creator edits payer, participants, amount" 200
check_body "Edited amount saved" '"amount":55.25'
check_body "Creation date unchanged after edit" "\"createdAt\":\"$CREATED_AT\""

call PUT "/expenses/$EXPENSE" "$ALICE_TOKEN" "$(expense "Bad edit" 10 "$ALICE" "$BOB,$CAROL")"
check "Edit still enforces payer rule" 400

call GET "/expenses/$EXPENSE" "$ALICE_TOKEN"
check_body "Edit persisted after reload" '"title":"Groceries + snacks"'

# --- Delete ---
call DELETE "/expenses/$EXPENSE" "$BOB_TOKEN"
check "Non-creator cannot delete" 403

call DELETE "/expenses/$EXPENSE" "$ALICE_TOKEN"
check "Creator deletes expense" 204

call GET "/expenses/$EXPENSE" "$ALICE_TOKEN"
check "Deleted expense is gone" 404

call POST "/groups/$GROUP/expenses" "$ALICE_TOKEN" "$(expense "Pet care" 10 "$ALICE" "$ALICE,$BOB" PETS)"
check "Custom category accepted" 201
check_body "Custom category saved" '"category":"PETS"'

echo ""
echo "Results: $PASS passed, $FAIL failed"

if [ "$FAIL" -gt 0 ]; then
    exit 1
fi
