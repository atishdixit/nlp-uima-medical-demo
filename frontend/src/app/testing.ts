import { AnalyzeResponse } from './models';

/** A small but realistic response for "Patient denies fever but reports cough. BP 120/80." used by the specs. */
export const TEXT = 'Patient denies fever but reports cough. BP 120/80.';

export function sampleResponse(overrides: Partial<AnalyzeResponse> = {}): AnalyzeResponse {
  return {
    textLength: TEXT.length,
    sections: [],
    sentenceCount: 2,
    sentences: [
      { begin: 0, end: 39, text: 'Patient denies fever but reports cough.' },
      { begin: 40, end: 50, text: 'BP 120/80.' },
    ],
    concepts: [
      {
        begin: 15, end: 20, text: 'fever', code: 'R50.9', codeSystem: 'ICD10CM', preferredName: 'Fever, unspecified',
        category: 'SYMPTOM', negated: true, negationTrigger: 'denies', experiencer: 'PATIENT', section: 'UNSECTIONED',
      },
      {
        begin: 33, end: 38, text: 'cough', code: 'R05.9', codeSystem: 'ICD10CM', preferredName: 'Cough, unspecified',
        category: 'SYMPTOM', negated: false, negationTrigger: null, experiencer: 'PATIENT', section: 'UNSECTIONED',
      },
    ],
    phi: [],
    measurements: [
      { type: 'BLOOD_PRESSURE', value: '120/80', unit: 'mmHg', begin: 40, end: 49, text: 'BP 120/80', rule: 'MEAS_BLOOD_PRESSURE' },
    ],
    redactedText: TEXT,
    stats: { durationMs: 5, rulesLoadedAt: '2026-09-19T05:30:39Z', rulesRejected: 0 },
    ...overrides,
  };
}

export const RULES = {
  loadedAt: '2026-09-19T05:30:39Z',
  sectionRules: 13,
  regexRules: 13,
  concepts: 39,
  triggerTerms: 61,
  negationTriggers: 48,
  rejected: [],
};
