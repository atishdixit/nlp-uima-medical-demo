import { AnalyzeResponse } from './models';

export type SpanKind = 'concept' | 'measurement' | 'phi';

/** One annotation to paint on the chart text. */
export interface Span {
  id: string;
  kind: SpanKind;
  begin: number;
  end: number;
  negated: boolean;
  label: string;
}

/** A run of text with identical annotations, ready to render as one element. */
export interface Segment {
  text: string;
  spans: Span[];
  /** Section headers that begin exactly at the start of this segment. */
  sectionStarts: string[];
}

export function buildSpans(response: AnalyzeResponse): Span[] {
  const spans: Span[] = [];
  response.concepts.forEach((c, i) =>
    spans.push({
      id: `c${i}`,
      kind: 'concept',
      begin: c.begin,
      end: c.end,
      negated: c.negated,
      label: `${c.code} (${c.codeSystem}) ${c.preferredName}` + (c.negated ? ` — negated by "${c.negationTrigger}"` : ''),
    }),
  );
  response.measurements.forEach((m, i) =>
    spans.push({
      id: `m${i}`,
      kind: 'measurement',
      begin: m.begin,
      end: m.end,
      negated: false,
      label: `${m.type}: ${m.value}${m.unit ? ' ' + m.unit : ''}`,
    }),
  );
  response.phi.forEach((p, i) =>
    spans.push({ id: `p${i}`, kind: 'phi', begin: p.begin, end: p.end, negated: false, label: `PHI: ${p.type}` }),
  );
  return spans;
}

/**
 * Splits the text at every span boundary so overlapping annotations (a concept inside a measurement,
 * for example) can be painted together. Offsets are clamped, so a response that no longer matches the
 * text degrades to less highlighting instead of throwing.
 */
export function buildSegments(text: string, spans: Span[], sectionStarts: { name: string; begin: number }[] = []): Segment[] {
  const length = text.length;
  const clamp = (n: number) => Math.min(Math.max(n, 0), length);
  const valid = spans
    .map((s) => ({ ...s, begin: clamp(s.begin), end: clamp(s.end) }))
    .filter((s) => s.end > s.begin);

  const cuts = new Set<number>([0, length]);
  valid.forEach((s) => {
    cuts.add(s.begin);
    cuts.add(s.end);
  });
  const sectionsAt = new Map<number, string[]>();
  sectionStarts.forEach((s) => {
    const at = clamp(s.begin);
    cuts.add(at);
    sectionsAt.set(at, [...(sectionsAt.get(at) ?? []), s.name]);
  });

  const points = [...cuts].sort((a, b) => a - b);
  const segments: Segment[] = [];
  for (let i = 0; i < points.length - 1; i++) {
    const from = points[i];
    const to = points[i + 1];
    segments.push({
      text: text.slice(from, to),
      spans: valid.filter((s) => s.begin <= from && s.end >= to),
      sectionStarts: sectionsAt.get(from) ?? [],
    });
  }
  return segments;
}

/** CSS classes for a segment; {@code selectedId} highlights the span picked in a table. */
export function segmentClasses(segment: Segment, selectedId: string | null): string {
  const classes: string[] = [];
  for (const s of segment.spans) {
    if (s.kind === 'concept') {
      classes.push(s.negated ? 'concept-neg' : 'concept-aff');
    } else {
      classes.push(s.kind);
    }
    if (s.id === selectedId) {
      classes.push('selected');
    }
  }
  return classes.join(' ');
}

export function segmentTitle(segment: Segment): string | null {
  return segment.spans.length ? segment.spans.map((s) => s.label).join('\n') : null;
}
