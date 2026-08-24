export function resolveShareToken(pathname: string): string | null {
  const match = pathname.match(/^\/share\/([^/]+)$/)
  return match ? match[1] : null
}
