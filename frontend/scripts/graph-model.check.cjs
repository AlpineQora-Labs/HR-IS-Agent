/* Checks for the workflow drawing logic (graphModel.ts). Run: npm run check:graph */
const assert = require('node:assert/strict')
// Compiled graphModel.js lives wherever `npm run check:graph` put it.
const g = require(require('node:path').join(process.env.GM_DIR || __dirname, 'graphModel.js'))
const N = (id, type, data = {}) => ({ id, type, data })
const E = (id, source, target, sourceHandle) => ({ id, source, target, sourceHandle })
let n = 0
const t = (name, fn) => { fn(); n++; console.log('  ok  ' + name) }

// Event approval: trigger -> lvl -> rule -> YES exc / NO end
const ev = {
  nodes: [N('trigger','trigger',{label:'Event'}), N('lvl','approval',{label:'Level 1',approverRole:'Hiring Manager'}),
          N('rule','condition',{condition:'Flagged critical'}), N('exc','exception',{label:'Compliance review'}), N('end','end',{label:'Approved'})],
  edges: [E('e1','trigger','lvl'), E('e2','lvl','rule'), E('e3','rule','exc','yes'), E('e4','rule','end','no')],
}
t('a side ending in an exception stops', () => assert.equal(g.sideOf(ev.nodes, ev.edges, 'rule', 'yes').state, 'stop'))
t('a side going to the outcome continues', () => {
  const s = g.sideOf(ev.nodes, ev.edges, 'rule', 'no'); assert.equal(s.state, 'continue'); assert.equal(s.join.id, 'end') })
t('answers only rejoin when both carry on', () => assert.equal(g.joinOf(ev.nodes, ev.edges, 'rule'), undefined))
t('without the rule, requests go to the answer that carries on', () => assert.equal(g.afterRule(ev.nodes, ev.edges, 'rule').id, 'end'))
t('the exception side goes left, the main flow stays centred', () => {
  const l = g.lanes(ev.nodes, ev.edges); assert.equal(l.get('exc'), -1); assert.equal(l.get('end'), 0); assert.equal(l.get('lvl'), 0) })

// a freshly inserted rule: both answers lead to the same step
const fresh = {
  nodes: [N('trigger','trigger'), N('r','condition',{condition:''}), N('end','end')],
  edges: [E('a','trigger','r'), E('y','r','end','yes'), E('n','r','end','no')],
}
t('a new rule is complete: both answers continue', () => {
  assert.equal(g.sideOf(fresh.nodes, fresh.edges, 'r', 'yes').state, 'continue')
  assert.equal(g.sideOf(fresh.nodes, fresh.edges, 'r', 'no').state, 'continue')
  assert.equal(g.joinOf(fresh.nodes, fresh.edges, 'r').id, 'end') })
t('nothing chosen reads as such', () => {
  assert.equal(g.ruleTitle(''), 'Choose what to check'); assert.equal(g.ruleSentence(''), null)
  assert.equal(g.ruleTitle('Flagged critical'), 'If the request is flagged critical')
  assert.equal(g.ruleTitle(undefined), 'Always') })
t('both answers in lane 0 when they go to the same step', () => assert.equal(g.lanes(fresh.nodes, fresh.edges).get('end'), 0))

// a step on the YES side only
const withStep = {
  nodes: [N('trigger','trigger'), N('r','condition',{condition:'Flagged critical'}),
          N('a','approval',{label:'Compliance',approverRole:'Compliance'}), N('end','end',{label:'Approved'})],
  edges: [E('1','trigger','r'), E('2','r','a','yes'), E('3','a','end'), E('4','r','end','no')],
}
t('a side with its own step lists it and names where it rejoins', () => {
  const s = g.sideOf(withStep.nodes, withStep.edges, 'r', 'yes')
  assert.equal(s.state, 'steps'); assert.deepEqual(s.steps.map(x => x.id), ['a']); assert.equal(s.join.id, 'end') })
t('YES-only steps sit one lane left; the join stays centred', () => {
  const l = g.lanes(withStep.nodes, withStep.edges); assert.equal(l.get('a'), -1); assert.equal(l.get('end'), 0) })
