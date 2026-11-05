-- SplitSmart Project 2.3 local DEMO seed (V9, for V1-V8 schema).
-- Flyway runs this migration ONCE per database, not on each Docker restart.
-- Do not deploy this migration to a production database.
-- All four fictional accounts have the DEVELOPMENT-ONLY password: password
-- BCrypt hash generated for that password with cost 10.
-- No fixed user/group/expense IDs are assumed.

SET @demo_hash = '$2a$10$WHvhrNR4fsRU5OypxAQ4C.hEc.JS0UZzUf0IndLCTIZZ.8kMQQgWO';

INSERT INTO users (name, email, usc_id, password_hash) VALUES
 ('Bob', 'bob@usc.edu', '1111111111', @demo_hash),
 ('Alice', 'alice@usc.edu', '2222222222', @demo_hash),
 ('Carol', 'carol@usc.edu', '3333333333', @demo_hash),
 ('David', 'david@usc.edu', '4444444444', @demo_hash)
ON DUPLICATE KEY UPDATE id = id;

SET @bob = (SELECT id FROM users WHERE email = 'bob@usc.edu');
SET @alice = (SELECT id FROM users WHERE email = 'alice@usc.edu');
SET @carol = (SELECT id FROM users WHERE email = 'carol@usc.edu');
SET @david = (SELECT id FROM users WHERE email = 'david@usc.edu');

-- Avoid duplicate groups if they were manually created previously.
INSERT INTO `groups` (name, description, owner_id)
SELECT 'Group 1', 'Splitting and balances demo', @bob
WHERE NOT EXISTS (SELECT 1 FROM `groups` WHERE name='Group 1' AND owner_id=@bob);
INSERT INTO `groups` (name, description, owner_id)
SELECT 'Group 2', 'Monthly summaries and settlements demo', @alice
WHERE NOT EXISTS (SELECT 1 FROM `groups` WHERE name='Group 2' AND owner_id=@alice);
INSERT INTO `groups` (name, description, owner_id)
SELECT 'Group 3 (dummy tester)', 'Disposable group for deletion demonstration', @alice
WHERE NOT EXISTS (SELECT 1 FROM `groups` WHERE name='Group 3 (dummy tester)' AND owner_id=@alice);

SET @g1 = (SELECT MIN(id) FROM `groups` WHERE name='Group 1' AND owner_id=@bob);
SET @g2 = (SELECT MIN(id) FROM `groups` WHERE name='Group 2' AND owner_id=@alice);
SET @g3 = (SELECT MIN(id) FROM `groups` WHERE name='Group 3 (dummy tester)' AND owner_id=@alice);

INSERT IGNORE INTO memberships (group_id, user_id) VALUES
 (@g1,@bob),(@g1,@alice),(@g1,@carol),
 (@g2,@alice),(@g2,@bob),(@g2,@carol),(@g2,@david),
 (@g3,@alice),(@g3,@bob);

-- The demo records below are inserted once as part of V9.
-- They use fixed demo timestamps to permit multi-month summaries.
-- The Android app should still assign the current local date to NEW expenses.
-- Group 1: EQUAL $30, EQUAL $10 rounding, EXACT $30, PERCENTAGE $30.
-- Group 2: November Food $30 + Transportation $20, a $5 settlement,
--          and October Entertainment $12.
-- Group 3: disposable $8 expense + $2 settlement (delete group last).

INSERT INTO expenses
(group_id,title,amount,category,payer_id,creator_id,note,created_at,updated_at,settled,split_method)
VALUES
(@g1,'Equal dinner',30.00,'FOOD',@alice,@alice,'Three-way equal split','2026-11-02 12:00:00','2026-11-02 12:00:00',b'0','EQUAL');
SET @e1 = LAST_INSERT_ID();
INSERT INTO expense_participants VALUES (@e1,@alice),(@e1,@bob),(@e1,@carol);
INSERT INTO expense_shares VALUES (@e1,@alice,10.00),(@e1,@bob,10.00),(@e1,@carol,10.00);

INSERT INTO expenses
(group_id,title,amount,category,payer_id,creator_id,note,created_at,updated_at,settled,split_method)
VALUES
(@g1,'Rounding snacks',10.00,'FOOD',@bob,@bob,'Remainder cent example','2026-11-03 10:00:00','2026-11-03 10:00:00',b'0','EQUAL');
SET @e2 = LAST_INSERT_ID();
INSERT INTO expense_participants VALUES (@e2,@alice),(@e2,@bob),(@e2,@carol);
-- Lowest user ID receives the remainder cent (Bob=111..., Alice=222..., Carol=333...).
INSERT INTO expense_shares VALUES (@e2,@bob,3.34),(@e2,@alice,3.33),(@e2,@carol,3.33);

