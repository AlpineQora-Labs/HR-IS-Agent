-- =====================================================================
-- Olivia · seed data (V2)  — COMPLETE, FK-consistent seed for ALL tables
-- Realistic, coherent demo data for a Bank-of-America-style enterprise
-- recruiting program. FK-safe order (parents before children).
-- Explicit UUID literals so child rows can reference parents.
-- Runs on PostgreSQL 16 after V1, before V3. No COMMIT (Flyway owns txn).
--
-- UUID prefix scheme (last group encodes the row ordinal):
--   recruiter_user      10000000-...
--   skill               20000000-...
--   job                 30000000-...
--   knockout_question   40000000-...
--   candidate           50000000-...
--   application         60000000-...
--   screening_answer    61000000-...
--   conversation        70000000-...
--   message             71000000-...
--   interview_slot      80000000-...
--   interview           81000000-...
--   assessment          82000000-...
--   offer               83000000-...
--   onboarding_task     84000000-...
--   talent_pool         90000000-...
--   nurture_campaign    91000000-...
--   referral            92000000-...
--   recruiting_event    93000000-...
--   survey              94000000-...
--   survey_response     95000000-...
--   internal_employee   a0000000-...
--   mobility_match      a1000000-...
--   copilot_artifact    b0000000-...
--   integration         b1000000-...
--   audit_event         b2000000-...
--   bias_report         b3000000-...
--   metric_daily        b4000000-...
-- =====================================================================

-- ---------------------------------------------------------------------
-- recruiter_user  (6)  roles ADMIN/RECRUITER/HIRING_MANAGER/COORDINATOR/VIEWER
-- ---------------------------------------------------------------------
insert into recruiter_user (id, name, initials, email, role, title) values
 ('10000000-0000-0000-0000-000000000001','Priya Raman','PR','priya.raman@bofa.com','ADMIN','Director, Talent Acquisition'),
 ('10000000-0000-0000-0000-000000000002','Marcus Bell','MB','marcus.bell@bofa.com','RECRUITER','Senior Recruiter'),
 ('10000000-0000-0000-0000-000000000003','Elena Vasquez','EV','elena.vasquez@bofa.com','RECRUITER','Technical Recruiter'),
 ('10000000-0000-0000-0000-000000000004','David Okafor','DO','david.okafor@bofa.com','HIRING_MANAGER','VP, Consumer Banking'),
 ('10000000-0000-0000-0000-000000000005','Sarah Chen','SC','sarah.chen@bofa.com','COORDINATOR','Recruiting Coordinator'),
 ('10000000-0000-0000-0000-000000000006','James Whitfield','JW','james.whitfield@bofa.com','VIEWER','HR Analyst');

-- ---------------------------------------------------------------------
-- skill  (24)  categories: Technical, Operational, Leadership, Compliance, Customer, Data
-- (note: 6 categories x 4 = 24)
-- ---------------------------------------------------------------------
insert into skill (id, name, category) values
 -- Technical
 ('20000000-0000-0000-0000-000000000001','Java','Technical'),
 ('20000000-0000-0000-0000-000000000002','Python','Technical'),
 ('20000000-0000-0000-0000-000000000003','React','Technical'),
 ('20000000-0000-0000-0000-000000000004','Spring Boot','Technical'),
 -- Data
 ('20000000-0000-0000-0000-000000000005','SQL','Data'),
 ('20000000-0000-0000-0000-000000000006','Data Modeling','Data'),
 ('20000000-0000-0000-0000-000000000007','Machine Learning','Data'),
 ('20000000-0000-0000-0000-000000000008','Tableau','Data'),
 -- Operational
 ('20000000-0000-0000-0000-000000000009','Cash Handling','Operational'),
 ('20000000-0000-0000-0000-000000000010','Branch Operations','Operational'),
 ('20000000-0000-0000-0000-000000000011','Loan Processing','Operational'),
 ('20000000-0000-0000-0000-000000000012','Process Improvement','Operational'),
 -- Leadership
 ('20000000-0000-0000-0000-000000000013','Team Leadership','Leadership'),
 ('20000000-0000-0000-0000-000000000014','Coaching','Leadership'),
 ('20000000-0000-0000-0000-000000000015','Stakeholder Management','Leadership'),
 ('20000000-0000-0000-0000-000000000016','Strategic Planning','Leadership'),
 -- Compliance
 ('20000000-0000-0000-0000-000000000017','AML Compliance','Compliance'),
 ('20000000-0000-0000-0000-000000000018','KYC','Compliance'),
 ('20000000-0000-0000-0000-000000000019','Regulatory Reporting','Compliance'),
 ('20000000-0000-0000-0000-000000000020','Risk Management','Compliance'),
 -- Customer
 ('20000000-0000-0000-0000-000000000021','Customer Service','Customer'),
 ('20000000-0000-0000-0000-000000000022','Relationship Banking','Customer'),
 ('20000000-0000-0000-0000-000000000023','Sales','Customer'),
 ('20000000-0000-0000-0000-000000000024','Financial Advising','Customer');

-- ---------------------------------------------------------------------
-- skill_adjacency  (12 pairs)
-- ---------------------------------------------------------------------
insert into skill_adjacency (skill_id, related_skill_id, weight) values
 ('20000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000004',0.90), -- Java -> Spring Boot
 ('20000000-0000-0000-0000-000000000004','20000000-0000-0000-0000-000000000001',0.90), -- Spring Boot -> Java
 ('20000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000007',0.80), -- Python -> ML
 ('20000000-0000-0000-0000-000000000007','20000000-0000-0000-0000-000000000002',0.80), -- ML -> Python
 ('20000000-0000-0000-0000-000000000005','20000000-0000-0000-0000-000000000006',0.85), -- SQL -> Data Modeling
 ('20000000-0000-0000-0000-000000000005','20000000-0000-0000-0000-000000000008',0.60), -- SQL -> Tableau
 ('20000000-0000-0000-0000-000000000009','20000000-0000-0000-0000-000000000010',0.70), -- Cash Handling -> Branch Ops
 ('20000000-0000-0000-0000-000000000010','20000000-0000-0000-0000-000000000021',0.65), -- Branch Ops -> Customer Service
 ('20000000-0000-0000-0000-000000000017','20000000-0000-0000-0000-000000000018',0.88), -- AML -> KYC
 ('20000000-0000-0000-0000-000000000018','20000000-0000-0000-0000-000000000017',0.88), -- KYC -> AML
 ('20000000-0000-0000-0000-000000000013','20000000-0000-0000-0000-000000000014',0.82), -- Team Leadership -> Coaching
 ('20000000-0000-0000-0000-000000000021','20000000-0000-0000-0000-000000000022',0.78); -- Customer Service -> Relationship Banking

-- ---------------------------------------------------------------------
-- job  (10)  families HOURLY/CORPORATE/EARLY_CAREER/TECH
-- statuses: jobs 1-8 OPEN, job 9 PAUSED, job 10 DRAFT  (+ FILLED handled below: job 8 stays OPEN; use job 9 PAUSED, job 10 DRAFT)
-- To cover FILLED too, job 7 is FILLED.
-- ---------------------------------------------------------------------
insert into job (id, title, department, location, work_mode, employment_type, family, status, openings,
  hiring_manager_id, recruiter_id, summary, description, pay_min, pay_max, pay_period,
  preview_headline, preview_day_in_life, languages, posted_at) values
 ('30000000-0000-0000-0000-000000000001','Personal Banker','Consumer Banking','Charlotte, NC','ONSITE','FULL_TIME','HOURLY','OPEN',8,
   '10000000-0000-0000-0000-000000000004','10000000-0000-0000-0000-000000000002',
   'Be the face of Bank of America in your community.',
   'As a Personal Banker you build lasting relationships, open accounts, and connect clients to the right financial solutions in a high-traffic financial center.',
   24.00,30.00,'HOUR',
   'Help neighbors reach their financial goals','You greet clients at the door, handle deposits and account openings, and partner with specialists to solve money questions all day long.',
   'en,es','2026-06-10 13:00:00+00'),
 ('30000000-0000-0000-0000-000000000002','Teller','Consumer Banking','Tampa, FL','ONSITE','PART_TIME','HOURLY','OPEN',5,
   '10000000-0000-0000-0000-000000000004','10000000-0000-0000-0000-000000000002',
   'Start your banking career at the teller line.',
   'Process transactions accurately, balance your drawer, and deliver friendly service that keeps clients coming back.',
   20.00,24.50,'HOUR',
   'Master the fundamentals of banking','Count your cash drawer, welcome a line of regulars by name, process deposits and withdrawals, and spot opportunities to help.',
   'en,es','2026-06-15 14:00:00+00'),
 ('30000000-0000-0000-0000-000000000003','Branch Operations Manager','Consumer Banking','Phoenix, AZ','ONSITE','FULL_TIME','CORPORATE','OPEN',2,
   '10000000-0000-0000-0000-000000000004','10000000-0000-0000-0000-000000000002',
   'Lead a flagship financial center.',
   'Own the operational health of a busy branch: coach a team of bankers and tellers, ensure compliance, and drive client satisfaction.',
   72000.00,95000.00,'YEAR',
   'Run the branch like it is your own business','You open the center, lead the morning huddle, coach bankers on the floor, review controls, and resolve escalations.',
   'en','2026-06-05 12:00:00+00'),
 ('30000000-0000-0000-0000-000000000004','Senior Software Engineer','Global Technology','New York, NY','HYBRID','FULL_TIME','TECH','OPEN',4,
   '10000000-0000-0000-0000-000000000004','10000000-0000-0000-0000-000000000003',
   'Build the platforms that move trillions.',
   'Design and ship resilient distributed services on our cloud platform supporting consumer and institutional banking at scale.',
   140000.00,185000.00,'YEAR',
   'Engineer banking at planetary scale','You pair on a new payments service, review a teammate''s PR, tune a Kubernetes deployment, and demo a feature to product.',
   'en','2026-06-08 16:00:00+00'),
 ('30000000-0000-0000-0000-000000000005','Data Scientist','Global Risk Analytics','Charlotte, NC','HYBRID','FULL_TIME','TECH','OPEN',3,
   '10000000-0000-0000-0000-000000000004','10000000-0000-0000-0000-000000000003',
   'Turn data into safer banking.',
   'Develop machine learning models for fraud detection and credit risk, partnering with risk and engineering teams.',
   125000.00,165000.00,'YEAR',
   'Fight fraud with models that learn','You explore a new feature set, train a gradient-boosted model, present lift to risk partners, and ship a scoring endpoint.',
   'en','2026-06-12 15:00:00+00'),
 ('30000000-0000-0000-0000-000000000006','Financial Solutions Advisor','Merrill','Boston, MA','HYBRID','FULL_TIME','CORPORATE','OPEN',3,
   '10000000-0000-0000-0000-000000000004','10000000-0000-0000-0000-000000000002',
   'Guide clients toward their financial future.',
   'Provide goals-based advice, build investment plans, and grow a book of business with full Merrill platform support.',
   65000.00,90000.00,'YEAR',
   'Advice that changes lives','You review portfolios, meet with clients on retirement goals, collaborate with specialists, and prospect for new relationships.',
   'en,es','2026-06-18 13:30:00+00'),
 ('30000000-0000-0000-0000-000000000007','Compliance Analyst','Global Compliance','Charlotte, NC','HYBRID','FULL_TIME','CORPORATE','FILLED',2,
   '10000000-0000-0000-0000-000000000004','10000000-0000-0000-0000-000000000003',
   'Keep the bank on the right side of every rule.',
   'Monitor transactions for AML/KYC red flags, investigate alerts, and prepare regulatory reporting.',
   70000.00,92000.00,'YEAR',
   'Protect the bank and its clients','You triage AML alerts, dig into suspicious activity, document findings, and file a regulatory report.',
   'en','2026-06-09 14:30:00+00'),
 ('30000000-0000-0000-0000-000000000008','Software Engineer (New Grad)','Global Technology','Charlotte, NC','HYBRID','FULL_TIME','EARLY_CAREER','OPEN',10,
   '10000000-0000-0000-0000-000000000004','10000000-0000-0000-0000-000000000003',
   'Launch your engineering career at scale.',
   'Join our Technology Development Program, rotate across teams, and build production software with mentorship.',
   95000.00,110000.00,'YEAR',
   'From classroom to codebase','You attend a training cohort session, fix your first production bug, pair with a mentor, and present at the intern showcase.',
   'en','2026-06-20 17:00:00+00'),
 ('30000000-0000-0000-0000-000000000009','Summer Analyst Intern','Global Banking','New York, NY','ONSITE','SEASONAL','EARLY_CAREER','PAUSED',15,
   '10000000-0000-0000-0000-000000000004','10000000-0000-0000-0000-000000000002',
   'A summer that shapes your career.',
   'Ten-week internship across global banking with real deal exposure, training, and a path to full-time offers.',
   45.00,55.00,'HOUR',
   'Wall Street, your summer','You build a model, sit in on a client pitch, attend speaker series, and present your capstone to the desk.',
   'en','2026-06-22 12:00:00+00'),
 ('30000000-0000-0000-0000-000000000010','Wealth Management Associate','Merrill','San Francisco, CA','ONSITE','FULL_TIME','CORPORATE','DRAFT',2,
   '10000000-0000-0000-0000-000000000004','10000000-0000-0000-0000-000000000002',
   'Support top advisors and grow into the role.',
   'Provide operational and client-service support to senior advisors while learning the wealth management craft.',
   60000.00,80000.00,'YEAR',
   'Your apprenticeship in wealth','You prep client review materials, handle service requests, shadow advisor meetings, and learn the platform.',
   'en','2026-06-01 10:00:00+00');