t('the workflow reads back in order', () => {
  const s = g.describe(withStep.nodes, withStep.edges).map(x => x.text)
  assert.equal(s.length, 3)
  assert.match(s[1], /^If the request is flagged critical: Compliance must approve, then it continues to Approved\. Otherwise it continues to Approved\.$/)
  assert.equal(s[2], 'Outcome: Approved') })

// open side
const open = { nodes: [N('trigger','trigger'), N('r','condition',{condition:'Virtual event'}), N('end','end')],
               edges: [E('1','trigger','r'), E('2','r','end','yes')] }
t('an answer with no line is open', () => {
  const s = g.sideOf(open.nodes, open.edges, 'r', 'no'); assert.equal(s.state, 'open')
  assert.match(g.describe(open.nodes, open.edges)[1].text, /Otherwise nothing is chosen yet\.$/) })

// candidate journey loop: reschedule loops back to scheduling
const cj = {
  nodes: [N('trigger','trigger',{label:'Candidate applies'}), N('sched','step',{label:'Self-schedule'}), N('sms','sms',{smsTemplateName:'Interview scheduled'}),
          N('c','condition',{condition:'Flagged critical'}), N('re','step',{label:'Reschedule'}), N('rem','sms',{smsTemplateName:'Interview reminder'}), N('end','end',{label:'Interview complete'})],
  edges: [E('1','trigger','sched'), E('2','sched','sms'), E('3','sms','c'), E('4','c','re','yes'), E('5','c','rem','no'), E('6','re','sched'), E('7','rem','end')],
}
t('a loop back is found and does not hang anything', () => {
  assert.deepEqual([...g.backEdges(cj.nodes, cj.edges)], ['6'])
  const l = g.lanes(cj.nodes, cj.edges)
  assert.equal(l.get('re'), -1); assert.equal(l.get('rem'), 0); assert.equal(l.get('end'), 0) })
t('a journey that loops reads back in full, saying where it goes back to', () => {
  assert.deepEqual(g.describe(cj.nodes, cj.edges).map(x => x.text), [
    'Starts when: Candidate applies',
    'Self-schedule',
    'The text message “Interview scheduled” is sent',
    'If the request is flagged critical: reschedule, then it goes back to Self-schedule. Otherwise it carries on.',
    'The text message “Interview reminder” is sent',
    'Outcome: Interview complete',
  ]) })
t('a side that loops lists its step, where it returns to, and does not carry on', () => {
  const s = g.sideOf(cj.nodes, cj.edges, 'c', 'yes')
  assert.equal(s.state, 'steps'); assert.equal(s.join, undefined); assert.equal(s.returnsTo.id, 'sched')
  assert.equal(g.carriesOn(s), false) })
t('an answer whose own line loops back says so', () => {
  const loop = { nodes: [N('trigger','trigger'), N('sched','step',{label:'Self-schedule'}), N('c','condition',{condition:'Flagged critical'}), N('end','end',{label:'Done'})],
                 edges: [E('1','trigger','sched'), E('2','sched','c'), E('3','c','sched','yes'), E('4','c','end','no')] }
  const s = g.sideOf(loop.nodes, loop.edges, 'c', 'yes')
  assert.equal(s.state, 'back'); assert.deepEqual(s.steps, [])
  assert.equal(g.describe(loop.nodes, loop.edges)[2].text,
    'If the request is flagged critical: it goes back to Self-schedule. Otherwise it carries on.')
  assert.equal(g.afterRule(loop.nodes, loop.edges, 'c').id, 'end') })

// cutting the flow
t('stopping the flow on a line counts what is no longer reached', () => {
  const next = [E('e1','trigger','lvl'), E('x','lvl','exc2')]
  const cut = g.cutOff([...ev.nodes, N('exc2','exception')], ev.edges, next).map(x => x.id).sort()
  assert.deepEqual(cut, ['end','exc','rule']) })


