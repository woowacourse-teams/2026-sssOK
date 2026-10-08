export const MAX_SEARCH_QUERY_LENGTH = 200;

export const normalizeSearchQuery = (query: string) => query.trim().replace(/\s+/g, " ");
