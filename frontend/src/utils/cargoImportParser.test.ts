import { describe, it, expect } from 'vitest';
import { 
  parseAndValidateCsv, 
  parseAndValidateJson, 
  SAMPLE_CSV_TEMPLATE, 
  SAMPLE_JSON_TEMPLATE 
} from './cargoImportParser';

describe('cargoImportParser', () => {
  it('parses valid CSV template correctly without errors', () => {
    const res = parseAndValidateCsv(SAMPLE_CSV_TEMPLATE);
    expect(res.globalErrors).toHaveLength(0);
    expect(res.totalRows).toBe(3);
    expect(res.validRowsCount).toBe(3);
    expect(res.invalidRowsCount).toBe(0);
    expect(res.rows[0].item.cargoItemId).toBe('ITEM-SOLVENT-01');
    expect(res.rows[0].item.lengthMm).toBe(1200);
    expect(res.rows[0].item.stackable).toBe(true);
    expect(res.rows[1].item.stackable).toBe(false);
  });

  it('parses valid JSON template correctly without errors', () => {
    const res = parseAndValidateJson(SAMPLE_JSON_TEMPLATE);
    expect(res.globalErrors).toHaveLength(0);
    expect(res.totalRows).toBe(2);
    expect(res.validRowsCount).toBe(2);
    expect(res.invalidRowsCount).toBe(0);
    expect(res.rows[0].item.hazardClass).toBe('3');
  });

  it('detects duplicate cargoItemIds across rows and flags duplicate error', () => {
    const csvWithDupes = `cargoItemId,description,hazardClass,unNumber,lengthMm,widthMm,heightMm,weightKg,stackable,maxStackWeightKg,quantity
BOX-01,Box Alpha,3,UN1993,1000,1000,1000,200,true,500,1
BOX-01,Box Duplicate,3,UN1993,1000,1000,1000,200,true,500,1`;
    const res = parseAndValidateCsv(csvWithDupes);
    expect(res.totalRows).toBe(2);
    expect(res.validRowsCount).toBe(1);
    expect(res.invalidRowsCount).toBe(1);
    expect(res.rows[1].errors.some(e => e.code === 'DUPLICATE_ITEM_ID')).toBe(true);
  });

  it('detects invalid dimensions and non-integer millimeter values fail-closed', () => {
    const csvBadDims = `cargoItemId,description,hazardClass,unNumber,lengthMm,widthMm,heightMm,weightKg,stackable,maxStackWeightKg,quantity
BOX-01,Box Fractional,3,UN1993,1000.5,0,-50,200,true,500,1`;
    const res = parseAndValidateCsv(csvBadDims);
    expect(res.totalRows).toBe(1);
    expect(res.rows[0].isValid).toBe(false);
    expect(res.rows[0].errors.some(e => e.code === 'INVALID_LENGTH')).toBe(true);
    expect(res.rows[0].errors.some(e => e.code === 'INVALID_WIDTH')).toBe(true);
    expect(res.rows[0].errors.some(e => e.code === 'INVALID_HEIGHT')).toBe(true);
  });

  it('detects unrecognized hazard classifications and malformed UN numbers fail-closed', () => {
    const jsonBadHaz = JSON.stringify([
      {
        cargoItemId: 'BOX-BAD',
        description: 'Unknown Haz Class',
        hazardClass: 'UNKNOWN_99',
        unNumber: 'NOT_A_UN_CODE',
        lengthMm: 1000,
        widthMm: 1000,
        heightMm: 1000,
        weightKg: 200,
        stackable: true,
        maxStackWeightKg: 0,
        quantity: 1
      }
    ]);
    const res = parseAndValidateJson(jsonBadHaz);
    expect(res.totalRows).toBe(1);
    expect(res.rows[0].isValid).toBe(false);
    expect(res.rows[0].errors.some(e => e.code === 'INVALID_HAZARD_CLASS')).toBe(true);
    expect(res.rows[0].errors.some(e => e.code === 'INVALID_UN_NUMBER_FORMAT')).toBe(true);
  });

  it('detects missing headers in CSV and reports global error', () => {
    const badCsv = `colA,colB,colC\n1,2,3`;
    const res = parseAndValidateCsv(badCsv);
    expect(res.globalErrors.length).toBeGreaterThan(0);
    expect(res.globalErrors[0]).toContain('Missing required CSV headers');
  });

  it('detects malformed JSON syntax and reports global error without throwing', () => {
    const badJson = `{ "unclosed": `;
    const res = parseAndValidateJson(badJson);
    expect(res.globalErrors.length).toBeGreaterThan(0);
    expect(res.globalErrors[0]).toContain('JSON Syntax Error');
  });
});