// guarded exception: YES stops, NO is the rest of the flow
const guarded = {
  nodes: [N('trigger','trigger',{label:'Event'}), N('g','condition',{condition:''}), N('x','exception',{label:'Sent for review'}),
          N('lvl','approval',{label:'Level 1',approverRole:'Hiring Manager'}), N('r','condition',{condition:'Flagged critical'}),
          N('a','approval',{label:'New approval',approverRole:'Admin'}), N('end','end',{label:'Approved'})],
  edges: [E('1','trigger','g'), E('2','g','x','yes'), E('3','g','lvl','no'), E('4','lvl','r'), E('5','r','a','yes'), E('6','a','end'), E('7','r','end','no')],
}
t('after a guarded exception the read-back carries on through the whole flow', () => {
  const s = g.describe(guarded.nodes, guarded.edges).map(x => x.text)
  assert.deepEqual(s, [
    'Starts when: Event',
    'A rule with nothing to check yet: it stops and is sent for review (Sent for review). Otherwise it carries on.',
    'Hiring Manager must approve',
    'If the request is flagged critical: Admin must approve, then it continues to Approved. Otherwise it continues to Approved.',
    'Outcome: Approved',
  ]) })
t('acronyms in step names survive being put mid-sentence', () => {
  const j = { nodes: [N('trigger','trigger'), N('r','condition',{condition:'Virtual event'}), N('v','step',{label:'IDV check'}),
                      N('s','step',{label:'Self-schedule via chat'}), N('end','end',{label:'Done'})],
              edges: [E('1','trigger','r'), E('2','r','v','yes'), E('3','v','end'), E('4','r','s','no'), E('5','s','end')] }
  assert.match(g.describe(j.nodes, j.edges)[1].text, /: IDV check, then it continues to Done\. Otherwise self-schedule via chat, then/) })
t('each sentence points at its own step', () => {
  assert.deepEqual(g.describe(guarded.nodes, guarded.edges).map(x => x.id), ['trigger','g','lvl','r','end']) })

// a rule closing one side of another rule
const nested = {
  nodes: [N('trigger','trigger'), N('o','condition',{condition:'Virtual event'}), N('i','condition',{condition:'Flagged critical'}),
          N('a','approval',{label:'A',approverRole:'Admin'}), N('end','end',{label:'Approved'})],
  edges: [E('1','trigger','o'), E('2','o','i','yes'), E('3','o','end','no'), E('4','i','a','yes'), E('5','a','end'), E('6','i','end','no')],
}
t('a rule inside a side gets a sentence of its own, in order, before the flow carries on', () => {
  assert.deepEqual(g.describe(nested.nodes, nested.edges), [
    { id: 'trigger', text: 'Starts when: Start' },
    { id: 'o', text: 'If the event is virtual: another rule is checked (if the request is flagged critical). Otherwise it continues to Approved.' },
    { id: 'i', text: 'If the request is flagged critical: Admin must approve, then it continues to Approved. Otherwise it continues to Approved.' },
    { id: 'end', text: 'Outcome: Approved' },
  ]) })
t('a rule inside the side that stops is told before the main flow, and never runs past it', () => {
  // o: YES -> i (i: YES stops, NO rejoins the main flow at j) · NO -> lvl -> j -> end
  const w = { nodes: [N('trigger','trigger'), N('o','condition',{condition:'Virtual event'}), N('i','condition',{condition:'Flagged critical'}),
                      N('x','exception',{label:'Compliance review'}), N('lvl','approval',{approverRole:'Hiring Manager'}),
                      N('j','approval',{approverRole:'Admin'}), N('end','end',{label:'Approved'})],
              edges: [E('1','trigger','o'), E('2','o','i','yes'), E('3','o','lvl','no'), E('4','i','x','yes'), E('5','i','j','no'),
                      E('6','lvl','j'), E('7','j','end')] }
  const told = g.describe(w.nodes, w.edges)
  assert.deepEqual(told.map(x => x.id), ['trigger','o','i','j','end'])
  assert.equal(told[1].text, 'If the event is virtual: another rule is checked (if the request is flagged critical). Otherwise Hiring Manager must approve, then it continues to Approval.')
  assert.equal(told[2].text, 'If the request is flagged critical: it stops and is sent for review (Compliance review). Otherwise it carries on.') })

