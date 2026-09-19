import { TestBed } from '@angular/core/testing';
import { SavedChartsService } from './saved-charts.service';

const KEY = 'mednlp.charts.v1';

function create(): SavedChartsService {
  TestBed.resetTestingModule();
  return TestBed.inject(SavedChartsService);
}

describe('SavedChartsService', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => vi.restoreAllMocks());

  it('starts empty', () => {
    expect(create().charts()).toEqual([]);
  });

  it('saves a chart, newest first, and persists it', () => {
    const service = create();
    service.save('First', 'aaa');
    service.save('Second', 'bbb');
    expect(service.charts().map((c) => c.name)).toEqual(['Second', 'First']);
    expect(JSON.parse(localStorage.getItem(KEY) ?? '[]')).toHaveLength(2);
  });

  it('reloads charts saved by an earlier session', () => {
    create().save('Kept', 'text');
    expect(create().charts().map((c) => [c.name, c.text])).toEqual([['Kept', 'text']]);
  });

  it('updates in place when given an existing id (no duplicate)', () => {
    const service = create();
    const saved = service.save('Chart', 'v1');
    const updated = service.save('Chart renamed', 'v2', saved.id);
    expect(updated.id).toBe(saved.id);
    expect(service.charts()).toHaveLength(1);
    expect(service.find(saved.id)).toMatchObject({ name: 'Chart renamed', text: 'v2' });
  });

  it('treats an unknown id as a new chart', () => {
    const service = create();
    service.save('A', 'a', 'does-not-exist');
    expect(service.charts()).toHaveLength(1);
  });

  it('names blank charts', () => {
    expect(create().save('   ', 'x').name).toBe('Untitled chart');
  });

  it('removes charts', () => {
    const service = create();
    const a = service.save('A', 'a');
    service.save('B', 'b');
    service.remove(a.id);
    expect(service.charts().map((c) => c.name)).toEqual(['B']);
    expect(service.find(a.id)).toBeUndefined();
  });

  it('survives corrupt or foreign data in storage', () => {
    for (const junk of ['not json', '{"a":1}', '[1,2,3]', '[{"id":1}]', 'null']) {
      localStorage.setItem(KEY, junk);
      expect(create().charts()).toEqual([]);
    }
  });

  it('keeps only the valid entries of a partly damaged list', () => {
    localStorage.setItem(KEY, JSON.stringify([{ id: 'x', name: 'ok', text: 't', updatedAt: 1 }, { id: 'bad' }]));
    expect(create().charts().map((c) => c.id)).toEqual(['x']);
  });

  it('still works in memory when storage is blocked or full', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('full', 'QuotaExceededError');
    });
    const service = create();
    expect(() => service.save('A', 'a')).not.toThrow();
    expect(service.charts()).toHaveLength(1);
  });

  it('does not need crypto.randomUUID (insecure origins such as http://192.168.x.x)', () => {
    vi.stubGlobal('crypto', {});
    try {
      const a = create().save('A', 'a');
      expect(a.id).toBeTruthy();
    } finally {
      vi.unstubAllGlobals();
    }
  });
});
