/* How each kind of step looks: its colours and its line icon. */

/* ---------- node visual spec ---------- */

export const SPEC = {
  trigger: { kicker: 'Trigger', rail: '#012169', iconBg: '#eef2fb', iconFg: '#012169' },
  approval: { kicker: 'Approval', rail: '#3b6fd4', iconBg: '#eaf1fd', iconFg: '#2f5fc0' },
  condition: { kicker: 'Condition', rail: '#c98a00', iconBg: '#fdf5e3', iconFg: '#b07a00' },
  policy: { kicker: 'Policy', rail: '#0e8a80', iconBg: '#e6f5f3', iconFg: '#0b756d' },
  email: { kicker: 'Email', rail: '#7c3aed', iconBg: '#f3ecfd', iconFg: '#6d28d9' },
  sms: { kicker: 'SMS', rail: '#0e7490', iconBg: '#e6f3f7', iconFg: '#0b5f78' },
  step: { kicker: 'Step', rail: '#5b6b86', iconBg: '#eef1f6', iconFg: '#44536e' },
  exception: { kicker: 'Exception', rail: '#e31837', iconBg: '#fdecee', iconFg: '#c31432' },
  end: { kicker: 'Outcome', rail: '#1a9d55', iconBg: '#e9f7ef', iconFg: '#188a4b' },
} as const

export const Icons = {
  trigger: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
      <path d="M13 2 4.5 13.5H11L9.5 22 19 10h-6.5L13 2Z" />
    </svg>
  ),
  approval: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
      <circle cx="9" cy="8" r="3.5" />
      <path d="M3.5 20c.6-3.4 2.8-5 5.5-5s4.9 1.6 5.5 5" />
      <path d="m15 10 2 2 4-4.5" />
    </svg>
  ),
  condition: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 3v5" />
      <path d="M12 8c-4 0-6 2.5-6 6" />
      <path d="M12 8c4 0 6 2.5 6 6" />
      <path d="m4 12 2 2 2-2" />
      <path d="m16 12 2 2 2-2" />
      <circle cx="12" cy="5" r="0.5" />
    </svg>
  ),
  policy: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 2.5 5 5v6c0 4.6 3 8.4 7 9.5 4-1.1 7-4.9 7-9.5V5l-7-2.5Z" />
      <path d="m8.8 12 2.2 2.2 4.2-4.6" />
    </svg>
  ),
  email: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="5" width="18" height="14" rx="2" />
      <path d="m3.5 7 8.5 6 8.5-6" />
    </svg>
  ),
  sms: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
      <path d="M21 11.5a7.5 7.5 0 0 1-7.5 7.5H5l-1.8 2.2.3-4A7.5 7.5 0 1 1 21 11.5Z" />
      <path d="M8.5 10h7" />
      <path d="M8.5 13h4.5" />
    </svg>
  ),
  step: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
      <rect x="3.5" y="4" width="17" height="6.5" rx="1.8" />
      <rect x="3.5" y="14" width="17" height="6.5" rx="1.8" />
      <path d="M12 10.5V14" />
    </svg>
  ),
  exception: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 3 2.5 20h19L12 3Z" />
      <path d="M12 10v4" />
      <circle cx="12" cy="17" r="0.5" />
    </svg>
  ),
  end: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="8.5" />
      <path d="m8.5 12.2 2.4 2.4 4.6-5" />
    </svg>
  ),
  arrange: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
      <rect x="4" y="3" width="16" height="5" rx="1.5" />
      <rect x="4" y="10" width="7" height="5" rx="1.5" />
      <rect x="13" y="10" width="7" height="5" rx="1.5" />
      <rect x="8.5" y="17" width="7" height="5" rx="1.5" />
    </svg>
  ),
  plus: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2.4} strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 5v14" />
      <path d="M5 12h14" />
    </svg>
  ),
  undo: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
      <path d="M9 14 4 9l5-5" />
      <path d="M4 9h10.5a5.5 5.5 0 0 1 0 11H11" />
    </svg>
  ),
  chevron: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
      <path d="m9 6 6 6-6 6" />
    </svg>
  ),
  action: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
      <path d="M4 12 20 4l-5 16-3.5-6.5L4 12Z" />
    </svg>
  ),
  play: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
      <path d="M7 4.5 19 12 7 19.5V4.5Z" />
    </svg>
  ),
}
