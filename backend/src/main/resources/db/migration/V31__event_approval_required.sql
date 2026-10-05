-- An event must be approved before students can see it. Events that existed before this rule
-- (and never went for approval) stay visible as they were; every event created from now on is
-- marked, and is public only once an approval request for it has been approved.
alter table recruiting_event add column approval_required boolean not null default false;
