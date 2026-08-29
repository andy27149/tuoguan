export function resolveShareToken(pathname: string, basePath: string = '/'): string | null {
  const normalizedBase = basePath.endsWith('/') ? basePath.slice(0, -1) : basePath
  if (normalizedBase && !pathname.startsWith(normalizedBase)) {
    return null
  }
  const relativePath = normalizedBase ? pathname.slice(normalizedBase.length) || '/' : pathname
  const match = relativePath.match(/^\/share\/([^/]+)$/)
  return match ? match[1] : null
}
