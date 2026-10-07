-- How an interview's time was set: by the candidate in a text, on the self-schedule page or in
-- chat (all three from times the system worked out from the interviewers' calendars), or by the
-- hiring team picking it. Null for interviews booked before this was recorded.
alter table interview add column booked_via text;
