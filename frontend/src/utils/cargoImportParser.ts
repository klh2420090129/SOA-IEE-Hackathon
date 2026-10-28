import { CargoItemInput } from '../types/api';

export interface RowValidationError {
  rowNumber: number;
  field: keyof CargoItemInput | 'cargoItemId' | 'general';
  code: string;
  message: string;
}

export interface ParsedCargoRow {
  rowNumber: number;
  item: CargoItemInput;
  errors: RowValidationError[];
  isValid: boolean;
}

export interface ImportParseResult {
  totalRows: number;
  validRowsCount: number;
  invalidRowsCount: number;
  rows: ParsedCargoRow[];
  globalErrors: string[];
}

export const VALID_HAZARD_CLASSES = new Set([
  '1', '1.1', '1.2', '1.3', '1.4', '1.5', '1.6',
  '2.1', '2.2', '2.3',
  '3',
  '4.1', '4.2', '4.3',
  '5.1', '5.2',
  '6.1', '6.2',
  '7',
  '8',
  '9'
]);

export const UN_NUMBER_REGEX = /^UN\d{4}$/i;

/**
 * Parses raw CSV content into array of rows.
 */
export function parseCsvRows(csvText: string): string[][] {
  const lines = csvText.split(/\r?\n/).map(l => l.trim()).filter(l => l.length > 0);
  if (lines.length === 0) return [];

  return lines.map(line => {
    const row: string[] = [];
    let insideQuotes = false;
    let current = '';

    for (let i = 0; i < line.length; i++) {
      const char = line[i];
      if (char === '"') {
        insideQuotes = !insideQuotes;
      } else if (char === ',' && !insideQuotes) {
        row.push(current.trim());
        current = '';
      } else {
        current += char;
      }
    }
    row.push(current.trim());
    return row.map(cell => cell.replace(/^"(.*)"$/, '$1').trim());
  });
}

/**
 * Validates a single cargo item input.
 */
export function validateCargoItem(item: Partial<CargoItemInput>, rowNum: number, existingIds: Set<string>): RowValidationError[] {
  const errors: RowValidationError[] = [];

  // Description
  if (!item.description || item.description.trim().length === 0) {
    errors.push({
      rowNumber: rowNum,
      field: 'description',
      code: 'MISSING_DESCRIPTION',
      message: 'Cargo description is required'
    });
  }

  // ID Uniqueness & Non-empty
  const id = item.cargoItemId ? item.cargoItemId.trim() : '';
  if (!id) {
    errors.push({
      rowNumber: rowNum,
      field: 'cargoItemId',
      code: 'MISSING_ITEM_ID',
      message: 'Item ID is required for catalog imports'
    });
  } else if (existingIds.has(id.toLowerCase())) {
    errors.push({
      rowNumber: rowNum,
      field: 'cargoItemId',
      code: 'DUPLICATE_ITEM_ID',
      message: `Duplicate item ID '${id}' detected in import`
    });
  }

  // Hazard Class Validation
  const haz = (item.hazardClass || '').trim();
  if (!haz) {
    errors.push({
      rowNumber: rowNum,
      field: 'hazardClass',
      code: 'MISSING_HAZARD_CLASS',
      message: 'Hazard class is required'
    });
  } else if (!VALID_HAZARD_CLASSES.has(haz)) {
    errors.push({
      rowNumber: rowNum,
      field: 'hazardClass',
      code: 'INVALID_HAZARD_CLASS',
      message: `Hazard class '${haz}' is not recognized under statutory CMV/IMDG rules`
    });
  }

  // UN Number Validation
  const un = (item.unNumber || '').trim().toUpperCase();
  if (!un) {
    errors.push({
      rowNumber: rowNum,
      field: 'unNumber',
      code: 'MISSING_UN_NUMBER',
      message: 'UN number is required'
    });
  } else if (!UN_NUMBER_REGEX.test(un)) {
    errors.push({
      rowNumber: rowNum,
      field: 'unNumber',
      code: 'INVALID_UN_NUMBER_FORMAT',
      message: `UN number '${un}' must follow format 'UN####' (e.g., UN1993)`
    });
  }

  // Dimensions Validation (Integers in mm > 0)
  const l = Number(item.lengthMm);
  const w = Number(item.widthMm);
  const h = Number(item.heightMm);

  if (isNaN(l) || l <= 0 || !Number.isInteger(l)) {
    errors.push({
      rowNumber: rowNum,
      field: 'lengthMm',
      code: 'INVALID_LENGTH',
      message: `Length must be a positive integer in mm (received ${item.lengthMm})`
    });
  }

  if (isNaN(w) || w <= 0 || !Number.isInteger(w)) {
    errors.push({
      rowNumber: rowNum,
      field: 'widthMm',
      code: 'INVALID_WIDTH',
      message: `Width must be a positive integer in mm (received ${item.widthMm})`
    });
  }

  if (isNaN(h) || h <= 0 || !Number.isInteger(h)) {
    errors.push({
      rowNumber: rowNum,
      field: 'heightMm',
      code: 'INVALID_HEIGHT',
      message: `Height must be a positive integer in mm (received ${item.heightMm})`
    });
  }

  // Weight Validation (> 0)
  const wt = Number(item.weightKg);
  if (isNaN(wt) || wt <= 0) {
    errors.push({
      rowNumber: rowNum,
      field: 'weightKg',
      code: 'INVALID_WEIGHT',
      message: `Weight must be greater than 0 kg (received ${item.weightKg})`
    });
  }

  // Quantity Validation (Integer >= 1)
  const qty = Number(item.quantity);
  if (isNaN(qty) || qty < 1 || !Number.isInteger(qty)) {
    errors.push({
      rowNumber: rowNum,
      field: 'quantity',
      code: 'INVALID_QUANTITY',
      message: `Quantity must be an integer >= 1 (received ${item.quantity})`
    });
  }

  // Stacking Weight Validation
  const maxStack = Number(item.maxStackWeightKg);
  if (isNaN(maxStack) || maxStack < 0) {
    errors.push({
      rowNumber: rowNum,
      field: 'maxStackWeightKg',
      code: 'INVALID_MAX_STACK_WEIGHT',
      message: 'Max stack weight must be a non-negative number'
    });
  }

  return errors;
}

