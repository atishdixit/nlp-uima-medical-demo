import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { AnalysisStore } from './analysis.store';
import { ChartEditor } from './chart-editor/chart-editor';
import { Results } from './results/results';
import { RulesPanel } from './rules-panel/rules-panel';

@Component({
  selector: 'app-root',
  imports: [ChartEditor, Results, RulesPanel],
  templateUrl: './app.html',
  styleUrl: './app.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  protected readonly store = inject(AnalysisStore);

  constructor() {
    this.store.loadRules();
  }
}