-- ---------------------------------------------------------------------
-- job_skill  (3-5 per OPEN job; OPEN jobs are 1,2,3,4,5,6,8)
-- ---------------------------------------------------------------------
insert into job_skill (job_id, skill_id, weight, required) values
 -- Personal Banker (1)
 ('30000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000021',1.0,true),  -- Customer Service
 ('30000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000022',0.9,true),  -- Relationship Banking
 ('30000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000009',0.8,true),  -- Cash Handling
 ('30000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000023',0.7,false), -- Sales
 ('30000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000010',0.6,false), -- Branch Operations
 -- Teller (2)
 ('30000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000009',1.0,true),  -- Cash Handling
 ('30000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000021',0.9,true),  -- Customer Service
 ('30000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000010',0.6,false), -- Branch Operations
 -- Branch Operations Manager (3)
 ('30000000-0000-0000-0000-000000000003','20000000-0000-0000-0000-000000000010',1.0,true),  -- Branch Operations
 ('30000000-0000-0000-0000-000000000003','20000000-0000-0000-0000-000000000013',0.95,true), -- Team Leadership
 ('30000000-0000-0000-0000-000000000003','20000000-0000-0000-0000-000000000014',0.8,false), -- Coaching
 ('30000000-0000-0000-0000-000000000003','20000000-0000-0000-0000-000000000020',0.7,false), -- Risk Management
 ('30000000-0000-0000-0000-000000000003','20000000-0000-0000-0000-000000000012',0.6,false), -- Process Improvement
 -- Senior Software Engineer (4)
 ('30000000-0000-0000-0000-000000000004','20000000-0000-0000-0000-000000000001',1.0,true),  -- Java
 ('30000000-0000-0000-0000-000000000004','20000000-0000-0000-0000-000000000004',0.95,true), -- Spring Boot
 ('30000000-0000-0000-0000-000000000004','20000000-0000-0000-0000-000000000003',0.8,false), -- React
 ('30000000-0000-0000-0000-000000000004','20000000-0000-0000-0000-000000000005',0.6,false), -- SQL
 -- Data Scientist (5)
 ('30000000-0000-0000-0000-000000000005','20000000-0000-0000-0000-000000000002',1.0,true),  -- Python
 ('30000000-0000-0000-0000-000000000005','20000000-0000-0000-0000-000000000007',1.0,true),  -- Machine Learning
 ('30000000-0000-0000-0000-000000000005','20000000-0000-0000-0000-000000000005',0.85,true), -- SQL
 ('30000000-0000-0000-0000-000000000005','20000000-0000-0000-0000-000000000006',0.7,false), -- Data Modeling
 ('30000000-0000-0000-0000-000000000005','20000000-0000-0000-0000-000000000008',0.6,false), -- Tableau
 -- Financial Solutions Advisor (6)
 ('30000000-0000-0000-0000-000000000006','20000000-0000-0000-0000-000000000024',1.0,true),  -- Financial Advising
 ('30000000-0000-0000-0000-000000000006','20000000-0000-0000-0000-000000000022',0.9,true),  -- Relationship Banking
 ('30000000-0000-0000-0000-000000000006','20000000-0000-0000-0000-000000000023',0.8,false), -- Sales
 ('30000000-0000-0000-0000-000000000006','20000000-0000-0000-0000-000000000021',0.7,false), -- Customer Service
 -- Software Engineer New Grad (8)
 ('30000000-0000-0000-0000-000000000008','20000000-0000-0000-0000-000000000002',0.9,true),  -- Python
 ('30000000-0000-0000-0000-000000000008','20000000-0000-0000-0000-000000000001',0.8,false), -- Java
 ('30000000-0000-0000-0000-000000000008','20000000-0000-0000-0000-000000000003',0.7,false), -- React
 ('30000000-0000-0000-0000-000000000008','20000000-0000-0000-0000-000000000005',0.6,false); -- SQL

-- ---------------------------------------------------------------------
-- knockout_question  (2-3 per OPEN job; OPEN jobs 1,2,3,4,5,6,8)
-- ---------------------------------------------------------------------
insert into knockout_question (id, job_id, ordinal, prompt, answer_type, choices, disqualify_op, disqualify_value, required) values
 ('40000000-0000-0000-0000-000000000001','30000000-0000-0000-0000-000000000001',1,'Are you at least 18 years old?','BOOLEAN',null,'EQ','false',true),
 ('40000000-0000-0000-0000-000000000002','30000000-0000-0000-0000-000000000001',2,'Are you legally authorized to work in the United States?','BOOLEAN',null,'EQ','false',true),
 ('40000000-0000-0000-0000-000000000003','30000000-0000-0000-0000-000000000001',3,'How many months of customer-facing experience do you have?','NUMERIC',null,'LT','6',true),
 ('40000000-0000-0000-0000-000000000004','30000000-0000-0000-0000-000000000002',1,'Are you at least 18 years old?','BOOLEAN',null,'EQ','false',true),
 ('40000000-0000-0000-0000-000000000005','30000000-0000-0000-0000-000000000002',2,'Are you comfortable handling cash and balancing a drawer?','BOOLEAN',null,'EQ','false',true),
 ('40000000-0000-0000-0000-000000000006','30000000-0000-0000-0000-000000000003',1,'Do you have at least 3 years of branch or retail management experience?','BOOLEAN',null,'EQ','false',true),
 ('40000000-0000-0000-0000-000000000007','30000000-0000-0000-0000-000000000003',2,'How many years of people leadership experience do you have?','NUMERIC',null,'LT','3',true),
 ('40000000-0000-0000-0000-000000000008','30000000-0000-0000-0000-000000000004',1,'How many years of professional software engineering experience do you have?','NUMERIC',null,'LT','5',true),
 ('40000000-0000-0000-0000-000000000009','30000000-0000-0000-0000-000000000004',2,'Which best describes your primary backend language?','CHOICE','Java,Python,Go,C#,Other','NOT_IN','Java,Python,Go',true),
 ('40000000-0000-0000-0000-000000000010','30000000-0000-0000-0000-000000000004',3,'Are you authorized to work in the US without sponsorship?','BOOLEAN',null,'EQ','false',false),
 ('40000000-0000-0000-0000-000000000011','30000000-0000-0000-0000-000000000005',1,'Do you have a graduate degree in a quantitative field?','BOOLEAN',null,'EQ','false',false),
 ('40000000-0000-0000-0000-000000000012','30000000-0000-0000-0000-000000000005',2,'How many years of applied machine learning experience do you have?','NUMERIC',null,'LT','2',true),
 ('40000000-0000-0000-0000-000000000013','30000000-0000-0000-0000-000000000006',1,'Do you hold an active Series 7 license (or are willing to obtain one)?','BOOLEAN',null,'EQ','false',true),
 ('40000000-0000-0000-0000-000000000014','30000000-0000-0000-0000-000000000006',2,'Are you at least 18 years old?','BOOLEAN',null,'EQ','false',true),
 ('40000000-0000-0000-0000-000000000015','30000000-0000-0000-0000-000000000008',1,'Will you graduate with a bachelor''s degree by Summer 2026?','BOOLEAN',null,'EQ','false',true),
 ('40000000-0000-0000-0000-000000000016','30000000-0000-0000-0000-000000000008',2,'Which best describes your strongest programming language?','CHOICE','Java,Python,JavaScript,C++,None','NOT_IN','Java,Python,JavaScript,C++',true);