// removing a rule
t('removing a guard keeps the whole flow after it', () => {
  // g: YES stops, NO is the rest of the flow (lvl -> r -> ... -> end)
  assert.equal(g.afterRule(guarded.nodes, guarded.edges, 'g').id, 'lvl')
  assert.equal(g.joinOf(guarded.nodes, guarded.edges, 'g'), undefined) })
t('removing a rule whose answers rejoin sends requests to where they rejoin', () => {
  assert.equal(g.afterRule(withStep.nodes, withStep.edges, 'r').id, 'end')
  assert.equal(g.afterRule(fresh.nodes, fresh.edges, 'r').id, 'end') })
t('a rule with no answer that carries on leads nowhere when removed', () => {
  const dead = { nodes: [N('trigger','trigger'), N('r','condition',{condition:'Virtual event'}), N('x','exception')],
                 edges: [E('1','trigger','r'), E('2','r','x','yes')] }
  assert.equal(g.afterRule(dead.nodes, dead.edges, 'r'), undefined) })
t('steps then an exception is a side that stops, and is read that way', () => {
  const w = { nodes: [N('trigger','trigger'), N('r','condition',{condition:'Flagged critical'}), N('a','approval',{approverRole:'Compliance'}),
                      N('x','exception',{label:'Declined'}), N('lvl','approval',{approverRole:'Hiring Manager'}), N('end','end',{label:'Approved'})],
              edges: [E('1','trigger','r'), E('2','r','a','yes'), E('3','a','x'), E('4','r','lvl','no'), E('5','lvl','end')] }
  assert.equal(g.carriesOn(g.sideOf(w.nodes, w.edges, 'r', 'yes')), false)
  assert.equal(g.afterRule(w.nodes, w.edges, 'r').id, 'lvl')
  assert.equal(g.describe(w.nodes, w.edges)[1].text,
    'If the request is flagged critical: Compliance must approve, then it stops and is sent for review (Declined). Otherwise it carries on.') })

// removing a rule whose other answer goes nowhere useful
t('an unfinished side is not the rest of the flow: removing the rule keeps the flow that reaches the outcome', () => {
  // r: YES -> a5 (nothing after it yet) · NO -> lvl1 -> lvl2 -> end
  const w = { nodes: [N('trigger','trigger'), N('r','condition',{condition:'Flagged critical'}), N('a5','approval',{approverRole:'Admin'}),
                      N('lvl1','approval',{approverRole:'Hiring Manager'}), N('lvl2','approval',{approverRole:'Admin'}), N('end','end',{label:'Approved'})],
              edges: [E('1','trigger','r'), E('2','r','a5','yes'), E('3','r','lvl1','no'), E('4','lvl1','lvl2'), E('5','lvl2','end')] }
  assert.equal(g.mainOf(w.nodes, w.edges, 'r'), 'no')
  assert.equal(g.afterRule(w.nodes, w.edges, 'r').id, 'lvl1')
  assert.equal(g.joinOf(w.nodes, w.edges, 'r'), undefined)
  assert.equal(g.lanes(w.nodes, w.edges).get('lvl1'), 0)
  assert.deepEqual(g.describe(w.nodes, w.edges).map(x => x.id), ['trigger','r','lvl1','lvl2','end'])
  assert.equal(g.describe(w.nodes, w.edges)[1].text, 'If the request is flagged critical: Admin must approve. Otherwise it carries on.') })
t('a side holding a rule whose answers both stop is not the rest of the flow either', () => {
  // o: YES -> i (i: YES -> x1, NO -> x2) · NO -> lvl -> lvl2 -> end
  const w = { nodes: [N('trigger','trigger'), N('o','condition',{condition:'Virtual event'}), N('i','condition',{condition:'Flagged critical'}),
                      N('x1','exception'), N('x2','exception'), N('lvl','approval'), N('lvl2','approval'), N('end','end')],
              edges: [E('1','trigger','o'), E('2','o','i','yes'), E('3','i','x1','yes'), E('4','i','x2','no'), E('5','o','lvl','no'), E('6','lvl','lvl2'), E('7','lvl2','end')] }
  assert.equal(g.afterRule(w.nodes, w.edges, 'o').id, 'lvl')
  assert.deepEqual(g.continueChoices(w.nodes, w.edges.concat([E('0','pre','o')]), 'o', 'yes').map(x => x.id), ['lvl','lvl2','end'])
  assert.deepEqual(g.describe(w.nodes, w.edges).map(x => x.id), ['trigger','o','i','lvl','lvl2','end']) })