/**
 * Parses and validates CSV content.
 */
export function parseAndValidateCsv(csvText: string): ImportParseResult {
  const rawRows = parseCsvRows(csvText);
  if (rawRows.length === 0) {
    return {
      totalRows: 0,
      validRowsCount: 0,
      invalidRowsCount: 0,
      rows: [],
      globalErrors: ['CSV file is empty']
    };
  }

  const header = rawRows[0].map(h => h.toLowerCase().replace(/[^a-z0-9]/g, ''));
  const expectedFields: Record<string, string> = {
    cargoitemid: 'cargoItemId',
    description: 'description',
    hazardclass: 'hazardClass',
    unnumber: 'unNumber',
    lengthmm: 'lengthMm',
    widthmm: 'widthMm',
    heightmm: 'heightMm',
    weightkg: 'weightKg',
    stackable: 'stackable',
    maxstackweightkg: 'maxStackWeightKg',
    quantity: 'quantity'
  };

  const headerMap: Record<number, string> = {};
  for (let i = 0; i < header.length; i++) {
    const col = header[i];
    if (expectedFields[col]) {
      headerMap[i] = expectedFields[col];
    }
  }

  if (Object.keys(headerMap).length < 6) {
    return {
      totalRows: 0,
      validRowsCount: 0,
      invalidRowsCount: 0,
      rows: [],
      globalErrors: ['Missing required CSV headers. Expected headers: cargoItemId, description, hazardClass, unNumber, lengthMm, widthMm, heightMm, weightKg, stackable, maxStackWeightKg, quantity']
    };
  }

  const existingIds = new Set<string>();
  const parsedRows: ParsedCargoRow[] = [];

  for (let r = 1; r < rawRows.length; r++) {
    const cells = rawRows[r];
    const rowObj: any = {
      stackable: true,
      maxStackWeightKg: 0,
      quantity: 1
    };

    for (let c = 0; c < cells.length; c++) {
      const field = headerMap[c];
      if (field) {
        let val: any = cells[c];
        if (field === 'stackable') {
          val = val.toLowerCase() === 'true' || val === '1' || val.toLowerCase() === 'yes';
        } else if (['lengthMm', 'widthMm', 'heightMm', 'weightKg', 'maxStackWeightKg', 'quantity'].includes(field)) {
          val = Number(val);
        }
        rowObj[field] = val;
      }
    }

    const item: CargoItemInput = {
      cargoItemId: rowObj.cargoItemId || '',
      description: rowObj.description || '',
      hazardClass: String(rowObj.hazardClass || ''),
      unNumber: String(rowObj.unNumber || '').toUpperCase(),
      lengthMm: Number(rowObj.lengthMm),
      widthMm: Number(rowObj.widthMm),
      heightMm: Number(rowObj.heightMm),
      weightKg: Number(rowObj.weightKg),
      stackable: Boolean(rowObj.stackable),
      maxStackWeightKg: Number(rowObj.maxStackWeightKg || 0),
      quantity: Number(rowObj.quantity || 1)
    };

    const errors = validateCargoItem(item, r + 1, existingIds);
    if (item.cargoItemId) {
      existingIds.add(item.cargoItemId.trim().toLowerCase());
    }

    parsedRows.push({
      rowNumber: r + 1,
      item,
      errors,
      isValid: errors.length === 0
    });
  }

  return {
    totalRows: parsedRows.length,
    validRowsCount: parsedRows.filter(r => r.isValid).length,
    invalidRowsCount: parsedRows.filter(r => !r.isValid).length,
    rows: parsedRows,
    globalErrors: []
  };
}

