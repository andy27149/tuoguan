const SUBJECT_ICON_PATHS: Record<string, string[]> = {
  数学: [
    '<rect x="2" y="2" width="12" height="12" rx="3.5" /><path d="M8 5.2v5.6M5.2 8h5.6" />',
    '<rect x="2" y="2" width="12" height="12" rx="3.5" /><path d="M5.5 5.5l5 5M10.5 5.5l-5 5" />',
    '<rect x="2" y="2" width="12" height="12" rx="3.5" /><path d="M5 8h6" /><circle cx="8" cy="5.3" r="0.7" fill="currentColor" stroke="none" /><circle cx="8" cy="10.7" r="0.7" fill="currentColor" stroke="none" />',
  ],
  语文: [
    '<path d="M2.2 4.2c1.7-1.1 3.8-1.1 5.3 0v8.4c-1.5-1.1-3.6-1.1-5.3 0z" /><path d="M13.3 4.2c-1.7-1.1-3.8-1.1-5.3 0v8.4c1.5-1.1 3.6-1.1 5.3 0z" />',
    '<rect x="2.5" y="2" width="11" height="12" rx="1.5" /><path d="M5 6h6M5 8.5h6M5 11h3.5" />',
    '<path d="M3.5 3.2c2-.1 4.4-.9 5.8-2M2.8 7.8c3-.1 6.5-1.2 8.9-3M2.8 12.6c2.4-.1 5.8-1.1 8.2-3" />',
  ],
  英语: [
    '<path d="M3 13 L7 3 L11 13 M4.6 9 h4.8" />',
    '<circle cx="8" cy="8" r="6" /><path d="M2 8h12M8 2c1.8 1.8 1.8 10.2 0 12M8 2c-1.8 1.8-1.8 10.2 0 12" />',
    '<path d="M2 3.5h12v7H8l-3 2.6v-2.6H2z" /><path d="M4.4 6h7.2M4.4 8.2h4.4" />',
  ],
  default: [
    '<path d="M3 2.5 h7 l3 3 v8 h-10 z" /><path d="M10 2.5 v3 h3" />',
    '<circle cx="8" cy="8" r="6" /><path d="M5.5 8.2 l1.8 1.8 3.2-4" />',
  ],
}

export function subjectIconMarkup(subject: string, seed: number): string {
  const variants = SUBJECT_ICON_PATHS[subject] ?? SUBJECT_ICON_PATHS.default
  const index = ((seed || 0) % variants.length + variants.length) % variants.length
  return `<svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round">${variants[index]}</svg>`
}

export function subjectColor(subject: string): string {
  if (subject === '数学') return 'var(--subj-math)'
  if (subject === '语文') return 'var(--subj-chinese)'
  if (subject === '英语') return 'var(--subj-english)'
  return 'var(--subj-default)'
}
