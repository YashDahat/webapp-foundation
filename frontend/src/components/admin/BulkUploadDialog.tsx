import { useRef, useState } from 'react';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Download, Upload } from 'lucide-react';
import { toast } from 'sonner';
import type { BulkUploadResult } from '@/types/bulkUpload';

interface BulkUploadDialogProps {
  /** Trigger button label. */
  buttonLabel?: string;
  /** Dialog title, e.g. "Bulk upload products". */
  title: string;
  /** Column headers for the downloadable template (first is usually the required key). */
  columns: string[];
  /** Optional example row shown in the template to guide formatting. */
  sampleRow?: string[];
  /** Filename for the downloaded template. */
  templateName: string;
  /** Uploads the file and returns the import summary (call your POST .../bulk-upload service here). */
  onUpload: (file: File) => Promise<BulkUploadResult>;
}

/**
 * Admin bulk-upload dialog: download a CSV template, pick a .csv/.xlsx file, upload it, and show the
 * BulkUploadResult summary (created/skipped/failed + per-row errors). Admin-only.
 *
 * Foundation toast rule: request failures already auto-toast via the apiClient interceptor, so this
 * does NOT toast.error on a failed upload (that would double-toast); it surfaces the success/partial
 * summary toast instead.
 */
export function BulkUploadDialog({
  buttonLabel = 'Bulk upload',
  title,
  columns,
  sampleRow,
  templateName,
  onUpload,
}: BulkUploadDialogProps): React.JSX.Element {
  const [open, setOpen] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<BulkUploadResult | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const reset = () => {
    setFile(null);
    setResult(null);
    setBusy(false);
    if (inputRef.current) inputRef.current.value = '';
  };

  const handleOpenChange = (next: boolean) => {
    setOpen(next);
    if (!next) reset();
  };

  const downloadTemplate = () => {
    const rows = [columns.join(',')];
    if (sampleRow) rows.push(sampleRow.join(','));
    const blob = new Blob([rows.join('\n') + '\n'], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = templateName;
    a.click();
    URL.revokeObjectURL(url);
  };

  const handleUpload = async () => {
    if (!file) {
      toast.error('Please choose a .xlsx or .csv file first.');
      return;
    }
    setBusy(true);
    try {
      const res = await onUpload(file);
      setResult(res);
      if (res.failed > 0) {
        toast.warning(`Imported ${res.created}, skipped ${res.skipped}, ${res.failed} failed.`);
      } else {
        toast.success(`Imported ${res.created}, skipped ${res.skipped}.`);
      }
    } catch {
      // Request failures are surfaced by the apiClient response interceptor — do not double-toast here.
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <Button variant="outline" onClick={() => setOpen(true)} data-testid="bulk-upload-open">
        <Upload className="mr-2 h-4 w-4" />
        {buttonLabel}
      </Button>

      <Dialog open={open} onOpenChange={handleOpenChange}>
        <DialogContent className="sm:max-w-[520px]">
          <DialogHeader>
            <DialogTitle>{title}</DialogTitle>
            <DialogDescription>
              Upload a .xlsx or .csv file. Columns: {columns.join(', ')}. Existing names are skipped.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4 py-2">
            <Button variant="secondary" size="sm" onClick={downloadTemplate} data-testid="bulk-upload-template">
              <Download className="mr-2 h-4 w-4" />
              Download template
            </Button>

            <Input
              ref={inputRef}
              type="file"
              accept=".xlsx,.csv"
              onChange={(e) => { setFile(e.target.files?.[0] ?? null); setResult(null); }}
              data-testid="bulk-upload-file"
            />

            {result && (
              <div className="rounded-md border p-3 text-sm space-y-2" data-testid="bulk-upload-result">
                <div className="flex gap-4 font-medium">
                  <span className="text-green-600">Created: {result.created}</span>
                  <span className="text-muted-foreground">Skipped: {result.skipped}</span>
                  <span className={result.failed > 0 ? 'text-red-600' : 'text-muted-foreground'}>Failed: {result.failed}</span>
                </div>
                {result.errors.length > 0 && (
                  <ul className="max-h-40 overflow-y-auto list-disc list-inside text-red-600">
                    {result.errors.map((e, i) => (
                      <li key={i}>Row {e.row}: {e.message}</li>
                    ))}
                  </ul>
                )}
              </div>
            )}
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => handleOpenChange(false)}>Close</Button>
            <Button onClick={handleUpload} disabled={busy || !file} data-testid="bulk-upload-submit">
              {busy ? 'Uploading...' : 'Upload'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
