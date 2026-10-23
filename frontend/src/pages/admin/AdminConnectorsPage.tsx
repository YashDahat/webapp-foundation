import { useConnectors, useUpdateConnector } from '@/hooks/workflowHooks';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Switch } from '@/components/ui/switch';

/**
 * Admin Connectors: list registered connectors with their declared actions and toggle enable/disable.
 * Secrets are configured via env and shown as such — never rendered or edited here.
 */
export default function AdminConnectorsPage() {
  const { data: connectors, isLoading } = useConnectors();
  const update = useUpdateConnector();

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold">Connectors</h1>
      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : !connectors || connectors.length === 0 ? (
        <p className="text-sm text-muted-foreground">No connectors registered.</p>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2">
          {connectors.map((c) => (
            <Card key={c.id}>
              <CardHeader className="flex flex-row items-center justify-between space-y-0">
                <div>
                  <CardTitle className="text-base">{c.displayName}</CardTitle>
                  <p className="text-xs text-muted-foreground">{c.id} · {c.category}</p>
                </div>
                <Switch
                  checked={c.enabled}
                  disabled={update.isPending}
                  onCheckedChange={(enabled) => update.mutate({ id: c.id, req: { enabled } })}
                />
              </CardHeader>
              <CardContent className="space-y-2">
                <div className="flex flex-wrap gap-1">
                  {c.actions.map((a) => (
                    <Badge key={a.code} variant="outline">{a.code}</Badge>
                  ))}
                </div>
                <p className="text-xs text-muted-foreground">Secrets (API keys) are configured via env.</p>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
