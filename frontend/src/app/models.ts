/** Mirrors the REST DTOs in com.example.mednlp.api.Dtos. Offsets are 0-based UTF-16 indexes, i.e. JS string indexes. */

export interface AnalyzeRequest {
  text: string;
  redactPhi: boolean;
  includeSentences: boolean;
}

export interface Section {
  name: string;
  begin: number;
  end: number;
  experiencer: string;
}

export interface Sentence {
  begin: number;
  end: number;
  text: string;
}

export interface Concept {
  begin: number;
  end: number;
  text: string;
  code: string;
  codeSystem: string;
  preferredName: string;
  category: string;
  negated: boolean;
  negationTrigger: string | null;
  experiencer: string;
  section: string;
}

export interface Phi {
  type: string;
  begin: number;
  end: number;
}

export interface Measurement {
  type: string;
  value: string;
  unit: string | null;
  begin: number;
  end: number;
  text: string;
  rule: string;
}

export interface Stats {
  durationMs: number;
  rulesLoadedAt: string;
  rulesRejected: number;
}

export interface AnalyzeResponse {
  textLength: number;
  sections: Section[];
  sentenceCount: number;
  sentences?: Sentence[];
  concepts: Concept[];
  phi: Phi[];
  measurements: Measurement[];
  redactedText?: string;
  stats: Stats;
}

export interface RejectedRule {
  kind: string;
  name: string;
  reason: string;
}

export interface RuleSummary {
  loadedAt: string;
  sectionRules: number;
  regexRules: number;
  concepts: number;
  triggerTerms: number;
  negationTriggers: number;
  rejected: RejectedRule[];
}

export interface SavedChart {
  id: string;
  name: string;
  text: string;
  updatedAt: number;
}

export interface SampleChart {
  file: string;
  label: string;
}
