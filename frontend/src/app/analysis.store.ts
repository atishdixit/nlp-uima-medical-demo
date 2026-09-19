import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { Subscription } from 'rxjs';
import { ChartApi, describeError, isUnreachable } from './api.service';
import { AnalyzeResponse, RuleSummary } from './models';

const AUTO_ANALYZE_DELAY_MS = 700;

/** Single source of UI state: the chart being edited, the latest analysis, and the rule summary. */
@Injectable({ providedIn: 'root' })
export class AnalysisStore {
  private readonly api = inject(ChartApi);
  private inFlight?: Subscription;

  /** Server-side limit (mednlp.max-chars); the server remains the authority. */
  readonly maxChars = 1_000_000;

  readonly text = signal('');
  readonly autoAnalyze = signal(false);

  readonly response = signal<AnalyzeResponse | null>(null);
  /** The exact text the current response belongs to; highlights must use this, not the live editor text. */
  readonly analyzedText = signal('');
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly selectedId = signal<string | null>(null);

  readonly rules = signal<RuleSummary | null>(null);
  readonly rulesBusy = signal(false);
  readonly rulesError = signal<string | null>(null);
  /** null = not checked yet */
  readonly backendUp = signal<boolean | null>(null);

  readonly tooLarge = computed(() => this.text().length > this.maxChars);
  readonly canAnalyze = computed(() => this.text().trim().length > 0 && !this.tooLarge());
  /** The editor has changed since the results on screen were produced. */
  readonly stale = computed(() => this.response() !== null && this.text() !== this.analyzedText());

  constructor() {
    effect((onCleanup) => {
      if (!this.autoAnalyze()) {
        return;
      }
      const text = this.text();
      const worthRunning = text.trim().length > 0 && text.length <= this.maxChars && text !== untracked(this.analyzedText);
      if (!worthRunning) {
        return;
      }
      const handle = setTimeout(() => this.analyze(), AUTO_ANALYZE_DELAY_MS);
      onCleanup(() => clearTimeout(handle));
    });
  }

  setText(text: string): void {
    this.text.set(text);
  }

  analyze(): void {
    if (!this.canAnalyze()) {
      return;
    }
    const text = this.text();
    this.inFlight?.unsubscribe(); // a newer request supersedes the old one, so responses can never arrive out of order
    this.loading.set(true);
    this.error.set(null);
    this.inFlight = this.api.analyze({ text, redactPhi: true, includeSentences: true }).subscribe({
      next: (response) => {
        this.response.set(response);
        this.analyzedText.set(text);
        this.selectedId.set(null);
        this.loading.set(false);
        this.backendUp.set(true);
      },
      error: (err: unknown) => {
        this.error.set(describeError(err));
        this.loading.set(false);
        this.backendUp.set(!isUnreachable(err));
      },
    });
  }

  clearResults(): void {
    this.inFlight?.unsubscribe();
    this.response.set(null);
    this.analyzedText.set('');
    this.selectedId.set(null);
    this.error.set(null);
    this.loading.set(false);
  }

  select(id: string | null): void {
    this.selectedId.set(id);
  }

  loadRules(): void {
    this.api.rules().subscribe({
      next: (rules) => {
        this.rules.set(rules);
        this.rulesError.set(null);
        this.backendUp.set(true);
      },
      error: (err: unknown) => {
        this.rulesError.set(describeError(err));
        this.backendUp.set(!isUnreachable(err));
      },
    });
  }

  /** Re-reads the rule tables from MySQL, then re-runs the analysis so the effect of a rule edit is visible at once. */
  refreshRules(): void {
    this.rulesBusy.set(true);
    this.api.refreshRules().subscribe({
      next: (rules) => {
        this.rules.set(rules);
        this.rulesError.set(null);
        this.rulesBusy.set(false);
        this.backendUp.set(true);
        if (this.response() !== null) {
          this.analyze();
        }
      },
      error: (err: unknown) => {
        this.rulesError.set(describeError(err));
        this.rulesBusy.set(false);
        this.backendUp.set(!isUnreachable(err));
      },
    });
  }

  loadSample(file: string): void {
    this.api.sample(file).subscribe({
      next: (content) => {
        this.text.set(content.replace(/\r\n/g, '\n'));
        this.clearResults();
      },
      error: (err: unknown) => this.error.set(`Could not load the sample: ${describeError(err)}`),
    });
  }
}
