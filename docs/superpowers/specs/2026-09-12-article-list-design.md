# Article List — Feature Design

**Date:** 2026-09-12
**Status:** In progress
**Builds on:** `docs/superpowers/specs/2026-09-12-architecture-design.md` (package layout, layering, DI, tech stack, and shared API/auth decisions all apply here and aren't repeated below)

## Overview

The Article List screen fetches and displays a paginated list of technology news articles from NewsAPI.org. It's the first of three MVP features (Article List → Source List → AI Summarization).

## Package Layout

Per the architecture doc's feature-first convention:

```
com.dailyaipulse.articles/
├── ui/            # ArticleListScreen
├── presentation/  # ArticleListViewModel, ArticleListUiState, Article (presentation model)
└── data/          # ArticleData, ArticleApiService, ArticleRepository, ArticleModule (Hilt)
```

Package directories already scaffolded (empty, pending implementation).

## API Contract

**Provider:** NewsAPI.org (auth handling is cross-cutting — see architecture doc's External APIs section).

- **Endpoint:** `GET /v2/top-headlines`
- **Query params:**
  - `country=us`
  - `category=technology`
  - `pageSize=20`
  - `page={page}` — for pagination/infinite scroll
- **Response shape** (per NewsAPI docs):

```json
{
  "status": "ok",
  "totalResults": 42,
  "articles": [
    {
      "source": { "id": "string|null", "name": "string" },
      "author": "string|null",
      "title": "string",
      "description": "string|null",
      "url": "string",
      "urlToImage": "string|null",
      "publishedAt": "string (ISO 8601)",
      "content": "string|null"
    }
  ]
}
```

This shape maps directly to `ArticleData` (and a nested `ArticleSourceData` for the `source` object) in `articles/data/`.

## Open Items

1. **Key attachment mechanism** — inherited from the architecture doc's cross-cutting open decision (query param vs. shared OkHttp interceptor); not yet resolved.
2. **`ArticleData → Article` mapping location** — Repository vs. ViewModel; not yet resolved (architecture doc frames this as a per-feature pattern question).
3. **Presentation model (`Article`) field set** — not yet defined; depends on what the Article List UI actually needs to render (likely a subset/reshaping of `ArticleData`, e.g. formatted date, no need to carry `content` if unused).
4. **UI states** (`ArticleListUiState`) — loading / content / error / empty-pagination-end — not yet designed.
5. **Error handling** — network failure, empty results, pagination failure mid-scroll — not yet designed.
6. **Testing approach** — deferred per architecture doc (testing libraries not yet chosen).
