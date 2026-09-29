import { useState } from 'react';
import { useMyCases, useSupportThread, useOpenCase, usePostMessage } from '@/hooks/supportHooks';
import type { SupportCaseDto } from '@/types/support';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { SupportAttachmentInput, SupportAttachmentThumbs } from '@/components/support/SupportAttachmentInput';

const STATUS_LABEL: Record<string, string> = {
  OPEN: 'Open',
  IN_PROGRESS: 'In progress',
  RESOLVED: 'Resolved',
  CLOSED: 'Closed',
};

/** Customer support: open a ticket, list your own tickets, and exchange messages in a thread. */
export default function SupportPage() {
  const { data: cases, isLoading } = useMyCases();
  const [selectedId, setSelectedId] = useState<number | null>(null);

  return (
    <div className="mx-auto max-w-5xl px-4 py-8">
      <h1 className="mb-6 text-2xl font-bold">Support</h1>
      <div className="grid gap-6 md:grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)]">
        <div className="space-y-6">
          <NewCaseForm onCreated={setSelectedId} />
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Your requests</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2">
              {isLoading ? (
                <p className="text-sm text-muted-foreground">Loading…</p>
              ) : cases && cases.length > 0 ? (
                cases.map((c) => <CaseRow key={c.id} c={c} active={c.id === selectedId} onClick={() => setSelectedId(c.id)} />)
              ) : (
                <p className="text-sm text-muted-foreground">No requests yet.</p>
              )}
            </CardContent>
          </Card>
        </div>
        <div>{selectedId != null ? <CustomerThread caseId={selectedId} /> : <EmptyThread />}</div>
      </div>
    </div>
  );
}

function CaseRow({ c, active, onClick }: { c: SupportCaseDto; active: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex w-full items-center justify-between rounded-md border px-3 py-2 text-left text-sm hover:bg-muted ${active ? 'border-primary bg-muted' : ''}`}
    >
      <span className="truncate pr-2">#{c.id} · {c.subject}</span>
      <Badge variant="secondary">{STATUS_LABEL[c.status] ?? c.status}</Badge>
    </button>
  );
}

function NewCaseForm({ onCreated }: { onCreated: (id: number) => void }) {
  const [subject, setSubject] = useState('');
  const [message, setMessage] = useState('');
  const [attachments, setAttachments] = useState<string[]>([]);
  const open = useOpenCase();

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!subject.trim() || !message.trim()) return;
    open.mutate(
      { subject: subject.trim(), message: message.trim(), attachments },
      {
        onSuccess: (created) => {
          setSubject('');
          setMessage('');
          setAttachments([]);
          onCreated(created.id);
        },
      },
    );
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">New request</CardTitle>
      </CardHeader>
      <CardContent>
        <form onSubmit={submit} className="space-y-3">
          <Input placeholder="Subject" value={subject} onChange={(e) => setSubject(e.target.value)} />
          <Textarea placeholder="How can we help?" rows={4} value={message} onChange={(e) => setMessage(e.target.value)} />
          <SupportAttachmentInput value={attachments} onChange={setAttachments} />
          <Button type="submit" disabled={open.isPending || !subject.trim() || !message.trim()}>
            {open.isPending ? 'Submitting…' : 'Submit request'}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}

function CustomerThread({ caseId }: { caseId: number }) {
  const { data: thread, isLoading } = useSupportThread(caseId);
  const [body, setBody] = useState('');
  const [attachments, setAttachments] = useState<string[]>([]);
  const post = usePostMessage(caseId);

  const send = (e: React.FormEvent) => {
    e.preventDefault();
    if (!body.trim()) return;
    post.mutate({ body: body.trim(), attachments }, { onSuccess: () => { setBody(''); setAttachments([]); } });
  };

  if (isLoading || !thread) {
    return (
      <Card>
        <CardContent className="py-8 text-sm text-muted-foreground">Loading thread…</CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">
          #{thread.supportCase.id} · {thread.supportCase.subject}
          <Badge variant="secondary" className="ml-2">{STATUS_LABEL[thread.supportCase.status] ?? thread.supportCase.status}</Badge>
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <MessageList messages={thread.messages} />
        <form onSubmit={send} className="space-y-2">
          <Textarea placeholder="Write a reply…" rows={3} value={body} onChange={(e) => setBody(e.target.value)} />
          <SupportAttachmentInput value={attachments} onChange={setAttachments} />
          <Button type="submit" disabled={post.isPending || !body.trim()}>
            {post.isPending ? 'Sending…' : 'Send reply'}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}

function MessageList({ messages }: { messages: { id: number; author: string; body: string; attachments: string[]; createdAt: string }[] }) {
  if (messages.length === 0) return <p className="text-sm text-muted-foreground">No messages yet.</p>;
  return (
    <div className="max-h-[420px] space-y-3 overflow-y-auto pr-1">
      {messages.map((m) => {
        const staff = m.author === 'STAFF';
        return (
          <div key={m.id} className={`flex ${staff ? 'justify-start' : 'justify-end'}`}>
            <div className={`max-w-[80%] rounded-lg px-3 py-2 text-sm ${staff ? 'bg-muted' : 'bg-primary text-primary-foreground'}`}>
              <div className="mb-0.5 text-xs opacity-70">{staff ? 'Support' : 'You'} · {new Date(m.createdAt).toLocaleString()}</div>
              <div className="whitespace-pre-wrap">{m.body}</div>
              <SupportAttachmentThumbs attachments={m.attachments} />
            </div>
          </div>
        );
      })}
    </div>
  );
}

function EmptyThread() {
  return (
    <Card>
      <CardContent className="py-12 text-center text-sm text-muted-foreground">
        Select a request to view the conversation, or submit a new one.
      </CardContent>
    </Card>
  );
}
