import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useStore } from '@/state/store'
import ConfirmButton from '@/components/ConfirmButton'
import SlideOver from '@/components/SlideOver'
import { MODULES, PERMISSIONS, useConfig } from '@/state/config'
import SurveyBuilder from './admin/SurveyBuilder'
import EmailBuilder from './admin/EmailBuilder'

type Tab = 'users' | 'survey' | 'email'

const TABS: { key: Tab; label: string; blurb: string }[] = [
  { key: 'users', label: 'Users & Access', blurb: 'People, roles, permissions, modules' },
  { key: 'survey', label: 'Survey Builder', blurb: 'Design candidate-experience surveys' },
  { key: 'email', label: 'Email Builder', blurb: 'Templates for nurture & offers' },
]

function StatTile({ label, value, meta }: { label: string; value: string | number; meta?: string }) {
  return (
    <div className="stat">
      <div className="stat__label">{label}</div>
      <div className="stat__value">{value}</div>
      {meta ? <div className="stat__meta">{meta}</div> : null}
    </div>
  )
}

function InviteUser({ onClose }: { onClose: () => void }) {
  const { roles, addUser } = useConfig()
  const { toastMsg } = useStore()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [roleKey, setRoleKey] = useState(roles.find((r) => r.key === 'recruiter')?.key ?? roles[0]?.key ?? '')
  const valid = email.trim().includes('@') && roleKey

  return (
    <SlideOver label="Invite teammate" onClose={onClose} width={420}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '18px 20px 14px', borderBottom: '1px solid var(--line)' }}>
        <div style={{ fontFamily: 'var(--font-display)', fontSize: 19, color: 'var(--ink-0)' }}>Invite teammate</div>
        <button type="button" onClick={onClose} aria-label="Close" style={{ border: 0, background: 'none', cursor: 'pointer', fontSize: 20, color: 'var(--ink-4)' }}>×</button>
      </div>
      <div style={{ flex: 1, overflowY: 'auto', padding: '18px 20px', display: 'flex', flexDirection: 'column', gap: 14 }}>
        <label style={{ display: 'block' }}>
          <div className="eyebrow" style={{ marginBottom: 6 }}>Full name</div>
          <input className="input" value={name} onChange={(e) => setName(e.target.value)} placeholder="Jordan Rivera" />
        </label>
        <label style={{ display: 'block' }}>
          <div className="eyebrow" style={{ marginBottom: 6 }}>Work email</div>
          <input className="input" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="name@bofa.com" />
        </label>
        <label style={{ display: 'block' }}>
          <div className="eyebrow" style={{ marginBottom: 6 }}>Role</div>
          <select className="select" value={roleKey} onChange={(e) => setRoleKey(e.target.value)}>
            {roles.map((r) => <option key={r.key} value={r.key}>{r.name}</option>)}
          </select>
        </label>
        <div style={{ fontSize: 11.5, color: 'var(--ink-5)' }}>
          {roles.find((r) => r.key === roleKey)?.description}
        </div>
      </div>
      <div style={{ display: 'flex', gap: 8, padding: '14px 20px', borderTop: '1px solid var(--line)' }}>
        <button className="btn btn--ghost btn--sm" onClick={onClose}>Cancel</button>
        <div style={{ flex: 1 }} />
        <button className="btn btn--primary btn--sm" disabled={!valid} onClick={() => { addUser(name, email, roleKey); toastMsg(`Invite sent to ${email.trim()}`); onClose() }}>
          Send invite
        </button>
      </div>
    </SlideOver>
  )
}