-- ---------------------------------------------------------------------
-- candidate  (18)
-- sources: CAREER_SITE/REFERRAL/SOURCED/EVENT/CAMPUS/DATABASE/AGENCY
-- lifecycle mostly ACTIVE; several PASSIVE/DORMANT
-- preferred_language mostly en; some es/fr/zh
-- ---------------------------------------------------------------------
insert into candidate (id, name, email, phone, location, headline, years_experience, source, preferred_language, lifecycle, last_active_at) values
 ('50000000-0000-0000-0000-000000000001','Maria Gonzalez','maria.gonzalez@example.com','+1-704-555-0101','Charlotte, NC','Retail banking professional with 5 years client experience',5.0,'CAREER_SITE','es','ACTIVE','2026-06-25 14:20:00+00'),
 ('50000000-0000-0000-0000-000000000002','Tyrone Jackson','tyrone.jackson@example.com','+1-813-555-0102','Tampa, FL','Customer service specialist',2.5,'REFERRAL','en','ACTIVE','2026-06-26 09:10:00+00'),
 ('50000000-0000-0000-0000-000000000003','Aisha Khan','aisha.khan@example.com','+1-602-555-0103','Phoenix, AZ','Branch manager, 8 years leading high-volume centers',8.0,'SOURCED','en','PASSIVE','2026-06-20 17:45:00+00'),
 ('50000000-0000-0000-0000-000000000004','Liam O''Brien','liam.obrien@example.com','+1-212-555-0104','New York, NY','Backend engineer — Java, Spring, distributed systems',7.0,'CAREER_SITE','en','ACTIVE','2026-06-26 11:30:00+00'),
 ('50000000-0000-0000-0000-000000000005','Wei Zhang','wei.zhang@example.com','+1-704-555-0105','Charlotte, NC','ML engineer focused on fraud and risk models',6.0,'SOURCED','zh','ACTIVE','2026-06-25 08:00:00+00'),
 ('50000000-0000-0000-0000-000000000006','Sophie Martin','sophie.martin@example.com','+1-617-555-0106','Boston, MA','Wealth advisor, Series 7/66, growing book',4.0,'AGENCY','fr','ACTIVE','2026-06-24 13:15:00+00'),
 ('50000000-0000-0000-0000-000000000007','Carlos Reyes','carlos.reyes@example.com','+1-704-555-0107','Charlotte, NC','AML/KYC compliance analyst',3.5,'DATABASE','es','DORMANT','2026-03-12 10:00:00+00'),
 ('50000000-0000-0000-0000-000000000008','Emily Carter','emily.carter@example.com','+1-980-555-0108','Charlotte, NC','New grad — CS, internships in fintech',0.5,'CAMPUS','en','ACTIVE','2026-06-26 16:40:00+00'),
 ('50000000-0000-0000-0000-000000000009','Raj Patel','raj.patel@example.com','+1-212-555-0109','New York, NY','Finance undergrad, summer analyst candidate',0.0,'CAMPUS','en','ACTIVE','2026-06-25 19:05:00+00'),
 ('50000000-0000-0000-0000-000000000010','Nina Petrova','nina.petrova@example.com','+1-813-555-0110','Tampa, FL','Fraud investigations, 4 years',4.0,'EVENT','en','ACTIVE','2026-06-23 12:00:00+00'),
 ('50000000-0000-0000-0000-000000000011','Jordan Lee','jordan.lee@example.com','+1-704-555-0111','Charlotte, NC','Teller and cash operations',1.5,'CAREER_SITE','en','ACTIVE','2026-06-26 10:25:00+00'),
 ('50000000-0000-0000-0000-000000000012','Fatima Al-Sayed','fatima.alsayed@example.com','+1-602-555-0112','Phoenix, AZ','Operations lead, process improvement',6.5,'SOURCED','en','PASSIVE','2026-06-18 15:30:00+00'),
 ('50000000-0000-0000-0000-000000000013','Daniel Kim','daniel.kim@example.com','+1-415-555-0113','San Francisco, CA','Full-stack engineer, React + Java',5.5,'REFERRAL','en','ACTIVE','2026-06-26 08:45:00+00'),
 ('50000000-0000-0000-0000-000000000014','Grace Mwangi','grace.mwangi@example.com','+1-617-555-0114','Boston, MA','Relationship banker transitioning to advisory',3.0,'CAREER_SITE','en','ACTIVE','2026-06-24 09:50:00+00'),
 ('50000000-0000-0000-0000-000000000015','Hiroshi Tanaka','hiroshi.tanaka@example.com','+1-212-555-0115','New York, NY','Data scientist, credit risk modeling',9.0,'AGENCY','en','ACTIVE','2026-06-22 14:10:00+00'),
 ('50000000-0000-0000-0000-000000000016','Olivia Brown','olivia.brown@example.com','+1-704-555-0116','Charlotte, NC','Recent bootcamp grad, Python focus',0.5,'CAMPUS','en','ACTIVE','2026-06-26 13:00:00+00'),
 ('50000000-0000-0000-0000-000000000017','Diego Fernandez','diego.fernandez@example.com','+1-813-555-0117','Tampa, FL','Customer service and sales, bilingual',2.0,'REFERRAL','es','ACTIVE','2026-06-25 11:55:00+00'),
 ('50000000-0000-0000-0000-000000000018','Hannah Schmidt','hannah.schmidt@example.com','+1-602-555-0118','Phoenix, AZ','Risk and controls, 5 years',5.0,'DATABASE','en','DORMANT','2026-02-28 16:20:00+00');

-- ---------------------------------------------------------------------
-- candidate_skill  (3-5 per candidate; some inferred=true)
-- ---------------------------------------------------------------------
insert into candidate_skill (candidate_id, skill_id, proficiency, years, inferred) values
 -- 1 Maria — Personal Banker profile
 ('50000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000021',5,5.0,false),
 ('50000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000022',4,4.0,false),
 ('50000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000009',4,5.0,false),
 ('50000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000023',3,3.0,true),
 -- 2 Tyrone
 ('50000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000021',4,2.5,false),
 ('50000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000009',3,2.0,false),
 ('50000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000010',2,1.0,true),
 -- 3 Aisha — branch manager
 ('50000000-0000-0000-0000-000000000003','20000000-0000-0000-0000-000000000010',5,8.0,false),
 ('50000000-0000-0000-0000-000000000003','20000000-0000-0000-0000-000000000013',5,6.0,false),
 ('50000000-0000-0000-0000-000000000003','20000000-0000-0000-0000-000000000014',4,5.0,false),
 ('50000000-0000-0000-0000-000000000003','20000000-0000-0000-0000-000000000020',3,4.0,true),
 ('50000000-0000-0000-0000-000000000003','20000000-0000-0000-0000-000000000012',4,5.0,false),
 -- 4 Liam — backend engineer
 ('50000000-0000-0000-0000-000000000004','20000000-0000-0000-0000-000000000001',5,7.0,false),
 ('50000000-0000-0000-0000-000000000004','20000000-0000-0000-0000-000000000004',5,6.0,false),
 ('50000000-0000-0000-0000-000000000004','20000000-0000-0000-0000-000000000005',4,5.0,false),
 ('50000000-0000-0000-0000-000000000004','20000000-0000-0000-0000-000000000003',3,2.0,true),
 -- 5 Wei — ML
 ('50000000-0000-0000-0000-000000000005','20000000-0000-0000-0000-000000000002',5,6.0,false),
 ('50000000-0000-0000-0000-000000000005','20000000-0000-0000-0000-000000000007',5,5.0,false),
 ('50000000-0000-0000-0000-000000000005','20000000-0000-0000-0000-000000000005',4,6.0,false),
 ('50000000-0000-0000-0000-000000000005','20000000-0000-0000-0000-000000000006',4,4.0,true),
 -- 6 Sophie — advisor
 ('50000000-0000-0000-0000-000000000006','20000000-0000-0000-0000-000000000024',4,4.0,false),
 ('50000000-0000-0000-0000-000000000006','20000000-0000-0000-0000-000000000022',4,4.0,false),
 ('50000000-0000-0000-0000-000000000006','20000000-0000-0000-0000-000000000023',3,3.0,false),
 -- 7 Carlos — compliance
 ('50000000-0000-0000-0000-000000000007','20000000-0000-0000-0000-000000000017',4,3.5,false),
 ('50000000-0000-0000-0000-000000000007','20000000-0000-0000-0000-000000000018',4,3.0,false),
 ('50000000-0000-0000-0000-000000000007','20000000-0000-0000-0000-000000000019',3,2.0,false),
 ('50000000-0000-0000-0000-000000000007','20000000-0000-0000-0000-000000000020',3,2.0,true),
 -- 8 Emily — new grad CS
 ('50000000-0000-0000-0000-000000000008','20000000-0000-0000-0000-000000000002',3,1.0,false),
 ('50000000-0000-0000-0000-000000000008','20000000-0000-0000-0000-000000000001',2,0.5,false),
 ('50000000-0000-0000-0000-000000000008','20000000-0000-0000-0000-000000000003',3,1.0,false),
 -- 9 Raj — finance undergrad
 ('50000000-0000-0000-0000-000000000009','20000000-0000-0000-0000-000000000005',2,0.5,false),
 ('50000000-0000-0000-0000-000000000009','20000000-0000-0000-0000-000000000008',2,0.5,false),
 ('50000000-0000-0000-0000-000000000009','20000000-0000-0000-0000-000000000015',2,0.0,true),
 -- 10 Nina — fraud
 ('50000000-0000-0000-0000-000000000010','20000000-0000-0000-0000-000000000017',4,4.0,false),
 ('50000000-0000-0000-0000-000000000010','20000000-0000-0000-0000-000000000020',4,4.0,false),
 ('50000000-0000-0000-0000-000000000010','20000000-0000-0000-0000-000000000021',3,3.0,false),
 -- 11 Jordan — teller
 ('50000000-0000-0000-0000-000000000011','20000000-0000-0000-0000-000000000009',4,1.5,false),
 ('50000000-0000-0000-0000-000000000011','20000000-0000-0000-0000-000000000021',3,1.5,false),
 ('50000000-0000-0000-0000-000000000011','20000000-0000-0000-0000-000000000010',2,1.0,true),
 -- 12 Fatima — operations
 ('50000000-0000-0000-0000-000000000012','20000000-0000-0000-0000-000000000010',4,6.0,false),
 ('50000000-0000-0000-0000-000000000012','20000000-0000-0000-0000-000000000012',5,6.5,false),
 ('50000000-0000-0000-0000-000000000012','20000000-0000-0000-0000-000000000013',4,5.0,false),
 ('50000000-0000-0000-0000-000000000012','20000000-0000-0000-0000-000000000015',3,4.0,true),
 -- 13 Daniel — full stack
 ('50000000-0000-0000-0000-000000000013','20000000-0000-0000-0000-000000000003',5,5.0,false),
 ('50000000-0000-0000-0000-000000000013','20000000-0000-0000-0000-000000000001',4,4.5,false),
 ('50000000-0000-0000-0000-000000000013','20000000-0000-0000-0000-000000000004',3,3.0,false),
 ('50000000-0000-0000-0000-000000000013','20000000-0000-0000-0000-000000000005',3,3.0,true),
 -- 14 Grace — relationship banker
 ('50000000-0000-0000-0000-000000000014','20000000-0000-0000-0000-000000000022',4,3.0,false),
 ('50000000-0000-0000-0000-000000000014','20000000-0000-0000-0000-000000000021',4,3.0,false),
 ('50000000-0000-0000-0000-000000000014','20000000-0000-0000-0000-000000000024',2,1.0,true),
 -- 15 Hiroshi — data scientist
 ('50000000-0000-0000-0000-000000000015','20000000-0000-0000-0000-000000000002',5,9.0,false),
 ('50000000-0000-0000-0000-000000000015','20000000-0000-0000-0000-000000000007',5,8.0,false),
 ('50000000-0000-0000-0000-000000000015','20000000-0000-0000-0000-000000000005',5,9.0,false),
 ('50000000-0000-0000-0000-000000000015','20000000-0000-0000-0000-000000000006',4,7.0,false),
 ('50000000-0000-0000-0000-000000000015','20000000-0000-0000-0000-000000000008',3,5.0,true),
 -- 16 Olivia — bootcamp
 ('50000000-0000-0000-0000-000000000016','20000000-0000-0000-0000-000000000002',3,0.5,false),
 ('50000000-0000-0000-0000-000000000016','20000000-0000-0000-0000-000000000003',2,0.5,false),
 ('50000000-0000-0000-0000-000000000016','20000000-0000-0000-0000-000000000005',2,0.5,true),
 -- 17 Diego — service/sales bilingual
 ('50000000-0000-0000-0000-000000000017','20000000-0000-0000-0000-000000000021',4,2.0,false),
 ('50000000-0000-0000-0000-000000000017','20000000-0000-0000-0000-000000000023',3,2.0,false),
 ('50000000-0000-0000-0000-000000000017','20000000-0000-0000-0000-000000000009',3,1.5,false),
 -- 18 Hannah — risk/controls
 ('50000000-0000-0000-0000-000000000018','20000000-0000-0000-0000-000000000020',4,5.0,false),
 ('50000000-0000-0000-0000-000000000018','20000000-0000-0000-0000-000000000017',3,3.0,false),
 ('50000000-0000-0000-0000-000000000018','20000000-0000-0000-0000-000000000019',3,3.0,false),
 ('50000000-0000-0000-0000-000000000018','20000000-0000-0000-0000-000000000012',3,2.0,true);