INSERT INTO expenses
(group_id,title,amount,category,payer_id,creator_id,note,created_at,updated_at,settled,split_method)
VALUES
(@g1,'Exact tickets',30.00,'ENTERTAINMENT',@bob,@bob,'5 + 10 + 15','2026-11-03 13:00:00','2026-11-03 13:00:00',b'0','EXACT');
SET @e3 = LAST_INSERT_ID();
INSERT INTO expense_participants VALUES (@e3,@alice),(@e3,@bob),(@e3,@carol);
INSERT INTO expense_shares VALUES (@e3,@alice,5.00),(@e3,@bob,10.00),(@e3,@carol,15.00);
INSERT INTO expense_split_values VALUES (@e3,@alice,5.00),(@e3,@bob,10.00),(@e3,@carol,15.00);

INSERT INTO expenses
(group_id,title,amount,category,payer_id,creator_id,note,created_at,updated_at,settled,split_method)
VALUES
(@g1,'Percentage supplies',30.00,'OTHER',@carol,@carol,'20/30/50 percentage','2026-11-04 09:00:00','2026-11-04 09:00:00',b'0','PERCENTAGE');
SET @e4 = LAST_INSERT_ID();
INSERT INTO expense_participants VALUES (@e4,@alice),(@e4,@bob),(@e4,@carol);
INSERT INTO expense_shares VALUES (@e4,@alice,6.00),(@e4,@bob,9.00),(@e4,@carol,15.00);
INSERT INTO expense_split_values VALUES (@e4,@alice,20.00),(@e4,@bob,30.00),(@e4,@carol,50.00);

-- Group 2 November: Alice pays $30 Food for Alice/Bob; Bob pays $20
-- Transportation for Alice/Bob. Net Bob -> Alice = $5 before settlement.
INSERT INTO expenses
(group_id,title,amount,category,payer_id,creator_id,note,created_at,updated_at,settled,split_method)
VALUES
(@g2,'November groceries',30.00,'FOOD',@alice,@alice,'November monthly summary','2026-11-01 11:00:00','2026-11-01 11:00:00',b'0','EQUAL');
SET @e5 = LAST_INSERT_ID();
INSERT INTO expense_participants VALUES (@e5,@alice),(@e5,@bob);
INSERT INTO expense_shares VALUES (@e5,@alice,15.00),(@e5,@bob,15.00);

INSERT INTO expenses
(group_id,title,amount,category,payer_id,creator_id,note,created_at,updated_at,settled,split_method)
VALUES
(@g2,'November rideshare',20.00,'TRANSPORTATION',@bob,@bob,'November monthly summary','2026-11-02 11:00:00','2026-11-02 11:00:00',b'0','EQUAL');
SET @e6 = LAST_INSERT_ID();
INSERT INTO expense_participants VALUES (@e6,@alice),(@e6,@bob);
INSERT INTO expense_shares VALUES (@e6,@alice,10.00),(@e6,@bob,10.00);

INSERT INTO settlements
(group_id,payer_id,payee_id,amount,created_at,updated_at,creator_id,note)
VALUES
(@g2,@bob,@alice,5.00,'2026-11-03 15:00:00','2026-11-03 15:00:00',@bob,'Bob settled November balance');

INSERT INTO expenses
(group_id,title,amount,category,payer_id,creator_id,note,created_at,updated_at,settled,split_method)
VALUES
(@g2,'October movie',12.00,'ENTERTAINMENT',@alice,@alice,'Prior-month record','2026-10-15 18:00:00','2026-10-15 18:00:00',b'0','EQUAL');
SET @e7 = LAST_INSERT_ID();
INSERT INTO expense_participants VALUES (@e7,@alice),(@e7,@david);
INSERT INTO expense_shares VALUES (@e7,@alice,6.00),(@e7,@david,6.00);

-- Group 3 is intentionally disposable; delete it during the demo to show
-- cascading removal of expenses, shares, participants, and settlements.
INSERT INTO expenses
(group_id,title,amount,category,payer_id,creator_id,note,created_at,updated_at,settled,split_method)
VALUES
(@g3,'Disposable snack',8.00,'FOOD',@alice,@alice,'Delete with disposable group','2026-11-03 16:00:00','2026-11-03 16:00:00',b'0','EQUAL');
SET @e8 = LAST_INSERT_ID();
INSERT INTO expense_participants VALUES (@e8,@alice),(@e8,@bob);
INSERT INTO expense_shares VALUES (@e8,@alice,4.00),(@e8,@bob,4.00);
INSERT INTO settlements
(group_id,payer_id,payee_id,amount,created_at,updated_at,creator_id,note)
VALUES
(@g3,@bob,@alice,2.00,'2026-11-04 10:00:00','2026-11-04 10:00:00',@alice,'Disposable partial settlement');