function UsersAndAccess() {
  const { users, roles, currentUser, setUserRole, removeUser, setCurrentUser, setRolePermission, toggleModule, isModuleOn } = useConfig()
  const [inviting, setInviting] = useState(false)

  const kpis = useMemo(() => {
    const admins = users.filter((u) => u.roleKey === 'admin').length
    const activeModules = MODULES.filter((m) => isModuleOn(m.key)).length
    return { users: users.length, roles: roles.length, admins, activeModules }
  }, [users, roles, isModuleOn])

  const groups: { group: string; keys: string[] }[] = []
  for (const m of MODULES) {
    const g = groups.find((x) => x.group === m.group)
    if (g) g.keys.push(m.key)
    else groups.push({ group: m.group, keys: [m.key] })
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <div className="grid-stats" style={{ gridTemplateColumns: 'repeat(4,1fr)' }}>
        <StatTile label="Team members" value={kpis.users} />
        <StatTile label="Roles" value={kpis.roles} />
        <StatTile label="Admins" value={kpis.admins} />
        <StatTile label="Active modules" value={`${kpis.activeModules}/${MODULES.length}`} />
      </div>

      {/* Team */}
      <div className="card">
        <div className="card__head">
          <h3 style={{ fontSize: 15 }}>Team</h3>
          <button className="btn btn--primary btn--sm" onClick={() => setInviting(true)}>Invite</button>
        </div>
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Member</th>
                <th>Role</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {users.map((u) => (
                <tr key={u.id}>
                  <td>
                    <div className="who">
                      <div className="avatar">{u.initials}</div>
                      <div>
                        <div className="who__name">
                          {u.name}
                          {currentUser?.id === u.id ? <span className="badge badge--info" style={{ marginLeft: 8 }}>You</span> : null}
                        </div>
                        <div className="who__sub">{u.email}</div>
                      </div>
                    </div>
                  </td>
                  <td>
                    <select className="select" style={{ width: 200 }} value={u.roleKey} onChange={(e) => setUserRole(u.id, e.target.value)}>
                      {roles.map((r) => <option key={r.key} value={r.key}>{r.name}</option>)}
                    </select>
                  </td>
                  <td className="t-right">
                    <div style={{ display: 'inline-flex', gap: 6 }}>
                      {currentUser?.id !== u.id && (
                        <button className="btn btn--outline btn--sm" onClick={() => setCurrentUser(u.id)} title="View the app as this user">Act as</button>
                      )}
                      <ConfirmButton label="Remove" confirmLabel="Remove user?" onConfirm={() => removeUser(u.id)} />
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Roles & permissions matrix */}
      <div className="card">
        <div className="card__head">
          <h3 style={{ fontSize: 15 }}>Roles &amp; permissions</h3>
          <span className="eyebrow">What each role can do</span>
        </div>
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Permission</th>
                {roles.map((r) => (
                  <th key={r.key} className="t-center" style={{ textAlign: 'center' }}>{r.name}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {PERMISSIONS.map((perm) => (
                <tr key={perm.key}>
                  <td className="t-strong">{perm.label}</td>
                  {roles.map((r) => {
                    const locked = r.key === 'admin'
                    return (
                      <td key={r.key} style={{ textAlign: 'center' }}>
                        <input
                          type="checkbox"
                          checked={!!r.permissions[perm.key]}
                          disabled={locked}
                          onChange={(e) => setRolePermission(r.key, perm.key, e.target.checked)}
                          aria-label={`${r.name} — ${perm.label}`}
                        />
                      </td>
                    )
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Module access */}
      <div className="card">
        <div className="card__head">
          <h3 style={{ fontSize: 15 }}>Module access</h3>
          <span className="eyebrow">Turn console sections on or off</span>
        </div>
        <div className="card__body" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {groups.map((g) => (
            <div key={g.group}>
              <div className="eyebrow" style={{ marginBottom: 8 }}>{g.group}</div>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))', gap: 8 }}>
                {g.keys.map((k) => {
                  const mod = MODULES.find((m) => m.key === k)!
                  const on = isModuleOn(k)
                  return (
                    <label key={k} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 10, padding: '9px 12px', border: '1px solid var(--line)', borderRadius: 'var(--ra-2)', fontSize: 13, cursor: mod.locked ? 'default' : 'pointer', opacity: mod.locked ? 0.6 : 1 }}>
                      <span>{mod.label}{mod.locked ? <span className="muted" style={{ marginLeft: 6 }}>· always on</span> : ''}</span>
                      <input type="checkbox" checked={on} disabled={mod.locked} onChange={() => toggleModule(k)} />
                    </label>
                  )
                })}
              </div>
            </div>
          ))}
        </div>
      </div>

      {inviting && <InviteUser onClose={() => setInviting(false)} />}
    </div>
  )
}

export default function AdminPage() {
  const [params, setParams] = useSearchParams()
  const rawTab = params.get('tab')
  const tab: Tab = rawTab === 'survey' || rawTab === 'email' ? rawTab : 'users'
  const setTab = (t: Tab) => setParams(t === 'users' ? {} : { tab: t }, { replace: true })

  return (
    <div>
      <div className="page-head" style={{ marginBottom: 18 }}>
        <div className="crumb">
          <span className="dot" />
          Platform · Admin
        </div>
        <h1 style={{ fontSize: 28 }}>Admin</h1>
        <p className="sub">Manage who has access and what they can do — plus the survey and email builders.</p>
      </div>

      <div className="tabs" style={{ marginBottom: 20 }}>
        {TABS.map((t) => (
          <button key={t.key} className="tab" aria-selected={tab === t.key} onClick={() => setTab(t.key)} title={t.blurb}>
            {t.label}
          </button>
        ))}
      </div>

      <div>
        {tab === 'users' && <UsersAndAccess />}
        {tab === 'survey' && <SurveyBuilder />}
        {tab === 'email' && <EmailBuilder />}
      </div>
    </div>
  )
}
