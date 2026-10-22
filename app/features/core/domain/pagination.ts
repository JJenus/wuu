export type SortDirection = 'ASC' | 'DESC'

export interface PageRequest {
  page: number
  size: number
  sortBy?: string
  sortDirection?: SortDirection
}

export interface Page<T> {
  items: readonly T[]
  page: number
  size: number
  total: number
  pages: number
  first: boolean
  last: boolean
}

export function emptyPage<T>(size = 20): Page<T> {
  return { items: [], page: 0, size, total: 0, pages: 0, first: true, last: true }
}

export function pageQuery(input: PageRequest): Record<string, string | number> {
  const query: Record<string, string | number> = {
    page: Math.max(0, Math.trunc(input.page)),
    size: Math.max(1, Math.trunc(input.size)),
  }
  if (input.sortBy) query.sortBy = input.sortBy
  if (input.sortDirection) query.sortDirection = input.sortDirection
  return query
}
