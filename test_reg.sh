
#!/usr/bin/env bash

BASE="http://localhost:8080/api/auth/register"
PASS=0
FAIL=0

# Unique test account for this run
RUN_ID="$(date +%s)"
EMAIL="test${RUN_ID}@usc.edu"
USC_ID="$(printf '%010d' "$((RUN_ID % 10000000000))")"

test_request() {
    name="$1"
    expected="$2"
    body="$3"

    actual=$(curl -s -o /dev/null -w "%{http_code}" \
        -X POST "$BASE" \
        -H "Content-Type: application/json" \
        -d "$body")

    if [ "$actual" = "$expected" ]; then
        echo "PASS: $name ($actual)"
        PASS=$((PASS + 1))
    else
        echo "FAIL: $name (expected $expected, got $actual)"
        FAIL=$((FAIL + 1))
    fi
}

test_request "Valid registration" 201 \
    "{\"name\":\"Test User\",\"email\":\"$EMAIL\",\"uscId\":\"$USC_ID\",\"password\":\"TestPassword123!\"}"

test_request "Duplicate email" 409 \
    "{\"name\":\"Duplicate\",\"email\":\"$EMAIL\",\"uscId\":\"0000000000\",\"password\":\"TestPassword123!\"}"

test_request "Duplicate USC ID" 409 \
    "{\"name\":\"Duplicate\",\"email\":\"another${RUN_ID}@usc.edu\",\"uscId\":\"$USC_ID\",\"password\":\"TestPassword123!\"}"

test_request "Invalid email" 400 \
    '{"name":"Invalid","email":"test@gmail.com","uscId":"9000000003","password":"TestPassword123!"}'

test_request "Short USC ID" 400 \
    '{"name":"Invalid","email":"short@usc.edu","uscId":"12345","password":"TestPassword123!"}'

test_request "Letters in USC ID" 400 \
    '{"name":"Invalid","email":"letters@usc.edu","uscId":"12345abcde","password":"TestPassword123!"}'

test_request "Missing name" 400 \
    '{"email":"noname@usc.edu","uscId":"9000000004","password":"TestPassword123!"}'

test_request "Missing password" 400 \
    '{"name":"Invalid","email":"nopassword@usc.edu","uscId":"9000000005"}'

test_request "Empty JSON" 400 '{}'

echo ""
echo "Results: $PASS passed, $FAIL failed"

if [ "$FAIL" -gt 0 ]; then
    exit 1
fi
