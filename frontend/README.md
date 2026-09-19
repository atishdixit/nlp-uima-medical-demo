# Chart UI (Angular)

Single-page UI for the medical chart NLP service: edit a chart, analyze it, and inspect the findings.
Angular 21, standalone components, signals, zoneless change detection, Vitest.

You normally do not run this folder by hand: `run.bat` (repository root) builds it into the Spring Boot app, so
<http://localhost:8080> serves the UI and the API together. See [SETUP.md](../SETUP.md).

## Developing the UI

Needs Node 20.19+ (or 22.12+).

```bat
run.bat dev        rem backend on :8080 in a new window + Angular dev server on http://localhost:4200
run.bat uitest     rem unit and component tests
```

or from this folder:

```bat
npm ci
npm start          rem ng serve; /api and /actuator are proxied to http://localhost:8080 (proxy.conf.json)
npm test           rem ng test (Vitest + jsdom)
npm run build      rem production build into dist/chart-ui/browser
```

## Layout

| File | Role |
|------|------|
| `src/app/analysis.store.ts` | All UI state as signals: chart text, latest analysis, staleness, errors, rules; auto-analyze; cancels superseded requests |
| `src/app/api.service.ts` | Typed HTTP client for `/api/v1/*`, sample loader, error to message mapping |
| `src/app/highlight.ts` | Pure functions that split the text at annotation boundaries so overlapping findings paint correctly |
| `src/app/saved-charts.service.ts` | Personal chart library in `localStorage` (never sent to the server) |
| `src/app/chart-editor/` | Left pane: editor, sample / saved / file loading, options, save |
| `src/app/results/` | Right pane: summary, annotated chart, tables, redacted text, JSON |
| `src/app/rules-panel/` | Header drop-down: rule counts, rejected rules, reload |
| `scripts/copy-samples.mjs` | Copies `../samples/*.txt` to `public/samples` before build/start (Angular cannot bundle files from outside its workspace) |

Design tokens (light and dark) are CSS variables in `src/styles.css`.