-- ---------------------------------------------------------------------
-- application  (24)  UNIQUE(candidate_id, job_id) respected
-- stages spread: APPLIED/SCREENED/MATCHED/INTERVIEW/ASSESSMENT/OFFER/HIRED/REJECTED/WITHDRAWN
-- ---------------------------------------------------------------------
insert into application (id, candidate_id, job_id, stage, source, fit_score, fit_explanation, knockout_passed, rejection_reason, applied_at, updated_at) values
 -- Personal Banker (job 1)
 ('60000000-0000-0000-0000-000000000001','50000000-0000-0000-0000-000000000001','30000000-0000-0000-0000-000000000001','HIRED','CAREER_SITE',92,'Strong client relationship background and cash handling; bilingual fit for market.',true,null,'2026-06-11 09:00:00+00','2026-06-24 17:00:00+00'),
 ('60000000-0000-0000-0000-000000000002','50000000-0000-0000-0000-000000000002','30000000-0000-0000-0000-000000000001','INTERVIEW','REFERRAL',81,'Solid service skills; building banking-specific experience.',true,null,'2026-06-12 10:30:00+00','2026-06-22 14:00:00+00'),
 ('60000000-0000-0000-0000-000000000003','50000000-0000-0000-0000-000000000017','30000000-0000-0000-0000-000000000001','SCREENED','REFERRAL',74,'Bilingual sales and service; needs more banking exposure.',true,null,'2026-06-13 11:00:00+00','2026-06-19 12:00:00+00'),
 -- Teller (job 2)
 ('60000000-0000-0000-0000-000000000004','50000000-0000-0000-0000-000000000011','30000000-0000-0000-0000-000000000002','OFFER','CAREER_SITE',88,'Direct teller and cash operations experience; strong drawer accuracy.',true,null,'2026-06-16 09:15:00+00','2026-06-25 10:00:00+00'),
 ('60000000-0000-0000-0000-000000000005','50000000-0000-0000-0000-000000000002','30000000-0000-0000-0000-000000000002','MATCHED','SOURCED',79,'Service background transfers well to the teller line.',true,null,'2026-06-17 13:00:00+00','2026-06-21 09:30:00+00'),
 -- Branch Operations Manager (job 3)
 ('60000000-0000-0000-0000-000000000006','50000000-0000-0000-0000-000000000003','30000000-0000-0000-0000-000000000003','OFFER','SOURCED',90,'8 years leading high-volume branches; strong leadership and controls.',true,null,'2026-06-06 08:00:00+00','2026-06-23 16:00:00+00'),
 ('60000000-0000-0000-0000-000000000007','50000000-0000-0000-0000-000000000012','30000000-0000-0000-0000-000000000003','INTERVIEW','SOURCED',83,'Deep operations and process improvement; growing people-leadership scope.',true,null,'2026-06-07 09:45:00+00','2026-06-20 11:00:00+00'),
 -- Senior Software Engineer (job 4)
 ('60000000-0000-0000-0000-000000000008','50000000-0000-0000-0000-000000000004','30000000-0000-0000-0000-000000000004','HIRED','CAREER_SITE',94,'Excellent Java/Spring depth and distributed-systems experience.',true,null,'2026-06-09 10:00:00+00','2026-06-26 09:00:00+00'),
 ('60000000-0000-0000-0000-000000000009','50000000-0000-0000-0000-000000000013','30000000-0000-0000-0000-000000000004','INTERVIEW','REFERRAL',85,'Strong full-stack with React and Java; some backend ramp needed.',true,null,'2026-06-10 11:30:00+00','2026-06-24 13:00:00+00'),
 ('60000000-0000-0000-0000-000000000010','50000000-0000-0000-0000-000000000008','30000000-0000-0000-0000-000000000004','REJECTED','CAREER_SITE',48,'Below required years of professional engineering experience for senior role.',false,'Did not meet 5-year experience knockout for senior level.','2026-06-11 14:00:00+00','2026-06-15 10:00:00+00'),
 -- Data Scientist (job 5)
 ('60000000-0000-0000-0000-000000000011','50000000-0000-0000-0000-000000000005','30000000-0000-0000-0000-000000000005','ASSESSMENT','SOURCED',91,'Strong ML and fraud-modeling background; great fit for risk analytics.',true,null,'2026-06-13 09:00:00+00','2026-06-25 15:00:00+00'),
 ('60000000-0000-0000-0000-000000000012','50000000-0000-0000-0000-000000000015','30000000-0000-0000-0000-000000000005','HIRED','AGENCY',93,'9 years credit-risk modeling; senior-level depth in ML and SQL.',true,null,'2026-06-12 16:00:00+00','2026-06-26 12:00:00+00'),
 ('60000000-0000-0000-0000-000000000013','50000000-0000-0000-0000-000000000016','30000000-0000-0000-0000-000000000005','REJECTED','CAMPUS',40,'Bootcamp grad below 2-year applied ML knockout for this role.',false,'Did not meet applied ML experience knockout.','2026-06-14 10:00:00+00','2026-06-18 09:00:00+00'),
 -- Financial Solutions Advisor (job 6)
 ('60000000-0000-0000-0000-000000000014','50000000-0000-0000-0000-000000000006','30000000-0000-0000-0000-000000000006','OFFER','AGENCY',87,'Licensed advisor with growing book; strong relationship skills.',true,null,'2026-06-19 09:00:00+00','2026-06-25 11:00:00+00'),
 ('60000000-0000-0000-0000-000000000015','50000000-0000-0000-0000-000000000014','30000000-0000-0000-0000-000000000006','SCREENED','CAREER_SITE',72,'Relationship banker moving into advisory; needs licensing.',true,null,'2026-06-20 10:15:00+00','2026-06-23 14:00:00+00'),
 -- Compliance Analyst (job 7, FILLED)
 ('60000000-0000-0000-0000-000000000016','50000000-0000-0000-0000-000000000007','30000000-0000-0000-0000-000000000007','HIRED','DATABASE',86,'Solid AML/KYC and regulatory reporting; ready for the role.',true,null,'2026-06-10 09:00:00+00','2026-06-20 16:00:00+00'),
 ('60000000-0000-0000-0000-000000000017','50000000-0000-0000-0000-000000000018','30000000-0000-0000-0000-000000000007','REJECTED','DATABASE',58,'Risk-controls background but lighter direct AML alert experience.',true,'Position filled by stronger AML-specific candidate.','2026-06-11 13:00:00+00','2026-06-21 10:00:00+00'),
 -- Software Engineer New Grad (job 8)
 ('60000000-0000-0000-0000-000000000018','50000000-0000-0000-0000-000000000008','30000000-0000-0000-0000-000000000008','INTERVIEW','CAMPUS',82,'Strong CS fundamentals and fintech internships; good TDP fit.',true,null,'2026-06-21 09:00:00+00','2026-06-25 12:00:00+00'),
 ('60000000-0000-0000-0000-000000000019','50000000-0000-0000-0000-000000000016','30000000-0000-0000-0000-000000000008','ASSESSMENT','CAMPUS',76,'Bootcamp grad with Python focus; eager and coachable.',true,null,'2026-06-22 10:00:00+00','2026-06-26 09:30:00+00'),
 ('60000000-0000-0000-0000-000000000020','50000000-0000-0000-0000-000000000013','30000000-0000-0000-0000-000000000008','WITHDRAWN','REFERRAL',70,'Strong candidate but pursuing the senior role instead.',true,null,'2026-06-21 15:00:00+00','2026-06-23 09:00:00+00'),
 -- Summer Analyst Intern (job 9, PAUSED)
 ('60000000-0000-0000-0000-000000000021','50000000-0000-0000-0000-000000000009','30000000-0000-0000-0000-000000000009','APPLIED','CAMPUS',68,'Finance undergrad; strong academics, early in process.',null,null,'2026-06-23 09:00:00+00','2026-06-23 09:00:00+00'),
 -- Cross-applications to broaden coverage
 ('60000000-0000-0000-0000-000000000022','50000000-0000-0000-0000-000000000010','30000000-0000-0000-0000-000000000005','APPLIED','EVENT',64,'Fraud investigations background; exploring analytics path.',null,null,'2026-06-24 11:00:00+00','2026-06-24 11:00:00+00'),
 ('60000000-0000-0000-0000-000000000023','50000000-0000-0000-0000-000000000014','30000000-0000-0000-0000-000000000001','MATCHED','CAREER_SITE',77,'Relationship banking skills map well to Personal Banker.',true,null,'2026-06-22 09:00:00+00','2026-06-25 09:00:00+00'),
 ('60000000-0000-0000-0000-000000000024','50000000-0000-0000-0000-000000000012','30000000-0000-0000-0000-000000000006','APPLIED','SOURCED',61,'Operations leader curious about advisory; early stage.',null,null,'2026-06-24 14:00:00+00','2026-06-24 14:00:00+00');

-- ---------------------------------------------------------------------
-- screening_answer  (10)  tied to applications + their job's knockout questions
-- ---------------------------------------------------------------------
insert into screening_answer (id, application_id, question_id, answer, passed) values
 -- app 1 (Maria, Personal Banker) — passes all
 ('61000000-0000-0000-0000-000000000001','60000000-0000-0000-0000-000000000001','40000000-0000-0000-0000-000000000001','true',true),
 ('61000000-0000-0000-0000-000000000002','60000000-0000-0000-0000-000000000001','40000000-0000-0000-0000-000000000002','true',true),
 ('61000000-0000-0000-0000-000000000003','60000000-0000-0000-0000-000000000001','40000000-0000-0000-0000-000000000003','60',true),
 -- app 4 (Jordan, Teller)
 ('61000000-0000-0000-0000-000000000004','60000000-0000-0000-0000-000000000004','40000000-0000-0000-0000-000000000004','true',true),
 ('61000000-0000-0000-0000-000000000005','60000000-0000-0000-0000-000000000004','40000000-0000-0000-0000-000000000005','true',true),
 -- app 8 (Liam, Senior SWE)
 ('61000000-0000-0000-0000-000000000006','60000000-0000-0000-0000-000000000008','40000000-0000-0000-0000-000000000008','7',true),
 ('61000000-0000-0000-0000-000000000007','60000000-0000-0000-0000-000000000008','40000000-0000-0000-0000-000000000009','Java',true),
 -- app 10 (Emily, Senior SWE) — fails experience knockout
 ('61000000-0000-0000-0000-000000000008','60000000-0000-0000-0000-000000000010','40000000-0000-0000-0000-000000000008','1',false),
 -- app 11 (Wei, Data Scientist)
 ('61000000-0000-0000-0000-000000000009','60000000-0000-0000-0000-000000000011','40000000-0000-0000-0000-000000000012','5',true),
 -- app 13 (Olivia, Data Scientist) — fails ML experience knockout
 ('61000000-0000-0000-0000-000000000010','60000000-0000-0000-0000-000000000013','40000000-0000-0000-0000-000000000012','0',false);

-- ---------------------------------------------------------------------
-- conversation  (6)  channels WEB_CHAT/SMS/WHATSAPP/EMAIL/VOICE ; context left null (column not present)
-- ---------------------------------------------------------------------
insert into conversation (id, candidate_id, application_id, job_id, channel, language, kind, status, created_at, updated_at) values
 ('70000000-0000-0000-0000-000000000001','50000000-0000-0000-0000-000000000001','60000000-0000-0000-0000-000000000001','30000000-0000-0000-0000-000000000001','WEB_CHAT','es','APPLY','COMPLETED','2026-06-11 09:00:00+00','2026-06-11 09:20:00+00'),
 ('70000000-0000-0000-0000-000000000002','50000000-0000-0000-0000-000000000011','60000000-0000-0000-0000-000000000004','30000000-0000-0000-0000-000000000002','SMS','en','SCHEDULE','COMPLETED','2026-06-18 10:00:00+00','2026-06-18 10:12:00+00'),
 ('70000000-0000-0000-0000-000000000003','50000000-0000-0000-0000-000000000004','60000000-0000-0000-0000-000000000008','30000000-0000-0000-0000-000000000004','EMAIL','en','SCREEN','COMPLETED','2026-06-13 14:00:00+00','2026-06-13 14:40:00+00'),
 ('70000000-0000-0000-0000-000000000004','50000000-0000-0000-0000-000000000007','60000000-0000-0000-0000-000000000016','30000000-0000-0000-0000-000000000007','VOICE','en','SCREEN','COMPLETED','2026-06-12 11:00:00+00','2026-06-12 11:18:00+00'),
 ('70000000-0000-0000-0000-000000000005','50000000-0000-0000-0000-000000000009','60000000-0000-0000-0000-000000000021','30000000-0000-0000-0000-000000000009','WHATSAPP','en','APPLY','OPEN','2026-06-23 09:00:00+00','2026-06-23 09:08:00+00'),
 ('70000000-0000-0000-0000-000000000006','50000000-0000-0000-0000-000000000012',null,null,'WEB_CHAT','en','NURTURE','ABANDONED','2026-06-19 16:00:00+00','2026-06-19 16:05:00+00');