t('the only answer that reaches the outcome is kept when the rule goes', () => {
  // r1: YES -> a6 -> end · NO -> r11 (both answers stop at x9)
  const w = { nodes: [N('trigger','trigger'), N('r1','condition',{condition:'Virtual event'}), N('a6','approval'), N('r11','condition',{condition:'Flagged critical'}),
                      N('x9','exception'), N('end','end')],
              edges: [E('1','trigger','r1'), E('2','r1','a6','yes'), E('3','a6','end'), E('4','r1','r11','no'), E('5','r11','x9','yes'), E('6','r11','x9','no')] }
  assert.equal(g.afterRule(w.nodes, w.edges, 'r1').id, 'a6') })
t('when both answers hold a rule of their own, they still rejoin where both lead', () => {
  // o: YES -> i1, NO -> i2 · i1 and i2: both answers -> j · j -> end
  const w = { nodes: [N('trigger','trigger'), N('o','condition',{condition:'Virtual event'}), N('i1','condition',{condition:'Flagged critical'}),
                      N('i2','condition',{condition:'Short notice (under 14 days)'}), N('j','approval',{label:'Level 1',approverRole:'Admin'}), N('end','end',{label:'Approved'})],
              edges: [E('1','trigger','o'), E('2','o','i1','yes'), E('3','i1','j','yes'), E('4','i1','j','no'), E('5','o','i2','no'),
                      E('6','i2','j','yes'), E('7','i2','j','no'), E('8','j','end')] }
  assert.equal(g.joinOf(w.nodes, w.edges, 'o').id, 'j')
  assert.equal(g.afterRule(w.nodes, w.edges, 'o').id, 'j')
  assert.deepEqual(g.cutOff(w.nodes, w.edges, [E('n','trigger','j'), E('8','j','end')]).map(x => x.id).sort(), ['i1','i2','o'])
  assert.deepEqual(g.describe(w.nodes, w.edges).map(x => x.id), ['trigger','o','i1','i2','j','end']) })
t('the outcome is told last even when both answers hold rules that lead straight to it', () => {
  const w = { nodes: [N('trigger','trigger'), N('r1','condition',{condition:'Virtual event'}), N('r6','condition',{condition:'Flagged critical'}),
                      N('r11','condition',{condition:'Short notice (under 14 days)'}), N('end','end',{label:'Approved'})],
              edges: [E('1','trigger','r1'), E('2','r1','r6','yes'), E('3','r6','end','yes'), E('4','r6','end','no'), E('5','r1','r11','no'),
                      E('6','r11','end','yes'), E('7','r11','end','no')] }
  assert.deepEqual(g.describe(w.nodes, w.edges).map(x => x.id), ['trigger','r1','r6','r11','end']) })
t('a step of the main flow named inside a nested rule still gets its own sentence, and the outcome is told', () => {
  // r4: YES -> a1 · NO -> r8 (r8: YES -> end, NO -> a1) · a1 -> end
  const w = { nodes: [N('trigger','trigger'), N('r4','condition',{condition:'Flagged critical'}), N('r8','condition',{condition:'Virtual event'}),
                      N('a1','approval',{label:'Level 1',approverRole:'Hiring Manager'}), N('end','end',{label:'Approved'})],
              edges: [E('1','trigger','r4'), E('2','r4','a1','yes'), E('3','r4','r8','no'), E('4','r8','end','yes'), E('5','r8','a1','no'), E('6','a1','end')] }
  const told = g.describe(w.nodes, w.edges)
  assert.deepEqual(told.map(x => x.id), ['trigger','r4','r8','a1','end'])
  assert.equal(new Set(told.map(x => x.id)).size, told.length) })
t('every step on the way is told exactly once, whatever the shape', () => {
  for (const w of [ev, fresh, withStep, open, cj, guarded, nested]) {
    const ids = g.describe(w.nodes, w.edges).map(x => x.id)
    assert.equal(new Set(ids).size, ids.length)
    assert.equal(ids[0], 'trigger') } })

