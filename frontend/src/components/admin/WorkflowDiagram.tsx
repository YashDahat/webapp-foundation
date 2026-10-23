import { useMemo } from 'react';
import {
  ReactFlow, Background, Controls, Handle, Position, MarkerType,
  type Node, type Edge, type NodeProps,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import type { WorkflowDto, WorkflowStageDto } from '@/types/order';
import { Badge } from '@/components/ui/badge';

const CANCELLED = 'CANCELLED';

type StageNodeData = { stage: WorkflowStageDto };

/** A stage rendered as a graph node: label, lifecycle flags, cancellation policy, and bound actions. */
function StageNode({ data }: NodeProps<Node<StageNodeData>>) {
  const s = data.stage;
  const terminal = s.terminal;
  const ring = s.initial
    ? 'ring-2 ring-blue-400'
    : s.onPaidEntry
      ? 'ring-2 ring-emerald-400'
      : terminal && s.code === CANCELLED
        ? 'ring-2 ring-red-300'
        : '';
  return (
    <div className={`w-56 rounded-lg border bg-background p-3 text-left shadow-sm ${ring}`}>
      <Handle type="target" position={Position.Left} id="in" className="!bg-muted-foreground" />
      <Handle type="target" position={Position.Top} id="cancelin" className="!bg-red-400" />
      <div className="flex items-center justify-between gap-2">
        <span className="font-semibold">{s.label}</span>
        <div className="flex gap-1">
          {s.initial && <Badge variant="secondary" className="text-[10px]">initial</Badge>}
          {s.onPaidEntry && <Badge variant="secondary" className="text-[10px]">on paid</Badge>}
          {terminal && <Badge variant="outline" className="text-[10px]">terminal</Badge>}
        </div>
      </div>
      <p className="mt-1 text-[11px] text-muted-foreground">
        {s.cancellable ? `Cancellable · ${s.refundPolicy}${s.refundPolicy === 'PARTIAL' ? ` ${s.refundPercent ?? 0}%` : ''}` : 'Not cancellable'}
      </p>
      {s.bindings.length > 0 && (
        <div className="mt-2 flex flex-wrap gap-1">
          {s.bindings.map((b, i) => (
            <span
              key={i}
              className={`rounded px-1.5 py-0.5 text-[10px] ${b.enabled ? 'bg-muted' : 'bg-muted/50 text-muted-foreground line-through'}`}
            >
              {b.connectorId}:{b.actionCode}
            </span>
          ))}
        </div>
      )}
      <Handle type="source" position={Position.Right} id="out" className="!bg-muted-foreground" />
      <Handle type="source" position={Position.Bottom} id="cancel" className="!bg-red-400" />
    </div>
  );
}

const nodeTypes = { stage: StageNode };

interface WorkflowDiagramProps {
  workflow: WorkflowDto;
  onSelectStage?: (code: string) => void;
}

/**
 * Read-first visual of a workflow: the happy path flows left→right through the ordered stages; dashed red
 * edges show where an order can be cancelled (into the terminal CANCELLED stage), with that stage's refund
 * policy on the node. Makes the lifecycle easy to explain to clients; click a node to edit it.
 */
export default function WorkflowDiagram({ workflow, onSelectStage }: WorkflowDiagramProps) {
  const { nodes, edges } = useMemo(() => {
    const stages = [...workflow.stages].sort((a, b) => a.ordinal - b.ordinal);
    const path = stages.filter((s) => s.code !== CANCELLED);
    const cancelled = stages.find((s) => s.code === CANCELLED);

    const nodes: Node<StageNodeData>[] = path.map((s, i) => ({
      id: s.code,
      type: 'stage',
      position: { x: i * 280, y: 0 },
      data: { stage: s },
    }));
    if (cancelled) {
      nodes.push({
        id: CANCELLED,
        type: 'stage',
        position: { x: Math.max(0, (path.length - 1) * 280) / 2, y: 260 },
        data: { stage: cancelled },
      });
    }

    const edges: Edge[] = [];
    // Happy path: each stage → the next (skip edges out of a terminal stage).
    for (let i = 0; i < path.length - 1; i++) {
      if (path[i].terminal) continue;
      edges.push({
        id: `flow-${path[i].code}`,
        source: path[i].code, sourceHandle: 'out',
        target: path[i + 1].code, targetHandle: 'in',
        markerEnd: { type: MarkerType.ArrowClosed },
      });
    }
    // Cancel edges: from each cancellable, non-terminal stage → CANCELLED.
    if (cancelled) {
      path.filter((s) => s.cancellable && !s.terminal).forEach((s) => {
        edges.push({
          id: `cancel-${s.code}`,
          source: s.code, sourceHandle: 'cancel',
          target: CANCELLED, targetHandle: 'cancelin',
          label: 'cancel',
          style: { stroke: '#f87171', strokeDasharray: '5 4' },
          labelStyle: { fill: '#ef4444', fontSize: 10 },
          markerEnd: { type: MarkerType.ArrowClosed, color: '#f87171' },
        });
      });
    }
    return { nodes, edges };
  }, [workflow]);

  return (
    <div className="h-[520px] w-full rounded-lg border">
      <ReactFlow
        nodes={nodes}
        edges={edges}
        nodeTypes={nodeTypes}
        fitView
        proOptions={{ hideAttribution: true }}
        onNodeClick={(_, node) => onSelectStage?.(node.id)}
      >
        <Background />
        <Controls showInteractive={false} />
      </ReactFlow>
    </div>
  );
}
