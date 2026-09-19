import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AnalyzeRequest, AnalyzeResponse, RuleSummary, SampleChart } from './models';

/** Sample charts are served from /samples (the repository's samples/ folder is bundled into the build). */
export const SAMPLES: SampleChart[] = [
  { file: 'soap-note.txt', label: 'SOAP note (every feature)' },
  { file: 'negation-cases.txt', label: 'Negation cases' },
  { file: 'phi-heavy.txt', label: 'PHI heavy' },
  { file: 'unsectioned-note.txt', label: 'No headers, messy spacing' },
];

@Injectable({ providedIn: 'root' })
export class ChartApi {
  private readonly http = inject(HttpClient);

  analyze(request: AnalyzeRequest): Observable<AnalyzeResponse> {
    return this.http.post<AnalyzeResponse>('/api/v1/charts/analyze', request);
  }

  rules(): Observable<RuleSummary> {
    return this.http.get<RuleSummary>('/api/v1/rules/summary');
  }

  refreshRules(): Observable<RuleSummary> {
    return this.http.post<RuleSummary>('/api/v1/admin/rules/refresh', null);
  }

  sample(file: string): Observable<string> {
    return this.http.get(`samples/${file}`, { responseType: 'text' });
  }
}

/** Turns an HTTP failure into one readable sentence (the backend answers RFC 7807 problem+json). */
export function describeError(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) {
      return 'Cannot reach the backend. Is the Spring Boot application running?';
    }
    const body: unknown = err.error;
    const detail =
      typeof body === 'object' && body !== null && 'detail' in body
        ? String((body as { detail: unknown }).detail)
        : '';
    return `${err.status} ${detail || err.statusText || 'Request failed'}`.trim();
  }
  return err instanceof Error ? err.message : String(err);
}

/** True when the failure means the backend could not be reached at all. */
export function isUnreachable(err: unknown): boolean {
  return err instanceof HttpErrorResponse && err.status === 0;
}
