import { Span, buildSegments, buildSpans, segmentClasses, segmentTitle } from './highlight';
import { TEXT, sampleResponse } from './testing';

const span = (over: Partial<Span>): Span => ({
  id: 's', kind: 'concept', begin: 0, end: 1, negated: false, label: 'label', ...over,
});

describe('buildSegments', () => {
  it('returns the whole text as one plain segment when nothing is annotated', () => {
    const segments = buildSegments('plain text', []);
    expect(segments).toHaveLength(1);
    expect(segments[0]).toMatchObject({ text: 'plain text', spans: [], sectionStarts: [] });
  });

  it('always reassembles to exactly the original text', () => {
    const response = sampleResponse();
    const segments = buildSegments(TEXT, buildSpans(response));
    expect(segments.map((s) => s.text).join('')).toBe(TEXT);
  });

  it('paints overlapping spans on the shared part (a concept inside a measurement)', () => {
    const text = 'Glucose 182 mg/dL';
    const segments = buildSegments(text, [
      span({ id: 'c0', kind: 'concept', begin: 0, end: 7 }),
      span({ id: 'm0', kind: 'measurement', begin: 0, end: 17 }),
    ]);
    expect(segments.map((s) => s.text)).toEqual(['Glucose', ' 182 mg/dL']);
    expect(segments[0].spans.map((s) => s.kind)).toEqual(['concept', 'measurement']);
    expect(segments[1].spans.map((s) => s.kind)).toEqual(['measurement']);
  });

  it('keeps offsets correct after emoji (UTF-16 surrogate pairs), as the backend reports them', () => {
    const text = '\u{1F600} fever noted';
    const start = text.indexOf('fever');
    const segments = buildSegments(text, [span({ begin: start, end: start + 5 })]);
    expect(segments.find((s) => s.spans.length)?.text).toBe('fever');
  });

  it('ignores empty or inverted spans and clamps spans that run past the text', () => {
    const segments = buildSegments('abc', [
      span({ id: 'a', begin: 2, end: 2 }),
      span({ id: 'b', begin: 3, end: 1 }),
      span({ id: 'c', begin: -5, end: 99 }),
    ]);
    expect(segments).toHaveLength(1);
    expect(segments[0].spans.map((s) => s.id)).toEqual(['c']);
    expect(segments[0].text).toBe('abc');
  });

  it('degrades gracefully when the response no longer matches the text', () => {
    expect(() => buildSegments('', [span({ begin: 4, end: 9 })])).not.toThrow();
    expect(buildSegments('', [span({ begin: 4, end: 9 })]).map((s) => s.text).join('')).toBe('');
  });

  it('marks where section headers begin', () => {
    const text = 'HPI: cough\nPlan: rest';
    const segments = buildSegments(text, [], [
      { name: 'HISTORY_OF_PRESENT_ILLNESS', begin: 0 },
      { name: 'PLAN', begin: 11 },
    ]);
    expect(segments.map((s) => s.text).join('')).toBe(text);
    expect(segments.filter((s) => s.sectionStarts.length).map((s) => [s.text[0], s.sectionStarts])).toEqual([
      ['H', ['HISTORY_OF_PRESENT_ILLNESS']],
      ['P', ['PLAN']],
    ]);
  });
});

describe('buildSpans', () => {
  it('creates one span per finding with stable ids and readable labels', () => {
    const spans = buildSpans(sampleResponse());
    expect(spans.map((s) => s.id)).toEqual(['c0', 'c1', 'm0']);
    expect(spans[0]).toMatchObject({ kind: 'concept', negated: true });
    expect(spans[0].label).toContain('R50.9');
    expect(spans[0].label).toContain('negated by "denies"');
    expect(spans[2].label).toBe('BLOOD_PRESSURE: 120/80 mmHg');
  });

  it('creates PHI spans', () => {
    const spans = buildSpans(sampleResponse({ phi: [{ type: 'MRN', begin: 0, end: 5 }] }));
    expect(spans.at(-1)).toMatchObject({ id: 'p0', kind: 'phi', label: 'PHI: MRN' });
  });
});

describe('segmentClasses / segmentTitle', () => {
  it('distinguishes affirmed and negated concepts', () => {
    const aff = { text: 'x', sectionStarts: [], spans: [span({ negated: false })] };
    const neg = { text: 'x', sectionStarts: [], spans: [span({ negated: true })] };
    expect(segmentClasses(aff, null)).toBe('concept-aff');
    expect(segmentClasses(neg, null)).toBe('concept-neg');
  });

  it('adds "selected" for the picked span only', () => {
    const seg = { text: 'x', sectionStarts: [], spans: [span({ id: 'c1', kind: 'measurement' })] };
    expect(segmentClasses(seg, 'c1')).toBe('measurement selected');
    expect(segmentClasses(seg, 'c2')).toBe('measurement');
  });

  it('has no class and no tooltip for plain text', () => {
    const plain = { text: 'x', sectionStarts: [], spans: [] };
    expect(segmentClasses(plain, null)).toBe('');
    expect(segmentTitle(plain)).toBeNull();
  });

  it('joins the labels of every span on a segment into the tooltip', () => {
    const seg = { text: 'x', sectionStarts: [], spans: [span({ label: 'one' }), span({ label: 'two' })] };
    expect(segmentTitle(seg)).toBe('one\ntwo');
  });
});
