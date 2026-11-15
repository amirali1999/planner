import { describe, expect, it } from 'vitest';
import { ApiError } from '../../shared/api/client';
import { prefillFromNote, unprefixErrors } from './triageModel';

describe('triage model', () => {
  it('prefills title from the first line and keeps the full note as description', () => {
    expect(prefillFromNote('مطالعه فصل دوم')).toEqual({ title: 'مطالعه فصل دوم', description: '' });
    expect(prefillFromNote('خرید کتاب\nلینک: example.com')).toEqual({ title: 'خرید کتاب', description: 'خرید کتاب\nلینک: example.com' });
    expect([...prefillFromNote('x'.repeat(300)).title]).toHaveLength(200);
  });
  it('maps nested server field errors back onto form fields', () => {
    const mapped = unprefixErrors(new ApiError(400, 'validation_error', 'm', { 'task.title': 'required', decision: 'x' }), 'task');
    expect((mapped as ApiError).fieldCodes()).toEqual({ title: 'required', decision: 'x' });
  });
});