/**
 * Parses and validates JSON content.
 */
export function parseAndValidateJson(jsonText: string): ImportParseResult {
  let rawData: any;
  try {
    rawData = JSON.parse(jsonText);
  } catch (err: any) {
    return {
      totalRows: 0,
      validRowsCount: 0,
      invalidRowsCount: 0,
      rows: [],
      globalErrors: [`JSON Syntax Error: ${err.message}`]
    };
  }

  const itemsArray = Array.isArray(rawData) ? rawData : (rawData.items || rawData.cargoItems);
  if (!Array.isArray(itemsArray) || itemsArray.length === 0) {
    return {
      totalRows: 0,
      validRowsCount: 0,
      invalidRowsCount: 0,
      rows: [],
      globalErrors: ['JSON input must be an array of cargo items or an object with an "items" array']
    };
  }

  const existingIds = new Set<string>();
  const parsedRows: ParsedCargoRow[] = [];

  itemsArray.forEach((rawItem: any, idx: number) => {
    const rowNum = idx + 1;
    const item: CargoItemInput = {
      cargoItemId: String(rawItem.cargoItemId || rawItem.id || ''),
      description: String(rawItem.description || ''),
      hazardClass: String(rawItem.hazardClass || ''),
      unNumber: String(rawItem.unNumber || '').toUpperCase(),
      lengthMm: Number(rawItem.lengthMm),
      widthMm: Number(rawItem.widthMm),
      heightMm: Number(rawItem.heightMm),
      weightKg: Number(rawItem.weightKg),
      stackable: rawItem.stackable !== undefined ? Boolean(rawItem.stackable) : true,
      maxStackWeightKg: Number(rawItem.maxStackWeightKg || 0),
      quantity: Number(rawItem.quantity || 1)
    };

    const errors = validateCargoItem(item, rowNum, existingIds);
    if (item.cargoItemId) {
      existingIds.add(item.cargoItemId.trim().toLowerCase());
    }

    parsedRows.push({
      rowNumber: rowNum,
      item,
      errors,
      isValid: errors.length === 0
    });
  });

  return {
    totalRows: parsedRows.length,
    validRowsCount: parsedRows.filter(r => r.isValid).length,
    invalidRowsCount: parsedRows.filter(r => !r.isValid).length,
    rows: parsedRows,
    globalErrors: []
  };
}

export const SAMPLE_CSV_TEMPLATE = `cargoItemId,description,hazardClass,unNumber,lengthMm,widthMm,heightMm,weightKg,stackable,maxStackWeightKg,quantity
ITEM-SOLVENT-01,Industrial Solvent Alpha,3,UN1993,1200,1000,1000,500,true,1000,1
ITEM-ACID-02,Hydrochloric Acid Solution,8,UN1789,1000,800,900,350,false,0,1
ITEM-EXPLOSIVE-03,Detonator Assembly Pack,1,UN0004,800,800,800,250,true,500,1`;

export const SAMPLE_JSON_TEMPLATE = JSON.stringify([
  {
    cargoItemId: 'ITEM-SOLVENT-01',
    description: 'Industrial Solvent Alpha',
    hazardClass: '3',
    unNumber: 'UN1993',
    lengthMm: 1200,
    widthMm: 1000,
    heightMm: 1000,
    weightKg: 500,
    stackable: true,
    maxStackWeightKg: 1000,
    quantity: 1
  },
  {
    cargoItemId: 'ITEM-ACID-02',
    description: 'Hydrochloric Acid Solution',
    hazardClass: '8',
    unNumber: 'UN1789',
    lengthMm: 1000,
    widthMm: 800,
    heightMm: 900,
    weightKg: 350,
    stackable: false,
    maxStackWeightKg: 0,
    quantity: 1
  }
], null, 2);
