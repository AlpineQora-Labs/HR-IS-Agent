-- The job ID a posting carries on the careers site (eight digits, e.g. 26032144), so a job here can
-- be matched to its posting there. The sample jobs take the ID of the closest posting found on
-- careers.bankofamerica.com on 2026-10-07; a job with no close posting is left without one.
alter table job add column job_no text;

update job set job_no = '26032144' where title = 'Personal Banker';                 -- Relationship Banker
update job set job_no = '26036615' where title = 'Teller';                          -- Relationship Banker (no Teller posting found)
update job set job_no = '26024269' where title = 'Software Engineer (New Grad)';    -- Software Engineer
update job set job_no = '26035213' where title = 'Senior Software Engineer';        -- Lead Software Engineer, Payments Modernization
update job set job_no = '26034307' where title = 'Data Scientist';                  -- Data Scientist I
update job set job_no = '26036753' where title = 'Financial Solutions Advisor';     -- Financial Solutions Advisor
update job set job_no = '26036716' where title = 'Compliance Analyst';              -- Compliance and Operational Risk Specialist
update job set job_no = '26033877' where title = 'Branch Operations Manager';       -- Operations Manager, Phoenix, AZ
update job set job_no = '26036845' where title = 'Wealth Management Associate';     -- Merrill Wealth Management Client Associate
