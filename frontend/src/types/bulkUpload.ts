/** Summary returned by every admin bulk-upload endpoint (mirrors backend BulkUploadResult). */
export interface BulkUploadRowError {
  /** 1-based row number as it appears in the file (data rows start at 2, after the header). */
  row: number;
  message: string;
}

export interface BulkUploadResult {
  created: number;
  skipped: number;
  failed: number;
  errors: BulkUploadRowError[];
}
