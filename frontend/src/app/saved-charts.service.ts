import { Injectable, signal } from '@angular/core';
import { SavedChart } from './models';

const KEY = 'mednlp.charts.v1';

/** crypto.randomUUID exists only in secure contexts (https or localhost), so fall back when the UI is opened by LAN IP. */
function newId(): string {
  return typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

/**
 * A personal chart library kept in this browser's localStorage. Nothing is sent to the server, which is
 * important because charts can contain PHI. Every storage access is guarded: storage can be blocked,
 * full, or hold garbage from an older version.
 */
@Injectable({ providedIn: 'root' })
export class SavedChartsService {
  private readonly _charts = signal<SavedChart[]>(this.read());
  readonly charts = this._charts.asReadonly();

  /** Creates a chart, or updates the one with {@code id}. Returns the saved chart. */
  save(name: string, text: string, id?: string): SavedChart {
    const trimmed = name.trim() || 'Untitled chart';
    const existing = id ? this._charts().find((c) => c.id === id) : undefined;
    const chart: SavedChart = {
      id: existing?.id ?? newId(),
      name: trimmed,
      text,
      updatedAt: Date.now(),
    };
    const others = this._charts().filter((c) => c.id !== chart.id);
    this.commit([chart, ...others]);
    return chart;
  }

  remove(id: string): void {
    this.commit(this._charts().filter((c) => c.id !== id));
  }

  find(id: string): SavedChart | undefined {
    return this._charts().find((c) => c.id === id);
  }

  private commit(charts: SavedChart[]): void {
    this._charts.set(charts);
    try {
      localStorage.setItem(KEY, JSON.stringify(charts));
    } catch {
      // storage full or blocked: the in-memory list still works for this session
    }
  }

  private read(): SavedChart[] {
    try {
      const parsed: unknown = JSON.parse(localStorage.getItem(KEY) ?? '[]');
      if (!Array.isArray(parsed)) {
        return [];
      }
      return parsed.filter(
        (c): c is SavedChart =>
          typeof c === 'object' &&
          c !== null &&
          typeof c.id === 'string' &&
          typeof c.name === 'string' &&
          typeof c.text === 'string' &&
          typeof c.updatedAt === 'number',
      );
    } catch {
      return [];
    }
  }
}
