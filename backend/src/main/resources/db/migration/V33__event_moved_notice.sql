-- When an event that students have registered for is moved (its date, time, place or format
-- changes), they are told the new details once the change is approved. This marks an event
-- whose students are still owed that message; it is cleared when the message is written.
alter table recruiting_event add column moved_at timestamptz;