-- ---------------------------------------------------------------------
-- message  (4-6 per conversation)  senders OLIVIA/CANDIDATE alternating
-- ---------------------------------------------------------------------
insert into message (id, conversation_id, sender, body, intent, created_at) values
 -- conv 1 (apply)
 ('71000000-0000-0000-0000-000000000001','70000000-0000-0000-0000-000000000001','OLIVIA','Hola Maria, soy Olivia. Veo que te interesa el puesto de Personal Banker en Charlotte. ¿Quieres aplicar ahora?','apply.start','2026-06-11 09:00:00+00'),
 ('71000000-0000-0000-0000-000000000002','70000000-0000-0000-0000-000000000001','CANDIDATE','Sí, me encantaría aplicar.','apply.confirm','2026-06-11 09:02:00+00'),
 ('71000000-0000-0000-0000-000000000003','70000000-0000-0000-0000-000000000001','OLIVIA','Perfecto. ¿Tienes al menos 18 años y autorización para trabajar en EE. UU.?','screen.knockout','2026-06-11 09:03:00+00'),
 ('71000000-0000-0000-0000-000000000004','70000000-0000-0000-0000-000000000001','CANDIDATE','Sí a ambas.','screen.answer','2026-06-11 09:05:00+00'),
 ('71000000-0000-0000-0000-000000000005','70000000-0000-0000-0000-000000000001','OLIVIA','¡Genial! Tu solicitud está completa. Un reclutador te contactará pronto.','apply.complete','2026-06-11 09:08:00+00'),
 -- conv 2 (schedule)
 ('71000000-0000-0000-0000-000000000006','70000000-0000-0000-0000-000000000002','OLIVIA','Hi Jordan! Great news — the team wants to meet you for the Teller role. Pick a time that works?','schedule.invite','2026-06-18 10:00:00+00'),
 ('71000000-0000-0000-0000-000000000007','70000000-0000-0000-0000-000000000002','CANDIDATE','Sure! Is Thursday morning open?','schedule.request','2026-06-18 10:03:00+00'),
 ('71000000-0000-0000-0000-000000000008','70000000-0000-0000-0000-000000000002','OLIVIA','Thursday 9:30 AM is available. Booking it now — you''ll get a calendar invite.','schedule.confirm','2026-06-18 10:06:00+00'),
 ('71000000-0000-0000-0000-000000000009','70000000-0000-0000-0000-000000000002','CANDIDATE','Perfect, thank you!','schedule.thanks','2026-06-18 10:08:00+00'),
 -- conv 3 (screen, email)
 ('71000000-0000-0000-0000-000000000010','70000000-0000-0000-0000-000000000003','OLIVIA','Hi Liam, thanks for applying to the Senior Software Engineer role. A few quick questions to get started.','screen.start','2026-06-13 14:00:00+00'),
 ('71000000-0000-0000-0000-000000000011','70000000-0000-0000-0000-000000000003','CANDIDATE','Happy to — go ahead.','screen.ack','2026-06-13 14:05:00+00'),
 ('71000000-0000-0000-0000-000000000012','70000000-0000-0000-0000-000000000003','OLIVIA','How many years of professional software engineering experience do you have, and what is your primary backend language?','screen.knockout','2026-06-13 14:08:00+00'),
 ('71000000-0000-0000-0000-000000000013','70000000-0000-0000-0000-000000000003','CANDIDATE','7 years, primarily Java with Spring Boot.','screen.answer','2026-06-13 14:20:00+00'),
 ('71000000-0000-0000-0000-000000000014','70000000-0000-0000-0000-000000000003','OLIVIA','Great fit — I''ll move you forward to an interview.','screen.advance','2026-06-13 14:30:00+00'),
 -- conv 4 (voice screen)
 ('71000000-0000-0000-0000-000000000015','70000000-0000-0000-0000-000000000004','OLIVIA','Hi Carlos, this is Olivia calling about the Compliance Analyst role. Is now a good time?','voice.intro','2026-06-12 11:00:00+00'),
 ('71000000-0000-0000-0000-000000000016','70000000-0000-0000-0000-000000000004','CANDIDATE','Yes, this works.','voice.ack','2026-06-12 11:01:00+00'),
 ('71000000-0000-0000-0000-000000000017','70000000-0000-0000-0000-000000000004','OLIVIA','Do you have prior AML or KYC experience, and how many years of compliance experience overall?','screen.knockout','2026-06-12 11:03:00+00'),
 ('71000000-0000-0000-0000-000000000018','70000000-0000-0000-0000-000000000004','CANDIDATE','Yes, about 3.5 years in AML/KYC and regulatory reporting.','screen.answer','2026-06-12 11:08:00+00'),
 ('71000000-0000-0000-0000-000000000019','70000000-0000-0000-0000-000000000004','OLIVIA','Excellent — you''re a strong match. I''ll connect you with the hiring manager.','screen.advance','2026-06-12 11:15:00+00'),
 -- conv 5 (apply, whatsapp, open)
 ('71000000-0000-0000-0000-000000000020','70000000-0000-0000-0000-000000000005','OLIVIA','Hi Raj! Interested in the Summer Analyst Internship in NYC? I can help you apply right here.','apply.start','2026-06-23 09:00:00+00'),
 ('71000000-0000-0000-0000-000000000021','70000000-0000-0000-0000-000000000005','CANDIDATE','Yes please!','apply.confirm','2026-06-23 09:02:00+00'),
 ('71000000-0000-0000-0000-000000000022','70000000-0000-0000-0000-000000000005','OLIVIA','Are you currently enrolled in an undergraduate program and available for the full 10 weeks?','screen.knockout','2026-06-23 09:04:00+00'),
 ('71000000-0000-0000-0000-000000000023','70000000-0000-0000-0000-000000000005','CANDIDATE','Yes to both.','screen.answer','2026-06-23 09:06:00+00'),
 -- conv 6 (nurture, abandoned)
 ('71000000-0000-0000-0000-000000000024','70000000-0000-0000-0000-000000000006','OLIVIA','Hi Fatima, new Branch Operations Manager roles just opened that match your background. Want details?','nurture.outreach','2026-06-19 16:00:00+00'),
 ('71000000-0000-0000-0000-000000000025','70000000-0000-0000-0000-000000000006','CANDIDATE','Maybe later, a bit busy right now.','nurture.defer','2026-06-19 16:04:00+00');

-- ---------------------------------------------------------------------
-- interview_slot  (16)  mix booked/open across jobs; future times
-- ---------------------------------------------------------------------
insert into interview_slot (id, job_id, interviewer_id, starts_at, ends_at, booked) values
 ('80000000-0000-0000-0000-000000000001','30000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000002','2026-07-01 14:00:00+00','2026-07-01 14:45:00+00',true),
 ('80000000-0000-0000-0000-000000000002','30000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000002','2026-07-01 15:00:00+00','2026-07-01 15:45:00+00',false),
 ('80000000-0000-0000-0000-000000000003','30000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000005','2026-07-02 13:30:00+00','2026-07-02 14:00:00+00',true),
 ('80000000-0000-0000-0000-000000000004','30000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000005','2026-07-02 14:30:00+00','2026-07-02 15:00:00+00',false),
 ('80000000-0000-0000-0000-000000000005','30000000-0000-0000-0000-000000000003','10000000-0000-0000-0000-000000000004','2026-07-03 16:00:00+00','2026-07-03 17:00:00+00',true),
 ('80000000-0000-0000-0000-000000000006','30000000-0000-0000-0000-000000000003','10000000-0000-0000-0000-000000000004','2026-07-03 17:30:00+00','2026-07-03 18:30:00+00',false),
 ('80000000-0000-0000-0000-000000000007','30000000-0000-0000-0000-000000000004','10000000-0000-0000-0000-000000000003','2026-07-06 15:00:00+00','2026-07-06 16:00:00+00',true),
 ('80000000-0000-0000-0000-000000000008','30000000-0000-0000-0000-000000000004','10000000-0000-0000-0000-000000000003','2026-07-06 16:30:00+00','2026-07-06 17:30:00+00',true),
 ('80000000-0000-0000-0000-000000000009','30000000-0000-0000-0000-000000000004','10000000-0000-0000-0000-000000000003','2026-07-07 15:00:00+00','2026-07-07 16:00:00+00',false),
 ('80000000-0000-0000-0000-000000000010','30000000-0000-0000-0000-000000000005','10000000-0000-0000-0000-000000000003','2026-07-08 14:00:00+00','2026-07-08 15:00:00+00',true),
 ('80000000-0000-0000-0000-000000000011','30000000-0000-0000-0000-000000000005','10000000-0000-0000-0000-000000000003','2026-07-08 15:30:00+00','2026-07-08 16:30:00+00',false),
 ('80000000-0000-0000-0000-000000000012','30000000-0000-0000-0000-000000000006','10000000-0000-0000-0000-000000000002','2026-07-09 13:00:00+00','2026-07-09 14:00:00+00',true),
 ('80000000-0000-0000-0000-000000000013','30000000-0000-0000-0000-000000000006','10000000-0000-0000-0000-000000000002','2026-07-09 14:30:00+00','2026-07-09 15:30:00+00',false),
 ('80000000-0000-0000-0000-000000000014','30000000-0000-0000-0000-000000000008','10000000-0000-0000-0000-000000000003','2026-07-10 16:00:00+00','2026-07-10 16:45:00+00',true),
 ('80000000-0000-0000-0000-000000000015','30000000-0000-0000-0000-000000000008','10000000-0000-0000-0000-000000000003','2026-07-10 17:00:00+00','2026-07-10 17:45:00+00',false),
 ('80000000-0000-0000-0000-000000000016','30000000-0000-0000-0000-000000000007','10000000-0000-0000-0000-000000000003','2026-07-11 14:00:00+00','2026-07-11 15:00:00+00',false);

-- ---------------------------------------------------------------------
-- interview  (10)  tied to INTERVIEW/ASSESSMENT/OFFER/HIRED applications
-- types AI_PHONE_SCREEN/AI_INTERVIEW/ONE_WAY_VIDEO/LIVE_VIDEO/PANEL/ONSITE
-- ---------------------------------------------------------------------
insert into interview (id, application_id, slot_id, type, scheduled_at, duration_min, status, interviewers, score, recommendation, summary) values
 ('81000000-0000-0000-0000-000000000001','60000000-0000-0000-0000-000000000001','80000000-0000-0000-0000-000000000001','ONSITE','2026-07-01 14:00:00+00',45,'COMPLETED','David Okafor, Marcus Bell',91,'ADVANCE','Strong client-first mindset; clear examples of cross-sell and conflict resolution. Recommend hire.'),
 ('81000000-0000-0000-0000-000000000002','60000000-0000-0000-0000-000000000002',null,'AI_PHONE_SCREEN','2026-06-22 13:00:00+00',20,'COMPLETED','Olivia (AI)',78,'ADVANCE','Friendly and articulate; service-oriented. Some banking ramp needed but coachable.'),
 ('81000000-0000-0000-0000-000000000003','60000000-0000-0000-0000-000000000004','80000000-0000-0000-0000-000000000003','LIVE_VIDEO','2026-07-02 13:30:00+00',30,'SCHEDULED','Sarah Chen',null,null,null),
 ('81000000-0000-0000-0000-000000000004','60000000-0000-0000-0000-000000000006','80000000-0000-0000-0000-000000000005','PANEL','2026-07-03 16:00:00+00',60,'COMPLETED','David Okafor, Priya Raman',89,'ADVANCE','Excellent operational command and leadership presence; ready to lead a flagship center.'),
 ('81000000-0000-0000-0000-000000000005','60000000-0000-0000-0000-000000000007','80000000-0000-0000-0000-000000000006','LIVE_VIDEO','2026-07-03 17:30:00+00',45,'SCHEDULED','David Okafor',null,null,null),
 ('81000000-0000-0000-0000-000000000006','60000000-0000-0000-0000-000000000008','80000000-0000-0000-0000-000000000007','PANEL','2026-07-06 15:00:00+00',60,'COMPLETED','Elena Vasquez, David Okafor',93,'ADVANCE','Deep distributed-systems knowledge; strong system-design round. Clear hire.'),
 ('81000000-0000-0000-0000-000000000007','60000000-0000-0000-0000-000000000009','80000000-0000-0000-0000-000000000008','LIVE_VIDEO','2026-07-06 16:30:00+00',45,'SCHEDULED','Elena Vasquez',null,null,null),
 ('81000000-0000-0000-0000-000000000008','60000000-0000-0000-0000-000000000011','80000000-0000-0000-0000-000000000010','AI_INTERVIEW','2026-07-08 14:00:00+00',40,'COMPLETED','Olivia (AI)',88,'ADVANCE','Strong modeling fundamentals and clear communication of fraud-detection approach.'),
 ('81000000-0000-0000-0000-000000000009','60000000-0000-0000-0000-000000000012',null,'ONSITE','2026-06-24 15:00:00+00',90,'COMPLETED','Elena Vasquez, David Okafor',94,'ADVANCE','Senior-level depth in credit risk; excellent stakeholder communication. Hired.'),
 ('81000000-0000-0000-0000-000000000010','60000000-0000-0000-0000-000000000018','80000000-0000-0000-0000-000000000014','ONE_WAY_VIDEO','2026-07-10 16:00:00+00',30,'COMPLETED','Olivia (AI)',80,'ADVANCE','Solid fundamentals for a new grad; enthusiastic and structured problem-solving.');

