import { useEffect, useState, type ComponentType, type ReactNode } from 'react'
import { NavLink, useLocation, useNavigate } from 'react-router-dom'
import CommandPalette from '@/components/CommandPalette'
import { useStore } from '@/state/store'
import { MODULES, useConfig } from '@/state/config'
import {
  IconAdmin,
  IconAnalytics,
  IconArrowRight,
  IconAssessments,
  IconAudit,
  IconBell,
  IconCampaigns,
  IconCandidates,
  IconCheck,
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
  admin: IconAdmin,
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
  admin: '/admin',
}

/* Short display names for the top-band group pills. */
const GROUP_PILL_LABELS: Record<string, string> = {
  'Recruiting': 'Recruiting',
  'Interview': 'Interviews',
  'Offer & Hire': 'Hire',
  'Talent Intelligence': 'Talent',
  'Engagement': 'Engagement',
  'Platform': 'Platform',
}

/* The contextual rail: only the ACTIVE group's modules, labeled. Short by
   design — no scrolling, no collapse. */
function RailNav({ group, keys }: { group: string; keys: string[] }) {
  return (
    <nav className="app__side">
      <div className="nav-group">{group}</div>
      {keys.map((k) => (
        <Item key={k} to={ROUTES[k]} Icon={ICONS[k]} label={MODULES.find((m) => m.key === k)!.label} />
      ))}
    </nav>
  )
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
 * Wordmark + placeholder logo mark. Drop files into /public:
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
        <span style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', gap: 4 }}>
          <img src={LOGO_SRCS[logoIdx]} alt="Bank of America" className="brand-logo" onError={() => setLogoIdx((i) => i + 1)} />
          <small className="brand-sub">Careers · Recruiting</small>
        </span>
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
  const [paletteOpen, setPaletteOpen] = useState(false)
  const location = useLocation()
  const navigate = useNavigate()

  // Cmd/Ctrl+K opens the command palette from anywhere.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        setPaletteOpen((v) => !v)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  // group enabled modules, preserving MODULES order and dropping empty groups
  const groups: { group: string; keys: string[] }[] = []
  for (const m of MODULES) {
    if (!isModuleOn(m.key)) continue
    const g = groups.find((x) => x.group === m.group)
    if (g) g.keys.push(m.key)
    else groups.push({ group: m.group, keys: [m.key] })
  }

  // Which module (and so which group) is the current route in?
  const activeKey =
    Object.entries(ROUTES)
      .filter(([, r]) => (r === '/' ? location.pathname === '/' : location.pathname.startsWith(r)))
      .sort((a, b) => b[1].length - a[1].length)[0]?.[0] ?? 'overview'
  const activeGroupName = MODULES.find((m) => m.key === activeKey)?.group ?? 'Recruiting'
  const pillGroups = groups.filter((g) => g.group !== 'Admin')
  const activeGroup = groups.find((g) => g.group === activeGroupName) ?? pillGroups[0]

  return (
    <div className="app">
      {/* Quixotic-style band: logo · group pills (primary nav) · actions. */}
      <div className="app__top">
        <div className="app__topbrand">
          <Brand collapsed={false} onToggle={() => navigate('/')} />
        </div>
        <nav className="topnav" aria-label="Sections">
          {pillGroups.map((g) => (
            <button
              key={g.group}
              className={g.group === activeGroup.group ? 'topnav__pill topnav__pill--active' : 'topnav__pill'}
              onClick={() => navigate(ROUTES[g.keys[0]])}
            >
              {GROUP_PILL_LABELS[g.group] ?? g.group}
            </button>
          ))}
        </nav>
        <div className="spacer" />
        <button className="btn btn--ghost btn--icon" onClick={() => setPaletteOpen(true)} aria-label="Search (⌘K)" title="Search (⌘K)">
          <IconSearch className="ic" />
        </button>
        <a href="http://localhost:5173" target="_blank" rel="noreferrer" className="btn btn--ghost" title="Open the candidate-facing career site">
          Career site
          <IconArrowRight className="ic" />
        </a>
        <button className="btn btn--ghost btn--icon" aria-label="Notifications">
          <IconBell className="ic" />
        </button>
        <NavLink to="/admin" className="btn btn--ghost btn--icon" aria-label="Admin" title="Admin">
          <IconAdmin className="ic" />
        </NavLink>
        <div className="avatar" title={currentUser?.name ?? 'Account'}>
          {currentUser?.initials ?? 'OA'}
        </div>
      </div>

      {/* Contextual rail: just the active group's modules. */}
      <div className="app__rail">
        <RailNav group={activeGroup.group} keys={activeGroup.keys} />
      </div>

      <main className="app__main">{children}</main>

      <CommandPalette open={paletteOpen} onClose={() => setPaletteOpen(false)} />

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
