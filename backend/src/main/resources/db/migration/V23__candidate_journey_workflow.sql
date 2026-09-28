-- The candidate journey drawn on the workflow canvas: apply -> status SMS ->
-- self-schedule via chat -> IDV -> confirmation SMS -> reschedule branch
-- (SMS or chat loops back to scheduling) -> reminder SMS.

INSERT INTO approval_workflows (wf_key, name, trigger_type, enabled, auto_approve, levels_json, graph_json)
VALUES ('cj1', 'Candidate journey', 'Candidate journey', true, false, '[]', $$
{"nodes":[
 {"id":"trigger","type":"trigger","position":{"x":300,"y":0},"data":{"label":"Candidate applies"}},
 {"id":"sms-received","type":"sms","position":{"x":300,"y":130},"data":{"label":"Application received","smsTemplateId":"70000000-0000-0000-0000-000000000001","smsTemplateName":"Application received","smsTrigger":"When this point is reached"}},
 {"id":"step-schedule","type":"step","position":{"x":300,"y":260},"data":{"label":"Self-schedule via chat"}},
 {"id":"step-idv","type":"step","position":{"x":300,"y":390},"data":{"label":"Identity verification (IDV)"}},
 {"id":"sms-confirmed","type":"sms","position":{"x":300,"y":520},"data":{"label":"Congratulations SMS","smsTemplateId":"70000000-0000-0000-0000-000000000003","smsTemplateName":"Interview scheduled","smsTrigger":"When the interview is scheduled"}},
 {"id":"cond-resched","type":"condition","position":{"x":300,"y":650},"data":{"condition":"Reschedule requested"}},
 {"id":"step-resched","type":"step","position":{"x":560,"y":800},"data":{"label":"Reschedule — SMS or chat"}},
 {"id":"sms-reminder","type":"sms","position":{"x":40,"y":800},"data":{"label":"Interview reminder","smsTemplateId":"70000000-0000-0000-0000-000000000004","smsTemplateName":"Interview reminder","smsTrigger":"24 hours before the interview"}},
 {"id":"end","type":"end","position":{"x":300,"y":950},"data":{"label":"Interview complete"}}
],"edges":[
 {"id":"e-t-s1","source":"trigger","target":"sms-received"},
 {"id":"e-s1-sch","source":"sms-received","target":"step-schedule"},
 {"id":"e-sch-idv","source":"step-schedule","target":"step-idv"},
 {"id":"e-idv-s2","source":"step-idv","target":"sms-confirmed"},
 {"id":"e-s2-c","source":"sms-confirmed","target":"cond-resched"},
 {"id":"e-c-resched","source":"cond-resched","target":"step-resched","sourceHandle":"yes"},
 {"id":"e-c-rem","source":"cond-resched","target":"sms-reminder","sourceHandle":"no"},
 {"id":"e-resched-sch","source":"step-resched","target":"step-schedule","targetHandle":"r"},
 {"id":"e-rem-end","source":"sms-reminder","target":"end"}
]}
$$);