-- ---------------------------------------------------------------------
-- assessment  (8)  COGNITIVE/CODING/GAME/JOB_TRYOUT
-- ---------------------------------------------------------------------
insert into assessment (id, application_id, type, name, status, score, percentile, assigned_at, completed_at) values
 ('82000000-0000-0000-0000-000000000001','60000000-0000-0000-0000-000000000008','CODING','Backend Coding Challenge','COMPLETED',92,95,'2026-06-12 09:00:00+00','2026-06-13 18:00:00+00'),
 ('82000000-0000-0000-0000-000000000002','60000000-0000-0000-0000-000000000009','CODING','Backend Coding Challenge','COMPLETED',84,82,'2026-06-13 09:00:00+00','2026-06-14 17:00:00+00'),
 ('82000000-0000-0000-0000-000000000003','60000000-0000-0000-0000-000000000011','CODING','ML Take-Home','COMPLETED',90,93,'2026-06-15 09:00:00+00','2026-06-17 20:00:00+00'),
 ('82000000-0000-0000-0000-000000000004','60000000-0000-0000-0000-000000000012','COGNITIVE','Quantitative Reasoning','COMPLETED',95,98,'2026-06-13 09:00:00+00','2026-06-14 12:00:00+00'),
 ('82000000-0000-0000-0000-000000000005','60000000-0000-0000-0000-000000000019','CODING','Foundations Coding Test','IN_PROGRESS',null,null,'2026-06-23 09:00:00+00',null),
 ('82000000-0000-0000-0000-000000000006','60000000-0000-0000-0000-000000000001','JOB_TRYOUT','Branch Day Simulation','COMPLETED',88,90,'2026-06-18 09:00:00+00','2026-06-19 16:00:00+00'),
 ('82000000-0000-0000-0000-000000000007','60000000-0000-0000-0000-000000000006','GAME','Leadership Judgment Game','COMPLETED',86,88,'2026-06-08 09:00:00+00','2026-06-09 11:00:00+00'),
 ('82000000-0000-0000-0000-000000000008','60000000-0000-0000-0000-000000000018','COGNITIVE','Aptitude Battery','ASSIGNED',null,null,'2026-06-24 09:00:00+00',null);

-- ---------------------------------------------------------------------
-- offer  (6)  DRAFT/SENT/ACCEPTED/DECLINED
-- ---------------------------------------------------------------------
insert into offer (id, application_id, title, comp_base, comp_period, comp_bonus, equity, start_date, status, letter_body, sent_at, responded_at) values
 ('83000000-0000-0000-0000-000000000001','60000000-0000-0000-0000-000000000001','Personal Banker',58000.00,'YEAR',4000.00,null,'2026-07-14','ACCEPTED','We are delighted to offer you the Personal Banker role in Charlotte. We look forward to welcoming you to the team.','2026-06-22 10:00:00+00','2026-06-24 09:00:00+00'),
 ('83000000-0000-0000-0000-000000000002','60000000-0000-0000-0000-000000000004','Teller',23.00,'HOUR',1000.00,null,'2026-07-21','SENT','Congratulations! Please review your offer for the Teller position in Tampa.','2026-06-25 10:00:00+00',null),
 ('83000000-0000-0000-0000-000000000003','60000000-0000-0000-0000-000000000006','Branch Operations Manager',88000.00,'YEAR',12000.00,null,'2026-07-28','SENT','We are pleased to extend an offer for the Branch Operations Manager role in Phoenix.','2026-06-23 16:00:00+00',null),
 ('83000000-0000-0000-0000-000000000004','60000000-0000-0000-0000-000000000008','Senior Software Engineer',172000.00,'YEAR',25000.00,'RSU grant per plan','2026-07-28','ACCEPTED','We are thrilled to offer you the Senior Software Engineer position in New York.','2026-06-24 09:00:00+00','2026-06-26 09:00:00+00'),
 ('83000000-0000-0000-0000-000000000005','60000000-0000-0000-0000-000000000012','Data Scientist',158000.00,'YEAR',22000.00,'RSU grant per plan','2026-07-28','ACCEPTED','Congratulations on your offer for the Data Scientist role in Charlotte.','2026-06-25 10:00:00+00','2026-06-26 11:30:00+00'),
 ('83000000-0000-0000-0000-000000000006','60000000-0000-0000-0000-000000000014','Financial Solutions Advisor',82000.00,'YEAR',15000.00,null,'2026-08-04','DRAFT','Draft offer for the Financial Solutions Advisor role in Boston, pending final approval.',null,null);

-- ---------------------------------------------------------------------
-- onboarding_task  (10)  for HIRED applications  (apps 1, 8, 12, 16)
-- categories DOCS/IT/COMMS/DAY_ONE/COMPLIANCE
-- ---------------------------------------------------------------------
insert into onboarding_task (id, application_id, name, category, status, due_date, completed_at) values
 -- app 1 (Maria, Personal Banker — HIRED)
 ('84000000-0000-0000-0000-000000000001','60000000-0000-0000-0000-000000000001','Complete I-9 and tax forms','DOCS','COMPLETED','2026-07-07','2026-07-02 14:00:00+00'),
 ('84000000-0000-0000-0000-000000000002','60000000-0000-0000-0000-000000000001','Background check authorization','COMPLIANCE','COMPLETED','2026-07-05','2026-07-01 10:00:00+00'),
 ('84000000-0000-0000-0000-000000000003','60000000-0000-0000-0000-000000000001','Provision laptop and badge','IT','SENT','2026-07-12',null),
 ('84000000-0000-0000-0000-000000000004','60000000-0000-0000-0000-000000000001','Day-one welcome and branch tour','DAY_ONE','PENDING','2026-07-14',null),
 -- app 8 (Liam, Senior SWE — HIRED)
 ('84000000-0000-0000-0000-000000000005','60000000-0000-0000-0000-000000000008','Complete I-9 and tax forms','DOCS','COMPLETED','2026-07-18','2026-07-10 11:00:00+00'),
 ('84000000-0000-0000-0000-000000000006','60000000-0000-0000-0000-000000000008','Set up GitHub, VPN, and dev environment','IT','SENT','2026-07-25',null),
 ('84000000-0000-0000-0000-000000000007','60000000-0000-0000-0000-000000000008','Team welcome email and Slack intro','COMMS','PENDING','2026-07-28',null),
 -- app 12 (Hiroshi, Data Scientist — HIRED)
 ('84000000-0000-0000-0000-000000000008','60000000-0000-0000-0000-000000000012','Information-security and data-handling training','COMPLIANCE','SENT','2026-07-25',null),
 ('84000000-0000-0000-0000-000000000009','60000000-0000-0000-0000-000000000012','Day-one orientation and team intro','DAY_ONE','PENDING','2026-07-28',null),
 -- app 16 (Carlos, Compliance Analyst — HIRED)
 ('84000000-0000-0000-0000-000000000010','60000000-0000-0000-0000-000000000016','AML/KYC certification onboarding','COMPLIANCE','COMPLETED','2026-06-30','2026-06-28 15:00:00+00');

-- ---------------------------------------------------------------------
-- talent_pool  (4)  + talent_pool_member (12)
-- ---------------------------------------------------------------------
insert into talent_pool (id, name, description) values
 ('90000000-0000-0000-0000-000000000001','Bilingual Branch Talent','Bilingual candidates for client-facing financial center roles.'),
 ('90000000-0000-0000-0000-000000000002','Engineering Pipeline','Software and platform engineering candidates for Global Technology.'),
 ('90000000-0000-0000-0000-000000000003','Risk & Compliance Bench','AML/KYC, fraud, and risk-controls professionals.'),
 ('90000000-0000-0000-0000-000000000004','Early Career & Campus','New grads and interns across technology and banking.');

insert into talent_pool_member (pool_id, candidate_id, added_at) values
 ('90000000-0000-0000-0000-000000000001','50000000-0000-0000-0000-000000000001','2026-06-11 09:30:00+00'),
 ('90000000-0000-0000-0000-000000000001','50000000-0000-0000-0000-000000000017','2026-06-13 11:30:00+00'),
 ('90000000-0000-0000-0000-000000000001','50000000-0000-0000-0000-000000000014','2026-06-22 09:30:00+00'),
 ('90000000-0000-0000-0000-000000000002','50000000-0000-0000-0000-000000000004','2026-06-09 10:30:00+00'),
 ('90000000-0000-0000-0000-000000000002','50000000-0000-0000-0000-000000000013','2026-06-10 12:00:00+00'),
 ('90000000-0000-0000-0000-000000000002','50000000-0000-0000-0000-000000000005','2026-06-13 09:30:00+00'),
 ('90000000-0000-0000-0000-000000000003','50000000-0000-0000-0000-000000000007','2026-06-10 09:30:00+00'),
 ('90000000-0000-0000-0000-000000000003','50000000-0000-0000-0000-000000000010','2026-06-24 11:30:00+00'),
 ('90000000-0000-0000-0000-000000000003','50000000-0000-0000-0000-000000000018','2026-06-11 13:30:00+00'),
 ('90000000-0000-0000-0000-000000000004','50000000-0000-0000-0000-000000000008','2026-06-21 09:30:00+00'),
 ('90000000-0000-0000-0000-000000000004','50000000-0000-0000-0000-000000000009','2026-06-23 09:30:00+00'),
 ('90000000-0000-0000-0000-000000000004','50000000-0000-0000-0000-000000000016','2026-06-22 10:30:00+00');

-- ---------------------------------------------------------------------
-- nurture_campaign  (5)  with sent/opened/replied
-- ---------------------------------------------------------------------
insert into nurture_campaign (id, name, audience, channel, status, sent, opened, replied) values
 ('91000000-0000-0000-0000-000000000001','Re-engage Dormant Bankers','Dormant candidates with branch experience','EMAIL','RUNNING',1200,540,86),
 ('91000000-0000-0000-0000-000000000002','Tech Talent Warm-up','Passive software engineering candidates','EMAIL','RUNNING',800,410,72),
 ('91000000-0000-0000-0000-000000000003','Campus Class of 2026','University seniors in CS and finance','SMS','DONE',2500,1100,240),
 ('91000000-0000-0000-0000-000000000004','Bilingual Hiring Push','Bilingual candidates in FL and NC markets','WHATSAPP','RUNNING',600,300,95),
 ('91000000-0000-0000-0000-000000000005','Compliance Talent Network','Risk and compliance professionals','EMAIL','PAUSED',450,180,33);

