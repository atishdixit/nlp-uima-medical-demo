import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { AnalysisStore } from '../analysis.store';
import { Concept } from '../models';
import { HighlightedChart } from './highlighted-chart';

type Tab = 'highlight' | 'concepts' | 'measurements' | 'phi' | 'sections' | 'sentences' | 'redacted' | 'json';
type ConceptFilter = 'all' | 'affirmed' | 'negated';

interface ConceptRow {
  id: string;
  concept: Concept;
}

/** The right-hand pane: summary, an annotated view of the chart, and a table per kind of finding. */
@Component({
  selector: 'app-results',
  imports: [DecimalPipe, HighlightedChart],
  templateUrl: './results.html',
  styleUrl: './results.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Results {
  protected readonly store = inject(AnalysisStore);

  protected readonly tab = signal<Tab>('highlight');
  protected readonly conceptFilter = signal<ConceptFilter>('all');
  protected readonly query = signal('');
  protected readonly copied = signal(false);

  protected readonly negatedCount = computed(() => this.store.response()?.concepts.filter((c) => c.negated).length ?? 0);

  protected readonly conceptRows = computed<ConceptRow[]>(() => {
    const response = this.store.response();
    if (!response) {
      return [];
    }
    const filter = this.conceptFilter();
    const q = this.query().trim().toLowerCase();
    return response.concepts
      .map((concept, i) => ({ id: `c${i}`, concept }))
      .filter(({ concept: c }) => filter === 'all' || (filter === 'negated') === c.negated)
      .filter(
        ({ concept: c }) =>
          !q || [c.text, c.code, c.preferredName, c.category, c.section, c.codeSystem].some((v) => v.toLowerCase().includes(q)),
      );
  });

  protected readonly measurementRows = computed(() => (this.store.response()?.measurements ?? []).map((m, i) => ({ id: `m${i}`, m })));

  protected readonly phiRows = computed(() => {
    const text = this.store.analyzedText();
    return (this.store.response()?.phi ?? []).map((p, i) => ({ id: `p${i}`, p, text: text.slice(p.begin, p.end) }));
  });

  protected readonly json = computed(() => JSON.stringify(this.store.response(), null, 2));

  protected readonly tabs = computed<{ id: Tab; label: string; count?: number }[]>(() => {
    const r = this.store.response();
    return [
      { id: 'highlight', label: 'Highlighted' },
      { id: 'concepts', label: 'Concepts', count: r?.concepts.length },
      { id: 'measurements', label: 'Measurements', count: r?.measurements.length },
      { id: 'phi', label: 'PHI', count: r?.phi.length },
      { id: 'sections', label: 'Sections', count: r?.sections.length },
      { id: 'sentences', label: 'Sentences', count: r?.sentenceCount },
      { id: 'redacted', label: 'Redacted' },
      { id: 'json', label: 'JSON' },
    ];
  });

  protected setFilter(event: Event): void {
    this.conceptFilter.set((event.target as HTMLSelectElement).value as ConceptFilter);
  }

  protected setQuery(event: Event): void {
    this.query.set((event.target as HTMLInputElement).value);
  }

  /** Picking a finding in a table jumps to it in the annotated chart. */
  protected reveal(id: string): void {
    this.store.select(id);
    this.tab.set('highlight');
  }

  protected async copy(text: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(text);
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 1500);
    } catch {
      // clipboard blocked (insecure origin or denied permission): nothing useful to do
    }
  }
}
