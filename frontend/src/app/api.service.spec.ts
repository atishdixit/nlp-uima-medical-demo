import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ChartApi, SAMPLES, describeError, isUnreachable } from './api.service';
import { RULES, sampleResponse } from './testing';

describe('ChartApi', () => {
  let api: ChartApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(ChartApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('posts the chart to the analyze endpoint', () => {
    let result: unknown;
    api.analyze({ text: 'fever', redactPhi: true, includeSentences: true }).subscribe((r) => (result = r));
    const req = http.expectOne('/api/v1/charts/analyze');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ text: 'fever', redactPhi: true, includeSentences: true });
    req.flush(sampleResponse());
    expect(result).toEqual(sampleResponse());
  });

  it('reads the rule summary with GET', () => {
    api.rules().subscribe();
    const req = http.expectOne('/api/v1/rules/summary');
    expect(req.request.method).toBe('GET');
    req.flush(RULES);
  });

  it('refreshes rules with POST', () => {
    api.refreshRules().subscribe();
    const req = http.expectOne('/api/v1/admin/rules/refresh');
    expect(req.request.method).toBe('POST');
    req.flush(RULES);
  });

  it('fetches sample charts as text', () => {
    let text = '';
    api.sample('soap-note.txt').subscribe((t) => (text = t));
    const req = http.expectOne('samples/soap-note.txt');
    expect(req.request.responseType).toBe('text');
    req.flush('Chief Complaint: cough');
    expect(text).toBe('Chief Complaint: cough');
  });

  it('offers the four bundled samples', () => {
    expect(SAMPLES.map((s) => s.file)).toEqual(['soap-note.txt', 'negation-cases.txt', 'phi-heavy.txt', 'unsectioned-note.txt']);
  });
});

describe('describeError', () => {
  it('uses the problem+json detail from the backend', () => {
    const err = new HttpErrorResponse({ status: 400, error: { detail: 'The chart text must not be empty' } });
    expect(describeError(err)).toBe('400 The chart text must not be empty');
  });

  it('reports the size-limit message of a 413', () => {
    const err = new HttpErrorResponse({ status: 413, error: { detail: 'The chart has 1001 characters; the limit is 1000' } });
    expect(describeError(err)).toContain('the limit is 1000');
  });

  it('explains an unreachable backend', () => {
    const err = new HttpErrorResponse({ status: 0 });
    expect(describeError(err)).toMatch(/cannot reach the backend/i);
    expect(isUnreachable(err)).toBe(true);
  });

  it('falls back to the status text when the body is not problem+json', () => {
    const err = new HttpErrorResponse({ status: 503, statusText: 'Service Unavailable', error: '<html>' });
    expect(describeError(err)).toBe('503 Service Unavailable');
    expect(isUnreachable(err)).toBe(false);
  });

  it('handles non-HTTP errors', () => {
    expect(describeError(new Error('boom'))).toBe('boom');
    expect(describeError('plain')).toBe('plain');
  });
});