-- ---------------------------------------------------------------------
-- referral  (6)  link_code unique, bonus
-- ---------------------------------------------------------------------
insert into referral (id, referrer_name, referrer_email, candidate_id, job_id, link_code, status, bonus_amount) values
 ('92000000-0000-0000-0000-000000000001','Marcus Bell','marcus.bell@bofa.com','50000000-0000-0000-0000-000000000002','30000000-0000-0000-0000-000000000001','REF-MB-0001','IN_REVIEW',2500.00),
 ('92000000-0000-0000-0000-000000000002','Daniel Kim','daniel.kim@example.com','50000000-0000-0000-0000-000000000013','30000000-0000-0000-0000-000000000004','REF-DK-0002','IN_REVIEW',5000.00),
 ('92000000-0000-0000-0000-000000000003','Elena Vasquez','elena.vasquez@bofa.com','50000000-0000-0000-0000-000000000005','30000000-0000-0000-0000-000000000005','REF-EV-0003','SUBMITTED',5000.00),
 ('92000000-0000-0000-0000-000000000004','Priya Raman','priya.raman@bofa.com',null,'30000000-0000-0000-0000-000000000006','REF-PR-0004','SUBMITTED',3000.00),
 ('92000000-0000-0000-0000-000000000005','Sarah Chen','sarah.chen@bofa.com','50000000-0000-0000-0000-000000000017','30000000-0000-0000-0000-000000000001','REF-SC-0005','CLOSED',2500.00),
 ('92000000-0000-0000-0000-000000000006','David Okafor','david.okafor@bofa.com','50000000-0000-0000-0000-000000000004','30000000-0000-0000-0000-000000000004','REF-DO-0006','HIRED',5000.00);

-- ---------------------------------------------------------------------
-- recruiting_event  (5)  HIRING_EVENT/CAMPUS/WEBINAR/CAREER_FAIR
-- ---------------------------------------------------------------------
insert into recruiting_event (id, name, type, location, starts_at, registrations, attended, hires) values
 ('93000000-0000-0000-0000-000000000001','Charlotte Branch Hiring Day','HIRING_EVENT','Charlotte, NC','2026-07-15 13:00:00+00',180,120,14),
 ('93000000-0000-0000-0000-000000000002','UNC Tech Career Fair','CAMPUS','Chapel Hill, NC','2026-09-22 10:00:00+00',350,210,0),
 ('93000000-0000-0000-0000-000000000003','Women in Engineering Webinar','WEBINAR','Virtual','2026-07-09 18:00:00+00',520,300,0),
 ('93000000-0000-0000-0000-000000000004','Tampa Financial Careers Fair','CAREER_FAIR','Tampa, FL','2026-08-05 11:00:00+00',240,150,6),
 ('93000000-0000-0000-0000-000000000005','NYC Summer Analyst Info Session','WEBINAR','Virtual','2026-07-01 17:00:00+00',640,400,0);

-- ---------------------------------------------------------------------
-- survey  (5)  one per stage  APPLICATION/INTERVIEW/OFFER/ONBOARDING/CANDIDATE_NPS
-- ---------------------------------------------------------------------
insert into survey (id, name, stage, sent, responses, avg_sentiment, nps) values
 ('94000000-0000-0000-0000-000000000001','Application Experience Pulse','APPLICATION',1500,640,0.62,48),
 ('94000000-0000-0000-0000-000000000002','Interview Experience Survey','INTERVIEW',420,280,0.55,42),
 ('94000000-0000-0000-0000-000000000003','Offer Experience Survey','OFFER',60,38,0.71,61),
 ('94000000-0000-0000-0000-000000000004','New Hire Onboarding Check-in','ONBOARDING',45,30,0.68,57),
 ('94000000-0000-0000-0000-000000000005','Candidate NPS','CANDIDATE_NPS',2000,820,0.40,35);

-- ---------------------------------------------------------------------
-- survey_response  (16)  rating/sentiment/verbatim
-- ---------------------------------------------------------------------
insert into survey_response (id, survey_id, candidate_id, rating, sentiment, verbatim, created_at) values
 ('95000000-0000-0000-0000-000000000001','94000000-0000-0000-0000-000000000001','50000000-0000-0000-0000-000000000001',9,0.80,'The chat made applying so easy, and in Spanish too!','2026-06-11 09:30:00+00'),
 ('95000000-0000-0000-0000-000000000002','94000000-0000-0000-0000-000000000001','50000000-0000-0000-0000-000000000002',8,0.55,'Quick and simple. Wished I could save and resume.','2026-06-12 11:00:00+00'),
 ('95000000-0000-0000-0000-000000000003','94000000-0000-0000-0000-000000000001','50000000-0000-0000-0000-000000000011',9,0.75,'Fast process, got scheduled within a day.','2026-06-16 10:00:00+00'),
 ('95000000-0000-0000-0000-000000000004','94000000-0000-0000-0000-000000000001','50000000-0000-0000-0000-000000000016',6,0.10,'Got rejected fast but unclear why.','2026-06-18 10:00:00+00'),
 ('95000000-0000-0000-0000-000000000005','94000000-0000-0000-0000-000000000002','50000000-0000-0000-0000-000000000001',9,0.78,'Interviewers were warm and well prepared.','2026-07-01 16:00:00+00'),
 ('95000000-0000-0000-0000-000000000006','94000000-0000-0000-0000-000000000002','50000000-0000-0000-0000-000000000004',10,0.85,'Great system-design conversation, very fair.','2026-07-06 17:00:00+00'),
 ('95000000-0000-0000-0000-000000000007','94000000-0000-0000-0000-000000000002','50000000-0000-0000-0000-000000000008',7,0.30,'Good experience but the wait between rounds was long.','2026-07-10 18:00:00+00'),
 ('95000000-0000-0000-0000-000000000008','94000000-0000-0000-0000-000000000002','50000000-0000-0000-0000-000000000003',9,0.72,'Panel was respectful and engaging.','2026-07-03 18:00:00+00'),
 ('95000000-0000-0000-0000-000000000009','94000000-0000-0000-0000-000000000003','50000000-0000-0000-0000-000000000001',10,0.90,'Offer was clear and the comp was competitive.','2026-06-24 10:00:00+00'),
 ('95000000-0000-0000-0000-000000000010','94000000-0000-0000-0000-000000000003','50000000-0000-0000-0000-000000000004',9,0.82,'Smooth offer process and quick turnaround.','2026-06-26 10:00:00+00'),
 ('95000000-0000-0000-0000-000000000011','94000000-0000-0000-0000-000000000003','50000000-0000-0000-0000-000000000015',8,0.60,'Good offer, would have liked more equity detail upfront.','2026-06-26 12:00:00+00'),
 ('95000000-0000-0000-0000-000000000012','94000000-0000-0000-0000-000000000004','50000000-0000-0000-0000-000000000001',9,0.77,'Onboarding tasks were clear and easy to follow.','2026-07-02 15:00:00+00'),
 ('95000000-0000-0000-0000-000000000013','94000000-0000-0000-0000-000000000004','50000000-0000-0000-0000-000000000007',8,0.58,'Good start, a bit much paperwork on day one.','2026-06-29 09:00:00+00'),
 ('95000000-0000-0000-0000-000000000014','94000000-0000-0000-0000-000000000005','50000000-0000-0000-0000-000000000006',8,0.50,'Overall positive experience with the recruiter.','2026-06-25 14:00:00+00'),
 ('95000000-0000-0000-0000-000000000015','94000000-0000-0000-0000-000000000005','50000000-0000-0000-0000-000000000018',4,-0.40,'Never heard back after the screen — disappointing.','2026-06-21 11:00:00+00'),
 ('95000000-0000-0000-0000-000000000016','94000000-0000-0000-0000-000000000005','50000000-0000-0000-0000-000000000010',7,0.35,'Decent, but the process felt slow at times.','2026-06-24 12:00:00+00');

-- ---------------------------------------------------------------------
-- internal_employee  (10)  uses current_title
-- ---------------------------------------------------------------------
insert into internal_employee (id, name, current_title, department, tenure_years, location) values
 ('a0000000-0000-0000-0000-000000000001','Robert Hayes','Teller','Consumer Banking',3.0,'Charlotte, NC'),
 ('a0000000-0000-0000-0000-000000000002','Linda Park','Personal Banker','Consumer Banking',5.0,'Tampa, FL'),
 ('a0000000-0000-0000-0000-000000000003','Omar Haddad','Branch Operations Manager','Consumer Banking',9.0,'Phoenix, AZ'),
 ('a0000000-0000-0000-0000-000000000004','Jenna Wu','Software Engineer','Global Technology',4.0,'Charlotte, NC'),
 ('a0000000-0000-0000-0000-000000000005','Kevin Ross','Senior Software Engineer','Global Technology',7.0,'New York, NY'),
 ('a0000000-0000-0000-0000-000000000006','Priscilla Adams','Data Analyst','Global Risk Analytics',3.5,'Charlotte, NC'),
 ('a0000000-0000-0000-0000-000000000007','Tomas Nilsson','Compliance Specialist','Global Compliance',6.0,'Charlotte, NC'),
 ('a0000000-0000-0000-0000-000000000008','Renee Dubois','Financial Solutions Advisor','Merrill',4.5,'Boston, MA'),
 ('a0000000-0000-0000-0000-000000000009','Marcus Webb','Operations Analyst','Consumer Banking',2.5,'Phoenix, AZ'),
 ('a0000000-0000-0000-0000-000000000010','Yuki Sato','Machine Learning Engineer','Global Risk Analytics',5.5,'New York, NY');

-- ---------------------------------------------------------------------
-- internal_employee_skill  (24)
-- ---------------------------------------------------------------------
insert into internal_employee_skill (employee_id, skill_id, proficiency) values
 ('a0000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000009',4),
 ('a0000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000021',4),
 ('a0000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000010',3),
 ('a0000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000022',5),
 ('a0000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000021',5),
 ('a0000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000023',4),
 ('a0000000-0000-0000-0000-000000000003','20000000-0000-0000-0000-000000000010',5),
 ('a0000000-0000-0000-0000-000000000003','20000000-0000-0000-0000-000000000013',5),
 ('a0000000-0000-0000-0000-000000000003','20000000-0000-0000-0000-000000000014',4),
 ('a0000000-0000-0000-0000-000000000004','20000000-0000-0000-0000-000000000001',4),
 ('a0000000-0000-0000-0000-000000000004','20000000-0000-0000-0000-000000000003',4),
 ('a0000000-0000-0000-0000-000000000004','20000000-0000-0000-0000-000000000005',3),
 ('a0000000-0000-0000-0000-000000000005','20000000-0000-0000-0000-000000000001',5),
 ('a0000000-0000-0000-0000-000000000005','20000000-0000-0000-0000-000000000004',5),
 ('a0000000-0000-0000-0000-000000000005','20000000-0000-0000-0000-000000000005',4),
 ('a0000000-0000-0000-0000-000000000006','20000000-0000-0000-0000-000000000005',4),
 ('a0000000-0000-0000-0000-000000000006','20000000-0000-0000-0000-000000000008',4),
 ('a0000000-0000-0000-0000-000000000006','20000000-0000-0000-0000-000000000006',3),
 ('a0000000-0000-0000-0000-000000000007','20000000-0000-0000-0000-000000000017',4),
 ('a0000000-0000-0000-0000-000000000007','20000000-0000-0000-0000-000000000018',4),
 ('a0000000-0000-0000-0000-000000000008','20000000-0000-0000-0000-000000000024',4),
 ('a0000000-0000-0000-0000-000000000009','20000000-0000-0000-0000-000000000012',4),
 ('a0000000-0000-0000-0000-000000000010','20000000-0000-0000-0000-000000000007',5),
 ('a0000000-0000-0000-0000-000000000010','20000000-0000-0000-0000-000000000002',5);

