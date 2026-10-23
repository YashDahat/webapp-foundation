import { useEffect, useMemo, useState } from 'react';
import { useConnectors, useReplaceWorkflow, useWorkflows } from '@/hooks/workflowHooks';
import type { RefundPolicy, WorkflowDto, WorkflowStageDto } from '@/types/order';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Badge } from '@/components/ui/badge';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import {
  Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle,
} from '@/components/ui/sheet';
import WorkflowDiagram from '@/components/admin/WorkflowDiagram';

const POLICIES: RefundPolicy[] = ['FULL', 'PARTIAL', 'NONE'];

/**
 * Admin workflow builder: pick a workflow and edit each stage's cancellation policy + connector bindings
 * (attach/detach/enable actions from the registry). Saving replaces the workflow's stages/bindings. Stage
 * codes are preserved so in-flight orders stay valid. (Adding/removing/reordering stages is a later add.)
 */
export default function AdminWorkflowPage() {
  const { data: workflows } = useWorkflows();
  const { data: connectors } = useConnectors();
  const replace = useReplaceWorkflow();

  const [code, setCode] = useState<string>('');
  const [draft, setDraft] = useState<WorkflowDto | null>(null);
  const [view, setView] = useState<'diagram' | 'editor'>('diagram');
  const [selectedCode, setSelectedCode] = useState<string | null>(null);

  // Default to the first workflow; reload the draft when the selection or source data changes.
  useEffect(() => {
    if (!workflows || workflows.length === 0) return;
    const selected = workflows.find((w) => w.code === code) ?? workflows[0];
    setCode(selected.code);
    setDraft(JSON.parse(JSON.stringify(selected)) as WorkflowDto);
  }, [workflows, code]);

  const actionsByConnector = useMemo(() => {
    const map: Record<string, { code: string; label: string }[]> = {};
    (connectors ?? []).forEach((c) => { map[c.id] = c.actions; });
    return map;
  }, [connectors]);

  if (!draft) {
    return <p className="text-sm text-muted-foreground">Loading…</p>;
  }

  const updateStage = (idx: number, patch: Partial<WorkflowStageDto>) => {
    setDraft((d) => d && ({ ...d, stages: d.stages.map((s, i) => (i === idx ? { ...s, ...patch } : s)) }));
  };

  const addBinding = (idx: number, connectorId: string, actionCode: string) => {
    updateStage(idx, {
      bindings: [
        ...draft.stages[idx].bindings,
        { connectorId, actionCode, ordinal: draft.stages[idx].bindings.length, enabled: true },
      ],
    });
  };

  const removeBinding = (idx: number, bIdx: number) => {
    updateStage(idx, { bindings: draft.stages[idx].bindings.filter((_, i) => i !== bIdx) });
  };

  const selectedIdx = draft.stages.findIndex((s) => s.code === selectedCode);
  const selectedStage = selectedIdx >= 0 ? draft.stages[selectedIdx] : null;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-4">
        <h1 className="text-2xl font-bold">Workflow</h1>
        <div className="flex items-center gap-2">
          <Select value={code} onValueChange={setCode}>
            <SelectTrigger className="w-48"><SelectValue /></SelectTrigger>
            <SelectContent>
              {(workflows ?? []).map((w) => (
                <SelectItem key={w.code} value={w.code}>{w.label}{w.default ? ' (default)' : ''}</SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button
            disabled={replace.isPending}
            onClick={() => replace.mutate({ code: draft.code, dto: draft })}
          >
            {replace.isPending ? 'Saving…' : 'Save workflow'}
          </Button>
        </div>
      </div>

      <Tabs value={view} onValueChange={(v) => setView(v as 'diagram' | 'editor')}>
        <TabsList>
          <TabsTrigger value="diagram">Diagram</TabsTrigger>
          <TabsTrigger value="editor">Editor</TabsTrigger>
        </TabsList>

        <TabsContent value="diagram" className="space-y-2">
          <WorkflowDiagram workflow={draft} onSelectStage={(c) => setSelectedCode(c)} />
          <div className="flex flex-wrap gap-4 text-xs text-muted-foreground">
            <span className="flex items-center gap-1"><span className="inline-block h-3 w-3 rounded ring-2 ring-blue-400" /> initial</span>
            <span className="flex items-center gap-1"><span className="inline-block h-3 w-3 rounded ring-2 ring-emerald-400" /> entered on payment</span>
            <span className="flex items-center gap-1"><span className="inline-block h-0.5 w-5 bg-red-400" style={{ borderTop: '2px dashed #f87171' }} /> cancellation path</span>
            <span>Click a stage to edit it.</span>
          </div>
        </TabsContent>

        <TabsContent value="editor" className="space-y-3">
          {draft.stages.map((stage, idx) => (
            <StageCard
              key={stage.code}
              stage={stage}
              connectorIds={Object.keys(actionsByConnector)}
              actionsByConnector={actionsByConnector}
              onPatch={(p) => updateStage(idx, p)}
              onAddBinding={(cid, ac) => addBinding(idx, cid, ac)}
              onToggleBinding={(bIdx, enabled) =>
                updateStage(idx, { bindings: stage.bindings.map((b, i) => (i === bIdx ? { ...b, enabled } : b)) })}
              onRemoveBinding={(bIdx) => removeBinding(idx, bIdx)}
            />
          ))}
        </TabsContent>
      </Tabs>

      {/* Right-side panel: edit the clicked stage's details in place. */}
      <Sheet open={selectedStage != null} onOpenChange={(open) => !open && setSelectedCode(null)}>
        <SheetContent className="w-full overflow-y-auto sm:max-w-md">
          {selectedStage && (
            <>
              <SheetHeader>
                <SheetTitle>Edit stage — {selectedStage.label}</SheetTitle>
                <SheetDescription>Changes apply when you Save the workflow.</SheetDescription>
              </SheetHeader>
              <div className="mt-4">
                <StageCard
                  stage={selectedStage}
                  connectorIds={Object.keys(actionsByConnector)}
                  actionsByConnector={actionsByConnector}
                  onPatch={(p) => updateStage(selectedIdx, p)}
                  onAddBinding={(cid, ac) => addBinding(selectedIdx, cid, ac)}
                  onToggleBinding={(bIdx, enabled) =>
                    updateStage(selectedIdx, { bindings: selectedStage.bindings.map((b, i) => (i === bIdx ? { ...b, enabled } : b)) })}
                  onRemoveBinding={(bIdx) => removeBinding(selectedIdx, bIdx)}
                />
              </div>
            </>
          )}
        </SheetContent>
      </Sheet>
    </div>
  );
}

function StageCard({
  stage, connectorIds, actionsByConnector, onPatch, onAddBinding, onToggleBinding, onRemoveBinding,
}: {
  stage: WorkflowStageDto;
  connectorIds: string[];
  actionsByConnector: Record<string, { code: string; label: string }[]>;
  onPatch: (p: Partial<WorkflowStageDto>) => void;
  onAddBinding: (connectorId: string, actionCode: string) => void;
  onToggleBinding: (bIdx: number, enabled: boolean) => void;
  onRemoveBinding: (bIdx: number) => void;
}) {
  const [newConnector, setNewConnector] = useState(connectorIds[0] ?? '');
  const actions = actionsByConnector[newConnector] ?? [];
  const [newAction, setNewAction] = useState(actions[0]?.code ?? '');

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between space-y-0">
        <CardTitle className="text-base">
          {stage.label} <span className="text-xs text-muted-foreground">({stage.code})</span>
        </CardTitle>
        <div className="flex gap-1">
          {stage.initial && <Badge variant="secondary">initial</Badge>}
          {stage.onPaidEntry && <Badge variant="secondary">on paid</Badge>}
          {stage.terminal && <Badge variant="outline">terminal</Badge>}
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        {/* Cancellation policy */}
        <div className="flex flex-wrap items-center gap-4">
          <div className="flex items-center gap-2">
            <Switch checked={stage.cancellable} onCheckedChange={(v) => onPatch({ cancellable: v })} />
            <Label>Cancellable</Label>
          </div>
          <div className="flex items-center gap-2">
            <Label className="text-sm">Refund</Label>
            <Select value={stage.refundPolicy} onValueChange={(v) => onPatch({ refundPolicy: v as RefundPolicy })}>
              <SelectTrigger className="w-32"><SelectValue /></SelectTrigger>
              <SelectContent>
                {POLICIES.map((p) => <SelectItem key={p} value={p}>{p}</SelectItem>)}
              </SelectContent>
            </Select>
          </div>
          {stage.refundPolicy === 'PARTIAL' && (
            <div className="flex items-center gap-2">
              <Label className="text-sm">%</Label>
              <Input
                type="number"
                className="w-20"
                value={stage.refundPercent ?? ''}
                onChange={(e) => onPatch({ refundPercent: e.target.value === '' ? null : Number(e.target.value) })}
              />
            </div>
          )}
        </div>

        {/* Bindings */}
        <div className="space-y-2">
          <p className="text-sm font-medium">Actions on entry</p>
          {stage.bindings.length === 0 && <p className="text-xs text-muted-foreground">No actions.</p>}
          {stage.bindings.map((b, bIdx) => (
            <div key={bIdx} className="flex items-center gap-2 rounded border p-2 text-sm">
              <Switch checked={b.enabled} onCheckedChange={(v) => onToggleBinding(bIdx, v)} />
              <span className="flex-1">{b.connectorId} : {b.actionCode}</span>
              <Button variant="ghost" size="sm" className="text-destructive" onClick={() => onRemoveBinding(bIdx)}>Remove</Button>
            </div>
          ))}
          <div className="flex items-end gap-2">
            <Select value={newConnector} onValueChange={(v) => { setNewConnector(v); setNewAction(actionsByConnector[v]?.[0]?.code ?? ''); }}>
              <SelectTrigger className="w-40"><SelectValue placeholder="Connector" /></SelectTrigger>
              <SelectContent>
                {connectorIds.map((id) => <SelectItem key={id} value={id}>{id}</SelectItem>)}
              </SelectContent>
            </Select>
            <Select value={newAction} onValueChange={setNewAction}>
              <SelectTrigger className="w-44"><SelectValue placeholder="Action" /></SelectTrigger>
              <SelectContent>
                {actions.map((a) => <SelectItem key={a.code} value={a.code}>{a.code}</SelectItem>)}
              </SelectContent>
            </Select>
            <Button variant="outline" size="sm" disabled={!newConnector || !newAction} onClick={() => onAddBinding(newConnector, newAction)}>
              Add
            </Button>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
