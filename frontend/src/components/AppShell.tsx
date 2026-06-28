import { useState, type ComponentType, type ReactNode } from 'react'
import { NavLink } from 'react-router-dom'
import { useStore } from '@/state/store'
import { MODULES, useConfig } from '@/state/config'
import {
  IconAnalytics,
  IconArrowRight,
  IconAssessments,
  IconAudit,
  IconBell,
  IconCampaigns,
  IconCandidates,
  IconCheck,
  IconChevronLeft,
  IconChevronRight,
  IconCompliance,
  IconCopilot,
  IconCrm,
  IconEvents,
  IconIntegrations,
  IconInterviews,
  IconJobs,
  IconMatching,
  IconMobility,
  IconOffers,
  IconOnboard,
  IconOverview,
  IconPipeline,
  IconReferrals,
  IconSearch,
  IconSourcing,
  IconSurveys,
} from './icons'

type IconC = ComponentType<{ className?: string }>

const ICONS: Record<string, IconC> = {
  overview: IconOverview,
  jobs: IconJobs,
  pipeline: IconPipeline,
  candidates: IconCandidates,
  interviews: IconInterviews,
  assessments: IconAssessments,
  offers: IconOffers,
  onboarding: IconOnboard,
  matching: IconMatching,
  sourcing: IconSourcing,
  mobility: IconMobility,
  crm: IconCrm,
  campaigns: IconCampaigns,
  referrals: IconReferrals,
  events: IconEvents,
  surveys: IconSurveys,
  copilot: IconCopilot,
  analytics: IconAnalytics,
  compliance: IconCompliance,
  integrations: IconIntegrations,
  audit: IconAudit,
}

const ROUTES: Record<string, string> = {
  overview: '/',
  jobs: '/jobs',
  pipeline: '/pipeline',
  candidates: '/candidates',
  interviews: '/interviews',
  assessments: '/assessments',
  offers: '/offers',
  onboarding: '/onboarding',
  matching: '/matching',
  sourcing: '/sourcing',
  mobility: '/mobility',
  crm: '/crm',
  campaigns: '/campaigns',
  referrals: '/referrals',
  events: '/events',
  surveys: '/surveys',
  copilot: '/copilot',
  analytics: '/analytics',
  compliance: '/compliance',
  integrations: '/integrations',
  audit: '/audit',
}

const STORAGE_KEY = 'olivia.sidebarCollapsed'
const readCollapsed = () => {
  try {
    return localStorage.getItem(STORAGE_KEY) === '1'
  } catch {
    return false
  }
}

function Item({ to, Icon, label }: { to: string; Icon: IconC; label: string }) {
  return (
    <NavLink to={to} end={to === '/'} className="nav-item" title={label}>
      <Icon className="ic" />
      <span className="nav-label">{label}</span>
    </NavLink>
  )
}

/**
 * Brand cell. Renders a client logo image when present, falling back to the
 * "Olivia" wordmark + placeholder "O" mark. Drop files into /public:
 *   - public/brand-logo.svg → full logo, shown when expanded
 *   - public/brand-mark.svg → symbol only, shown on the collapsed rail
 */
const LOGO_SRCS = ['/brand-logo.svg', '/brand-logo.png']
const MARK_SRCS = ['/brand-mark.svg', '/brand-mark.png']

function Brand({ collapsed, onToggle }: { collapsed: boolean; onToggle: () => void }) {
  const [logoIdx, setLogoIdx] = useState(0)
  const [markIdx, setMarkIdx] = useState(0)
  const logoFailed = logoIdx >= LOGO_SRCS.length
  const markFailed = markIdx >= MARK_SRCS.length

  if (collapsed) {
    return (
      <button className="brand-toggle" onClick={onToggle} title="Toggle sidebar" aria-label="Toggle sidebar">
        {!markFailed ? (
          <img src={MARK_SRCS[markIdx]} alt="" className="brand-mark-img" onError={() => setMarkIdx((i) => i + 1)} />
        ) : (
          <div className="mark">B</div>
        )}
      </button>
    )
  }

  return (
    <button className="brand-toggle" onClick={onToggle} title="Toggle sidebar" aria-label="Toggle sidebar">
      {!logoFailed ? (
        <img src={LOGO_SRCS[logoIdx]} alt="Bank of America" className="brand-logo" onError={() => setLogoIdx((i) => i + 1)} />
      ) : (
        <>
          <div className="mark">B</div>
          <div className="wordmark">
            Bank of America<small>Careers · Recruiting</small>
          </div>
        </>
      )}
    </button>
  )
}

export function AppShell({ children }: { children: ReactNode }) {
  const { toast } = useStore()
  const { isModuleOn, currentUser } = useConfig()
  const [collapsed, setCollapsed] = useState(readCollapsed)

  const toggle = () =>
    setCollapsed((c) => {
      const next = !c
      try {
        localStorage.setItem(STORAGE_KEY, next ? '1' : '0')
      } catch {
        /* ignore */
      }
      return next
    })

  // group enabled modules, preserving MODULES order and dropping empty groups
  const groups: { group: string; keys: string[] }[] = []
  for (const m of MODULES) {
    if (!isModuleOn(m.key)) continue
    const g = groups.find((x) => x.group === m.group)
    if (g) g.keys.push(m.key)
    else groups.push({ group: m.group, keys: [m.key] })
  }

  return (
    <div className={collapsed ? 'app app--collapsed' : 'app'}>
      <div className="app__brand">
        <Brand collapsed={collapsed} onToggle={toggle} />
      </div>

      <div className="app__top">
        <button
          className="btn btn--ghost btn--icon"
          onClick={toggle}
          aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          title="Toggle sidebar"
        >
          {collapsed ? <IconChevronRight className="ic" /> : <IconChevronLeft className="ic" />}
        </button>
        <div className="spacer" />
        <div className="input-group" style={{ width: 320, maxWidth: '32vw' }}>
          <IconSearch className="ic-lead" />
          <input className="input" placeholder="Search jobs, candidates, pipelines…" />
        </div>
        <a href="http://localhost:5173" target="_blank" rel="noreferrer" className="btn btn--ghost" title="Open the candidate-facing career site">
          Career site
          <IconArrowRight className="ic" />
        </a>
        <button className="btn btn--ghost btn--icon" aria-label="Notifications">
          <IconBell className="ic" />
        </button>
        <div className="avatar" title={currentUser?.name ?? 'Account'}>
          {currentUser?.initials ?? 'OA'}
        </div>
      </div>

      <nav className="app__side">
        {groups.map((g) => (
          <div key={g.group}>
            <div className="nav-group">{g.group}</div>
            {g.keys.map((k) => (
              <Item key={k} to={ROUTES[k]} Icon={ICONS[k]} label={MODULES.find((m) => m.key === k)!.label} />
            ))}
          </div>
        ))}
      </nav>

      <main className="app__main">{children}</main>

      {toast && (
        <div style={{ position: 'fixed', right: 24, bottom: 24, zIndex: 60 }}>
          <div className="toast toast--ok">
            <IconCheck className="toast__ic" style={{ color: 'var(--c-green)' }} />
            <div>
              <div className="toast__title">{toast}</div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