-- ---------------------------------------------------------------------
-- mobility_match  (8)  employee -> job
-- ---------------------------------------------------------------------
insert into mobility_match (id, employee_id, job_id, fit_score, rationale) values
 ('a1000000-0000-0000-0000-000000000001','a0000000-0000-0000-0000-000000000001','30000000-0000-0000-0000-000000000001',82,'Teller with strong cash handling and service ready to grow into Personal Banker.'),
 ('a1000000-0000-0000-0000-000000000002','a0000000-0000-0000-0000-000000000002','30000000-0000-0000-0000-000000000006',79,'Personal Banker with relationship skills could move into advisory with licensing.'),
 ('a1000000-0000-0000-0000-000000000003','a0000000-0000-0000-0000-000000000009','30000000-0000-0000-0000-000000000003',75,'Operations Analyst with process-improvement depth fits Branch Operations Manager track.'),
 ('a1000000-0000-0000-0000-000000000004','a0000000-0000-0000-0000-000000000004','30000000-0000-0000-0000-000000000004',81,'Software Engineer with Java/React ready for senior backend role.'),
 ('a1000000-0000-0000-0000-000000000005','a0000000-0000-0000-0000-000000000006','30000000-0000-0000-0000-000000000005',84,'Data Analyst with SQL and modeling strong fit to grow into Data Scientist.'),
 ('a1000000-0000-0000-0000-000000000006','a0000000-0000-0000-0000-000000000010','30000000-0000-0000-0000-000000000005',92,'ML Engineer is an excellent internal match for the Data Scientist opening.'),
 ('a1000000-0000-0000-0000-000000000007','a0000000-0000-0000-0000-000000000007','30000000-0000-0000-0000-000000000007',88,'Compliance Specialist with AML/KYC ideal for the Compliance Analyst role.'),
 ('a1000000-0000-0000-0000-000000000008','a0000000-0000-0000-0000-000000000005','30000000-0000-0000-0000-000000000004',90,'Senior Software Engineer is a near-exact internal match for the open req.');

-- ---------------------------------------------------------------------
-- copilot_artifact  (5)  JD/OUTREACH/INTERVIEW_QUESTIONS/SUMMARY
-- ---------------------------------------------------------------------
insert into copilot_artifact (id, kind, job_id, prompt, content, created_by) values
 ('b0000000-0000-0000-0000-000000000001','JD','30000000-0000-0000-0000-000000000004','Draft an inclusive JD for a Senior Software Engineer in Global Technology.','Senior Software Engineer — Global Technology. Design and operate resilient distributed services... (full generated JD body).','10000000-0000-0000-0000-000000000003'),
 ('b0000000-0000-0000-0000-000000000002','OUTREACH','30000000-0000-0000-0000-000000000005','Write a warm outreach message to a passive ML candidate.','Hi Wei — your fraud-modeling work caught our eye. We are building risk models at scale and would love to chat about our Data Scientist role.','10000000-0000-0000-0000-000000000003'),
 ('b0000000-0000-0000-0000-000000000003','INTERVIEW_QUESTIONS','30000000-0000-0000-0000-000000000003','Generate behavioral interview questions for a Branch Operations Manager.','1) Tell me about a time you turned around an underperforming branch. 2) How do you coach a struggling banker? 3) Describe a control gap you closed.','10000000-0000-0000-0000-000000000002'),
 ('b0000000-0000-0000-0000-000000000004','SUMMARY','30000000-0000-0000-0000-000000000004','Summarize the panel interview feedback for Liam O''Brien.','Strong hire. Deep distributed-systems expertise, excellent system-design round, collaborative. Recommend offer at senior level.','10000000-0000-0000-0000-000000000003'),
 ('b0000000-0000-0000-0000-000000000005','OUTREACH','30000000-0000-0000-0000-000000000001','Draft a bilingual re-engagement note for dormant branch candidates.','Hola — tenemos nuevas oportunidades en tu área para roles de Personal Banker. Nos encantaría reconectar contigo.','10000000-0000-0000-0000-000000000002');

-- ---------------------------------------------------------------------
-- integration  (8)  categories ATS/HRIS/CALENDAR/MESSAGING/BI/ASSESSMENT ; status CONNECTED/AVAILABLE/ERROR
-- ---------------------------------------------------------------------
insert into integration (id, name, category, status, last_sync_at) values
 ('b1000000-0000-0000-0000-000000000001','Workday','HRIS','CONNECTED','2026-06-27 02:00:00+00'),
 ('b1000000-0000-0000-0000-000000000002','Greenhouse','ATS','CONNECTED','2026-06-27 01:30:00+00'),
 ('b1000000-0000-0000-0000-000000000003','iCIMS','ATS','AVAILABLE',null),
 ('b1000000-0000-0000-0000-000000000004','SAP SuccessFactors','HRIS','AVAILABLE',null),
 ('b1000000-0000-0000-0000-000000000005','Oracle HCM','HRIS','ERROR','2026-06-25 03:00:00+00'),
 ('b1000000-0000-0000-0000-000000000006','Slack','MESSAGING','CONNECTED','2026-06-27 06:00:00+00'),
 ('b1000000-0000-0000-0000-000000000007','Outlook Calendar','CALENDAR','CONNECTED','2026-06-27 06:15:00+00'),
 ('b1000000-0000-0000-0000-000000000008','Power BI','BI','CONNECTED','2026-06-27 04:00:00+00');

-- ---------------------------------------------------------------------
-- audit_event  (12)  recent
-- ---------------------------------------------------------------------
insert into audit_event (id, actor, action, entity_type, entity_id, detail, created_at) values
 ('b2000000-0000-0000-0000-000000000001','priya.raman@bofa.com','JOB_PUBLISHED','job','30000000-0000-0000-0000-000000000001','Published Personal Banker req to career site.','2026-06-10 13:00:00+00'),
 ('b2000000-0000-0000-0000-000000000002','olivia','APPLICATION_CREATED','application','60000000-0000-0000-0000-000000000001','Maria Gonzalez applied via WEB_CHAT.','2026-06-11 09:08:00+00'),
 ('b2000000-0000-0000-0000-000000000003','olivia','KNOCKOUT_EVALUATED','application','60000000-0000-0000-0000-000000000010','Knockout failed: experience below threshold.','2026-06-15 10:00:00+00'),
 ('b2000000-0000-0000-0000-000000000004','marcus.bell@bofa.com','STAGE_CHANGED','application','60000000-0000-0000-0000-000000000002','Moved to INTERVIEW.','2026-06-22 14:00:00+00'),
 ('b2000000-0000-0000-0000-000000000005','elena.vasquez@bofa.com','INTERVIEW_SCHEDULED','interview','81000000-0000-0000-0000-000000000006','Panel scheduled for Senior Software Engineer.','2026-06-23 09:00:00+00'),
 ('b2000000-0000-0000-0000-000000000006','olivia','ASSESSMENT_ASSIGNED','assessment','82000000-0000-0000-0000-000000000003','ML take-home assigned to Wei Zhang.','2026-06-15 09:00:00+00'),
 ('b2000000-0000-0000-0000-000000000007','priya.raman@bofa.com','OFFER_SENT','offer','83000000-0000-0000-0000-000000000004','Offer sent for Senior Software Engineer.','2026-06-24 09:00:00+00'),
 ('b2000000-0000-0000-0000-000000000008','liam.obrien@example.com','OFFER_ACCEPTED','offer','83000000-0000-0000-0000-000000000004','Candidate accepted offer.','2026-06-26 09:00:00+00'),
 ('b2000000-0000-0000-0000-000000000009','sarah.chen@bofa.com','ONBOARDING_STARTED','application','60000000-0000-0000-0000-000000000001','Onboarding tasks generated.','2026-06-24 17:30:00+00'),
 ('b2000000-0000-0000-0000-000000000010','system','INTEGRATION_SYNC','integration','b1000000-0000-0000-0000-000000000001','Workday nightly sync completed.','2026-06-27 02:00:00+00'),
 ('b2000000-0000-0000-0000-000000000011','system','INTEGRATION_ERROR','integration','b1000000-0000-0000-0000-000000000005','Oracle HCM sync failed: auth token expired.','2026-06-25 03:00:00+00'),
 ('b2000000-0000-0000-0000-000000000012','priya.raman@bofa.com','BIAS_REPORT_GENERATED','job','30000000-0000-0000-0000-000000000004','Quarterly adverse-impact analysis generated.','2026-06-26 20:00:00+00');

-- ---------------------------------------------------------------------
-- bias_report  (8)  dimensions GENDER/ETHNICITY/AGE/OVERALL ; pass_rate + impact_ratio ; a couple flagged
-- ---------------------------------------------------------------------
insert into bias_report (id, job_id, stage, dimension, group_label, pass_rate, impact_ratio, flagged) values
 ('b3000000-0000-0000-0000-000000000001','30000000-0000-0000-0000-000000000004','SCREENED','GENDER','Female',62.00,0.91,false),
 ('b3000000-0000-0000-0000-000000000002','30000000-0000-0000-0000-000000000004','SCREENED','GENDER','Male',68.00,1.00,false),
 ('b3000000-0000-0000-0000-000000000003','30000000-0000-0000-0000-000000000004','INTERVIEW','ETHNICITY','Underrepresented',54.00,0.74,true),
 ('b3000000-0000-0000-0000-000000000004','30000000-0000-0000-0000-000000000001','SCREENED','AGE','40+',58.00,0.82,false),
 ('b3000000-0000-0000-0000-000000000005','30000000-0000-0000-0000-000000000001','SCREENED','AGE','Under 40',71.00,1.00,false),
 ('b3000000-0000-0000-0000-000000000006','30000000-0000-0000-0000-000000000005','ASSESSMENT','ETHNICITY','Underrepresented',49.00,0.71,true),
 ('b3000000-0000-0000-0000-000000000007','30000000-0000-0000-0000-000000000005','ASSESSMENT','GENDER','Female',60.00,0.93,false),
 ('b3000000-0000-0000-0000-000000000008',null,'OVERALL','OVERALL','All candidates',64.00,1.00,false);

-- ---------------------------------------------------------------------
-- metric_daily  (30 days x 8 keys = 240)  UNIQUE(metric_date, metric_key)
-- keys: applications, screened, scheduled, interviews, offers, hires,
--       time_to_hire_days, avg_fit_score
-- Generated procedurally to guarantee uniqueness and full coverage.
-- ---------------------------------------------------------------------
insert into metric_daily (id, metric_date, metric_key, value)
select
  gen_random_uuid(),
  d::date,
  k.metric_key,
  case k.metric_key
    when 'applications'      then 30 + (extract(day from d)::int % 7) * 6
    when 'screened'          then 18 + (extract(day from d)::int % 7) * 4
    when 'scheduled'         then 8  + (extract(day from d)::int % 5) * 2
    when 'interviews'        then 6  + (extract(day from d)::int % 5) * 2
    when 'offers'            then 1  + (extract(day from d)::int % 3)
    when 'hires'             then (extract(day from d)::int % 3)
    when 'time_to_hire_days' then 26 + (extract(day from d)::int % 6)
    when 'avg_fit_score'     then 70 + (extract(day from d)::int % 11)
  end
from generate_series(timestamptz '2026-05-29', timestamptz '2026-06-27', interval '1 day') as d
cross join (values
  ('applications'),
  ('screened'),
  ('scheduled'),
  ('interviews'),
  ('offers'),
  ('hires'),
  ('time_to_hire_days'),
  ('avg_fit_score')
) as k(metric_key);