// where an answer can carry on to
t('an answer can carry on to any step on the other answer’s way, nearest first', () => {
  // r: YES -> a -> b -> end · NO not chosen yet
  const w = { nodes: [N('trigger','trigger'), N('r','condition',{condition:'Flagged critical'}), N('a','approval',{label:'A'}),
                      N('b','approval',{label:'B'}), N('end','end',{label:'Approved'})],
              edges: [E('1','trigger','r'), E('2','r','a','yes'), E('3','a','b'), E('4','b','end')] }
  assert.deepEqual(g.continueChoices(w.nodes, w.edges, 'r', 'no').map(x => x.id), ['a','b','end']) })
t('choices pass through a later rule and never offer an exception', () => {
  assert.deepEqual(g.continueChoices(guarded.nodes, guarded.edges, 'g', 'yes').map(x => x.id), ['lvl','r','end']) })
t('with nothing on the other answer, the outcome is the only place to carry on to', () => {
  assert.deepEqual(g.continueChoices(ev.nodes, ev.edges, 'rule', 'no').map(x => x.id), ['end']) })

// lanes
t('a rule inside a side never puts a step back in the main column', () => {
  // o: YES -> i (i: YES -> a, NO -> c, both -> j) · NO -> b1 -> j -> end
  const w = { nodes: [N('trigger','trigger'), N('o','condition',{condition:'Virtual event'}), N('i','condition',{condition:'Flagged critical'}),
                      N('a','approval'), N('c','approval'), N('b1','approval'), N('j','approval'), N('end','end')],
              edges: [E('1','trigger','o'), E('2','o','i','yes'), E('3','o','b1','no'), E('4','i','a','yes'), E('5','i','c','no'),
                      E('6','a','j'), E('7','c','j'), E('8','b1','j'), E('9','j','end')] }
  const l = g.lanes(w.nodes, w.edges), r = g.rows(w.nodes, w.edges)
  assert.deepEqual(['o','i','a','c','b1','j','end'].map(x => l.get(x)), [0,-2,-3,-1,1,0,0])
  const places = w.nodes.map(n => `${r.get(n.id)}:${l.get(n.id)}`)
  assert.equal(new Set(places).size, places.length) })
t('the answer that carries on keeps the column even before an outcome is drawn', () => {
  // g: YES stops · NO -> lvl, and nothing after it yet
  const w = { nodes: [N('trigger','trigger'), N('g','condition',{condition:'Flagged critical'}), N('x','exception'), N('lvl','approval')],
              edges: [E('1','trigger','g'), E('2','g','x','yes'), E('3','g','lvl','no')] }
  const l = g.lanes(w.nodes, w.edges); assert.equal(l.get('lvl'), 0); assert.equal(l.get('x'), -1) })
t('a guard’s exception clears whatever the main flow holds on its row', () => {
  // g: YES -> x · NO -> r (r: YES -> a -> end, NO -> end). x shares a row with r, not with a.
  const w = { nodes: [N('trigger','trigger'), N('g','condition',{condition:'Virtual event'}), N('x','exception'),
                      N('r','condition',{condition:'Flagged critical'}), N('a','approval'), N('end','end')],
              edges: [E('1','trigger','g'), E('2','g','x','yes'), E('3','g','r','no'), E('4','r','a','yes'), E('5','a','end'), E('6','r','end','no')] }
  const l = g.lanes(w.nodes, w.edges)
  assert.deepEqual(['g','x','r','a','end'].map(n => l.get(n)), [0,-1,0,-1,0]) })

// names
t('a step with a blank name still has one', () => {
  assert.equal(g.stepName(N('a','approval',{label:'  '})), 'Approval')
  assert.equal(g.stepName(N('x','exception',{label:''})), 'Exception')
  assert.equal(g.stepName(N('m','email',{emailTemplateName:''})), 'Email')
  assert.equal(g.stepName(N('r','condition',{condition:''})), 'Choose what to check') })

console.log(`\n${n} graph-model checks passed`)
