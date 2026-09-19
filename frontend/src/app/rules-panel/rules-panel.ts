import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { AnalysisStore } from '../analysis.store';

/** Header drop-down: what rules the backend is running, which were rejected, and a button to reload them from MySQL. */
@Component({
  selector: 'app-rules-panel',
  imports: [DatePipe],
  templateUrl: './rules-panel.html',
  styleUrl: './rules-panel.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RulesPanel {
  protected readonly store = inject(AnalysisStore);
}
