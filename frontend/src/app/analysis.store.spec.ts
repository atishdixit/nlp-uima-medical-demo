import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AnalysisStore } from './analysis.store';
import { RULES, TEXT, sampleResponse } from './testing';

describe('AnalysisStore', () => {
  let store: AnalysisStore;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    store = TestBed.inject(AnalysisStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });

  it('sends the chart with redaction and sentences switched on, and keeps the response', () => {
    store.setText(TEXT);
    store.analyze();
    expect(store.loading()).toBe(true);

    const req = http.expectOne('/api/v1/charts/analyze');
    expect(req.request.body).toEqual({ text: TEXT, redactPhi: true, includeSentences: true });
    req.flush(sampleResponse());

    expect(store.loading()).toBe(false);
    expect(store.response()?.concepts).toHaveLength(2);
    expect(store.analyzedText()).toBe(TEXT);
    expect(store.backendUp()).toBe(true);
    expect(store.stale()).toBe(false);
  });

  it('marks the results stale as soon as the chart is edited', () => {
    store.setText(TEXT);
    store.analyze();
    http.expectOne('/api/v1/charts/analyze').flush(sampleResponse());
    store.setText(TEXT + ' Edited.');
    expect(store.stale()).toBe(true);
    expect(store.analyzedText()).toBe(TEXT); // highlights keep matching the analysed text
  });

  it('does not call the server for blank text or text over the limit', () => {
    store.setText('   \n ');
    store.analyze();
    store.setText('x'.repeat(store.maxChars + 1));
    expect(store.tooLarge()).toBe(true);
    expect(store.canAnalyze()).toBe(false);
    store.analyze();
    http.expectNone('/api/v1/charts/analyze');
  });

  it('shows the backend message for a rejected chart', () => {
    store.setText(TEXT);
    store.analyze();
    http.expectOne('/api/v1/charts/analyze').flush({ detail: 'The chart has 9 characters; the limit is 5' }, { status: 413, statusText: 'Payload Too Large' });
    expect(store.error()).toBe('413 The chart has 9 characters; the limit is 5');
    expect(store.loading()).toBe(false);
    expect(store.backendUp()).toBe(true);
  });

  it('reports an unreachable backend', () => {
    store.setText(TEXT);
    store.analyze();
    http.expectOne('/api/v1/charts/analyze').error(new ProgressEvent('error'), { status: 0 });
    expect(store.error()).toMatch(/cannot reach the backend/i);
    expect(store.backendUp()).toBe(false);
  });

  it('keeps the previous results when a new analysis fails, and clears the error on the next success', () => {
    store.setText(TEXT);
    store.analyze();
    http.expectOne('/api/v1/charts/analyze').flush(sampleResponse());
    store.analyze();
    http.expectOne('/api/v1/charts/analyze').flush({ detail: 'busy' }, { status: 503, statusText: 'Unavailable' });
    expect(store.response()).not.toBeNull();
    expect(store.error()).toContain('503');
    store.analyze();
    http.expectOne('/api/v1/charts/analyze').flush(sampleResponse());
    expect(store.error()).toBeNull();
  });

  it('cancels an older request when a newer one starts, so results cannot arrive out of order', () => {
    store.setText('first');
    store.analyze();
    store.setText('second');
    store.analyze();
    const [older, newer] = http.match('/api/v1/charts/analyze');
    expect(older.cancelled).toBe(true);
    newer.flush(sampleResponse({ textLength: 6 }));
    expect(store.analyzedText()).toBe('second');
  });

  describe('analyze as I type', () => {
    it('waits for a pause in typing and sends a single request', () => {
      vi.useFakeTimers();
      store.autoAnalyze.set(true);
      store.setText('fe');
      TestBed.tick();
      vi.advanceTimersByTime(300);
      store.setText('fever');
      TestBed.tick();
      vi.advanceTimersByTime(300);
      http.expectNone('/api/v1/charts/analyze');

      vi.advanceTimersByTime(500);
      const req = http.expectOne('/api/v1/charts/analyze');
      expect(req.request.body.text).toBe('fever');
      req.flush(sampleResponse());
    });

    it('does nothing while switched off, for blank text, or when the text is already analysed', () => {
      vi.useFakeTimers();
      store.setText('fever');
      TestBed.tick();
      vi.advanceTimersByTime(2000);
      http.expectNone('/api/v1/charts/analyze');

      store.analyze();
      http.expectOne('/api/v1/charts/analyze').flush(sampleResponse());
      store.autoAnalyze.set(true);
      TestBed.tick();
      vi.advanceTimersByTime(2000);
      http.expectNone('/api/v1/charts/analyze');

      store.setText('  ');
      TestBed.tick();
      vi.advanceTimersByTime(2000);
      http.expectNone('/api/v1/charts/analyze');
    });
  });

  describe('rules', () => {
    it('loads the rule summary', () => {
      store.loadRules();
      http.expectOne('/api/v1/rules/summary').flush(RULES);
      expect(store.rules()?.concepts).toBe(39);
      expect(store.backendUp()).toBe(true);
      expect(store.rulesError()).toBeNull();
    });

    it('marks the backend offline when the rules cannot be loaded', () => {
      store.loadRules();
      http.expectOne('/api/v1/rules/summary').error(new ProgressEvent('error'), { status: 0 });
      expect(store.backendUp()).toBe(false);
      expect(store.rulesError()).toBeTruthy();
    });

    it('re-analyzes the shown chart after a reload so a rule edit is visible immediately', () => {
      store.setText(TEXT);
      store.analyze();
      http.expectOne('/api/v1/charts/analyze').flush(sampleResponse());

      store.refreshRules();
      expect(store.rulesBusy()).toBe(true);
      http.expectOne('/api/v1/admin/rules/refresh').flush({ ...RULES, concepts: 40 });
      expect(store.rulesBusy()).toBe(false);
      expect(store.rules()?.concepts).toBe(40);
      http.expectOne('/api/v1/charts/analyze').flush(sampleResponse());
    });

    it('does not analyze after a reload when nothing is shown', () => {
      store.refreshRules();
      http.expectOne('/api/v1/admin/rules/refresh').flush(RULES);
      http.expectNone('/api/v1/charts/analyze');
    });

    it('surfaces a failed reload and keeps the old summary', () => {
      store.loadRules();
      http.expectOne('/api/v1/rules/summary').flush(RULES);
      store.refreshRules();
      http.expectOne('/api/v1/admin/rules/refresh').flush({ detail: 'Could not reload rules; still serving the previous rule set' }, { status: 503, statusText: 'Unavailable' });
      expect(store.rulesBusy()).toBe(false);
      expect(store.rulesError()).toContain('still serving the previous rule set');
      expect(store.rules()?.concepts).toBe(39);
    });
  });

  describe('samples and clearing', () => {
    it('loads a sample, normalizes Windows line endings and clears old results', () => {
      store.setText(TEXT);
      store.analyze();
      http.expectOne('/api/v1/charts/analyze').flush(sampleResponse());

      store.loadSample('soap-note.txt');
      http.expectOne('samples/soap-note.txt').flush('Line one\r\nLine two');
      expect(store.text()).toBe('Line one\nLine two');
      expect(store.response()).toBeNull();
    });

    it('reports a sample that cannot be loaded', () => {
      store.loadSample('missing.txt');
      http.expectOne('samples/missing.txt').flush('nope', { status: 404, statusText: 'Not Found' });
      expect(store.error()).toContain('Could not load the sample');
    });

    it('clearResults drops the response, selection and any in-flight request', () => {
      store.setText(TEXT);
      store.analyze();
      const req = http.expectOne('/api/v1/charts/analyze');
      store.select('c0');
      store.clearResults();
      expect(req.cancelled).toBe(true);
      expect(store.loading()).toBe(false);
      expect(store.selectedId()).toBeNull();
      expect(store.response()).toBeNull();
    });
  });
});
