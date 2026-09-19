import { ChangeDetectionStrategy, Component, ElementRef, Injector, afterNextRender, computed, effect, inject } from '@angular/core';
import { AnalysisStore } from '../analysis.store';
import { Segment, buildSegments, buildSpans, segmentClasses, segmentTitle } from '../highlight';

/** Rendering thousands of DOM nodes freezes the page; above this the tables are the better view. */
const MAX_HIGHLIGHT_CHARS = 150_000;

/** The analysed chart with every finding painted on it, and section headers marked in the margin. */
@Component({
  selector: 'app-highlighted-chart',
  templateUrl: './highlighted-chart.html',
  styleUrl: './highlighted-chart.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HighlightedChart {
  protected readonly store = inject(AnalysisStore);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  protected readonly tooBig = computed(() => this.store.analyzedText().length > MAX_HIGHLIGHT_CHARS);

  protected readonly segments = computed<Segment[]>(() => {
    const response = this.store.response();
    if (!response || this.tooBig()) {
      return [];
    }
    return buildSegments(this.store.analyzedText(), buildSpans(response), response.sections);
  });

  constructor() {
    // bring the row picked in a table into view once the new "selected" class has been rendered
    effect(() => {
      if (this.store.selectedId()) {
        afterNextRender(
          () => this.host.nativeElement.querySelector('.selected')?.scrollIntoView({ block: 'center', behavior: 'smooth' }),
          { injector: this.injector },
        );
      }
    });
  }

  protected classes(segment: Segment): string {
    return segmentClasses(segment, this.store.selectedId());
  }

  protected title(segment: Segment): string | null {
    return segmentTitle(segment);
  }
}
