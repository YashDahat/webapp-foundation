import { useState } from 'react';
import { useAdminSupportThread, useAdminReply } from '@/hooks/supportHooks';
import type { SupportStatus } from '@/types/support';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Badge } from '@/components/ui/badge';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { SupportAttachmentInput, SupportAttachmentThumbs } from '@/components/support/SupportAttachmentInput';

const STATUSES: SupportStatus[] = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'];
const STATUS_LABEL: Record<SupportStatus, string> = {
  OPEN: 'Open',
  IN_PROGRESS: 'In progress',
  RESOLVED: 'Resolved',
  CLOSED: 'Closed',
};

/** Admin helpdesk detail panel: the full thread + a status dropdown + a reply box (emails the customer). */
export function SupportThreadPanel({ caseId }: { caseId: number }) {
  const { data: thread, isLoading } = useAdminSupportThread(caseId);
  const reply = useAdminReply(caseId);
  const [body, setBody] = useState('');
  const [status, setStatus] = useState<SupportStatus | ''>('');
  const [attachments, setAttachments] = useState<string[]>([]);

  if (isLoading || !thread) {
    return <div className="py-8 text-sm text-muted-foreground">Loading thread…</div>;
  }

  const effectiveStatus = (status || thread.supportCase.status) as SupportStatus;

  const send = (e: React.FormEvent) => {
    e.preventDefault();
    if (!body.trim()) return;
    reply.mutate(
      { body: body.trim(), status: status || undefined, attachments },
      { onSuccess: () => { setBody(''); setStatus(''); setAttachments([]); } },
    );
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3">
        <div className="min-w-0">
          <h2 className="truncate text-lg font-semibold">#{thread.supportCase.id} · {thread.supportCase.subject}</h2>
          <p className="text-sm text-muted-foreground">
            {thread.supportCase.customerName || 'Unknown'} · {thread.supportCase.customerEmail || 'no email'}
          </p>
        </div>
        <Badge variant="secondary">{STATUS_LABEL[thread.supportCase.status]}</Badge>
      </div>

      <div className="max-h-[420px] space-y-3 overflow-y-auto rounded-md border p-3">
        {thread.messages.length === 0 ? (
          <p className="text-sm text-muted-foreground">No messages.</p>
        ) : (
          thread.messages.map((m) => {
            const staff = m.author === 'STAFF';
            return (
              <div key={m.id} className={`flex ${staff ? 'justify-end' : 'justify-start'}`}>
                <div className={`max-w-[80%] rounded-lg px-3 py-2 text-sm ${staff ? 'bg-primary text-primary-foreground' : 'bg-muted'}`}>
                  <div className="mb-0.5 text-xs opacity-70">{staff ? 'You (support)' : 'Customer'} · {new Date(m.createdAt).toLocaleString()}</div>
                  <div className="whitespace-pre-wrap">{m.body}</div>
                  <SupportAttachmentThumbs attachments={m.attachments} />
                </div>
              </div>
            );
          })
        )}
      </div>

      <form onSubmit={send} className="space-y-3">
        <Textarea placeholder="Reply to the customer…" rows={3} value={body} onChange={(e) => setBody(e.target.value)} />
        <SupportAttachmentInput value={attachments} onChange={setAttachments} />
        <div className="flex items-center gap-3">
          <Select value={effectiveStatus} onValueChange={(v) => setStatus(v as SupportStatus)}>
            <SelectTrigger className="w-44">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {STATUSES.map((s) => (
                <SelectItem key={s} value={s}>{STATUS_LABEL[s]}</SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button type="submit" disabled={reply.isPending || !body.trim()}>
            {reply.isPending ? 'Sending…' : 'Send reply'}
          </Button>
        </div>
      </form>
    </div>
  );
}
