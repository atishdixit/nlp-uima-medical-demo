import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { App } from './app';
import { RULES, TEXT, sampleResponse } from './testing';

/** Drives the real component tree through the DOM with a mocked HTTP backend. */
describe('App', () => {
  let fixture: ComponentFixture<App>;
  let http: HttpTestingController;

  const el = () => fixture.nativeElement as HTMLElement;
  const settle = async () => {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  };
  const button = (label: string) =>
    Array.from(el().querySelectorAll('button')).find((b) => b.textContent?.trim().startsWith(label)) as HTMLButtonElement;
  const type = async (text: string) => {
    const area = el().querySelector('textarea') as HTMLTextAreaElement;
    area.value = text;
    area.dispatchEvent(new Event('input'));
    await settle();
  };
  const analyze = async (response = sampleResponse()) => {
    await type(TEXT);
    button('Analyze').click();
    http.expectOne('/api/v1/charts/analyze').flush(response);
    await settle();
  };
  const openTab = async (label: string) => {
    (Array.from(el().querySelectorAll('[role=tab]')).find((t) => t.textContent?.trim().startsWith(label)) as HTMLElement).click();
    await settle();
  };

  beforeEach(async () => {
    localStorage.clear();
    TestBed.configureTestingModule({ imports: [App], providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(App);
    await settle();
    http.expectOne('/api/v1/rules/summary').flush(RULES);
    await settle();
  });

  afterEach(() => http.verify());

  it('shows the header, the backend status and the rule counts', () => {
    expect(el().querySelector('h1')?.textContent).toContain('Medical Chart NLP');
    expect(el().textContent).toContain('Backend connected');
    expect(el().querySelector('summary')?.textContent).toContain('39 concepts');
  });

  it('starts with an empty editor, a disabled Analyze button and a hint instead of results', () => {
    expect(button('Analyze').disabled).toBe(true);
    expect(el().textContent).toContain('No results yet');
  });

  it('enables Analyze once there is text and shows the character count', async () => {
    await type(TEXT);
    expect(button('Analyze').disabled).toBe(false);
    expect(el().textContent).toContain(`${TEXT.length} / 1,000,000 characters`);
  });

  it('analyzes the chart and shows the summary and the highlighted findings', async () => {
    await analyze();
    expect(el().querySelector('.summary')?.textContent).toContain('2 concepts');
    expect(el().querySelector('.summary')?.textContent).toContain('1 negated');

    const negated = el().querySelector('pre.chart .concept-neg') as HTMLElement;
    const affirmed = el().querySelector('pre.chart .concept-aff') as HTMLElement;
    expect(negated.textContent).toBe('fever');
    expect(negated.title).toContain('negated by "denies"');
    expect(affirmed.textContent).toBe('cough');
    expect(el().querySelector('pre.chart .measurement')?.textContent).toBe('BP 120/80');
    expect(el().querySelector('pre.chart')?.textContent).toBe(TEXT);
  });

  it('lists concepts in a table with code, status and trigger', async () => {
    await analyze();
    await openTab('Concepts');
    const rows = Array.from(el().querySelectorAll('tbody tr'));
    expect(rows).toHaveLength(2);
    expect(rows[0].textContent).toContain('R50.9');
    expect(rows[0].textContent).toContain('Negated');
    expect(rows[0].textContent).toContain('denies');
    expect(rows[1].textContent).toContain('Affirmed');
  });

  it('filters the concept table by status and by search text', async () => {
    await analyze();
    await openTab('Concepts');
    const select = el().querySelector('.toolbar select') as HTMLSelectElement;
    select.value = 'negated';
    select.dispatchEvent(new Event('change'));
    await settle();
    expect(el().querySelectorAll('tbody tr')).toHaveLength(1);

    select.value = 'all';
    select.dispatchEvent(new Event('change'));
    const search = el().querySelector('.toolbar input') as HTMLInputElement;
    search.value = 'zzz';
    search.dispatchEvent(new Event('input'));
    await settle();
    expect(el().textContent).toContain('No concepts match the filter');
  });

  it('jumps to the chart and marks the finding when a table row is clicked', async () => {
    await analyze();
    await openTab('Concepts');
    (el().querySelectorAll('tbody tr')[1] as HTMLElement).click();
    await settle();
    expect(el().querySelector('pre.chart')).not.toBeNull();
    expect(el().querySelector('.selected')?.textContent).toBe('cough');
  });

  it('shows measurements, and friendly empty states for the other tabs', async () => {
    await analyze();
    await openTab('Measurements');
    expect(el().querySelector('tbody')?.textContent).toContain('120/80');
    await openTab('PHI');
    expect(el().textContent).toContain('No protected health information was detected');
    await openTab('Sections');
    expect(el().textContent).toContain('No section headers were found');
  });

  it('shows the PHI text found and the redacted copy', async () => {
    const text = 'MRN: 12345678';
    await type(text);
    button('Analyze').click();
    http.expectOne('/api/v1/charts/analyze').flush(
      sampleResponse({ textLength: text.length, concepts: [], measurements: [], phi: [{ type: 'MRN', begin: 0, end: 13 }], redactedText: '[MRN]', sentences: [] }),
    );
    await settle();
    await openTab('PHI');
    expect(el().querySelector('tbody')?.textContent).toContain('MRN: 12345678');
    await openTab('Redacted');
    expect(el().querySelector('pre.plain')?.textContent).toBe('[MRN]');
  });

  it('shows the raw JSON', async () => {
    await analyze();
    await openTab('JSON');
    expect(el().querySelector('pre.plain')?.textContent).toContain('"code": "R50.9"');
  });

  it('warns that the results are stale after editing, and clears the warning after re-analyzing', async () => {
    await analyze();
    await type(TEXT + ' More.');
    expect(el().textContent).toContain('Results are out of date');
    expect(el().textContent).toContain('edited after this analysis');
    button('Analyze').click();
    http.expectOne('/api/v1/charts/analyze').flush(sampleResponse());
    await settle();
    expect(el().textContent).not.toContain('Results are out of date');
  });

  it('shows the server error message in an alert', async () => {
    await type(TEXT);
    button('Analyze').click();
    http.expectOne('/api/v1/charts/analyze').flush({ detail: 'All 4 analysis engines are busy; retry shortly' }, { status: 503, statusText: 'Unavailable' });
    await settle();
    expect(el().querySelector('[role=alert]')?.textContent).toContain('engines are busy');
  });

  it('shows a rejected-rules warning when the backend skipped rules', async () => {
    await analyze(sampleResponse({ stats: { durationMs: 1, rulesLoadedAt: '', rulesRejected: 2 } }));
    expect(el().textContent).toContain('2 rule(s) in the database were rejected');
  });

  it('saves a chart in this browser, lists it, and can delete it again', async () => {
    await type(TEXT);
    const name = el().querySelector('#chart-name') as HTMLInputElement;
    name.value = 'My test chart';
    name.dispatchEvent(new Event('input'));
    await settle();
    button('Save').click();
    await settle();
    expect(el().textContent).toContain('Saved "My test chart" in this browser');
    expect(JSON.parse(localStorage.getItem('mednlp.charts.v1') ?? '[]')).toHaveLength(1);
    expect(el().querySelector('#saved-select')?.textContent).toContain('My test chart');

    vi.spyOn(window, 'confirm').mockReturnValue(true);
    button('Delete').click();
    await settle();
    expect(JSON.parse(localStorage.getItem('mednlp.charts.v1') ?? '[]')).toHaveLength(0);
  });

  it('loads a sample chart into the editor', async () => {
    const select = el().querySelector('#sample-select') as HTMLSelectElement;
    select.value = 'soap-note.txt';
    select.dispatchEvent(new Event('change'));
    http.expectOne('samples/soap-note.txt').flush('Chief Complaint: cough');
    await settle();
    expect((el().querySelector('textarea') as HTMLTextAreaElement).value).toBe('Chief Complaint: cough');
  });

  it('clears the editor and the results', async () => {
    await analyze();
    button('Clear').click();
    await settle();
    expect((el().querySelector('textarea') as HTMLTextAreaElement).value).toBe('');
    expect(el().textContent).toContain('No results yet');
  });

  it('flags a chart that is longer than the server limit', async () => {
    await type('x'.repeat(1_000_001));
    expect(el().textContent).toContain('Too long');
    expect(button('Analyze').disabled).toBe(true);
  });

  it('replaces the annotated view by a hint for very large charts, but keeps the tables', async () => {
    const big = 'fever '.repeat(30_000); // 180,000 characters
    await type(big);
    button('Analyze').click();
    http.expectOne('/api/v1/charts/analyze').flush(sampleResponse({ textLength: big.length, concepts: [], measurements: [], sentences: [] }));
    await settle();
    expect(el().textContent).toContain('too large to highlight');
    expect(el().querySelector('pre.chart')).toBeNull();
    await openTab('Concepts');
    expect(el().textContent).toContain('No concepts were found');
  });

  it('lists rejected rules in the rules panel and reloads them on request', async () => {
    button('Reload rules').click();
    http.expectOne('/api/v1/admin/rules/refresh').flush({
      ...RULES,
      rejected: [{ kind: 'REGEX', name: 'BROKEN_RULE', reason: 'invalid regex: Unclosed character class' }],
    });
    await settle();
    expect(el().querySelector('.rules')?.textContent).toContain('BROKEN_RULE');
    expect(el().querySelector('summary')?.textContent).toContain('1 rejected');
  });

  it('offers a retry when the backend is offline', async () => {
    button('Reload rules').click();
    http.expectOne('/api/v1/admin/rules/refresh').error(new ProgressEvent('error'), { status: 0 });
    await settle();
    expect(el().textContent).toContain('Backend offline');
    button('Backend offline').click();
    http.expectOne('/api/v1/rules/summary').flush(RULES);
    await settle();
    expect(el().textContent).toContain('Backend connected');
  });
});
