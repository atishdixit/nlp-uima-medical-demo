import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { SAMPLES } from '../api.service';
import { AnalysisStore } from '../analysis.store';
import { SavedChartsService } from '../saved-charts.service';

/** The left-hand pane: edit a chart, load a sample / saved chart / .txt file, and run the analysis. */
@Component({
  selector: 'app-chart-editor',
  imports: [DecimalPipe],
  templateUrl: './chart-editor.html',
  styleUrl: './chart-editor.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChartEditor {
  protected readonly store = inject(AnalysisStore);
  protected readonly library = inject(SavedChartsService);
  protected readonly samples = SAMPLES;

  /** Name typed in the "save as" box, and the saved chart (if any) the editor is currently linked to. */
  protected readonly chartName = signal('');
  protected readonly currentId = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  protected readonly charCount = computed(() => this.store.text().length);
  protected readonly maxCount = this.store.maxChars;
  protected readonly current = computed(() => {
    const id = this.currentId();
    return id ? this.library.charts().find((c) => c.id === id) ?? null : null;
  });
  /** Unsaved edits relative to the linked saved chart. */
  protected readonly dirty = computed(() => {
    const saved = this.current();
    return saved !== null && saved.text !== this.store.text();
  });

  protected onInput(event: Event): void {
    this.store.setText((event.target as HTMLTextAreaElement).value);
  }

  protected onSample(event: Event): void {
    const select = event.target as HTMLSelectElement;
    if (select.value) {
      this.store.loadSample(select.value);
      this.detach();
    }
    select.value = '';
  }

  protected onOpenSaved(event: Event): void {
    const select = event.target as HTMLSelectElement;
    const chart = this.library.find(select.value);
    select.value = '';
    if (chart) {
      this.store.setText(chart.text);
      this.store.clearResults();
      this.chartName.set(chart.name);
      this.currentId.set(chart.id);
      this.flash(`Opened "${chart.name}"`);
    }
  }

  protected async onFile(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    this.store.setText((await file.text()).replace(/\r\n/g, '\n'));
    this.store.clearResults();
    this.chartName.set(file.name.replace(/\.[^.]+$/, ''));
    this.currentId.set(null);
  }

  protected onName(event: Event): void {
    this.chartName.set((event.target as HTMLInputElement).value);
  }

  protected save(): void {
    const saved = this.library.save(this.chartName(), this.store.text(), this.currentId() ?? undefined);
    this.currentId.set(saved.id);
    this.chartName.set(saved.name);
    this.flash(`Saved "${saved.name}" in this browser`);
  }

  protected saveAsNew(): void {
    const saved = this.library.save(this.chartName(), this.store.text());
    this.currentId.set(saved.id);
    this.chartName.set(saved.name);
    this.flash(`Saved a new copy "${saved.name}"`);
  }

  protected deleteCurrent(): void {
    const chart = this.current();
    if (chart && confirm(`Delete "${chart.name}" from this browser?`)) {
      this.library.remove(chart.id);
      this.detach();
      this.flash(`Deleted "${chart.name}"`);
    }
  }

  protected clear(): void {
    this.store.setText('');
    this.store.clearResults();
    this.detach();
  }

  protected onAuto(event: Event): void {
    this.store.autoAnalyze.set((event.target as HTMLInputElement).checked);
  }

  private detach(): void {
    this.currentId.set(null);
    this.chartName.set('');
  }

  private flash(message: string): void {
    this.notice.set(message);
    setTimeout(() => this.notice.set(null), 3000);
  }
}
