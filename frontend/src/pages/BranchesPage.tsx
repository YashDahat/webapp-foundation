import { useBranch } from '@/context/BranchContext';
import type { BranchDto } from '@/types/branch';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { MapPin } from 'lucide-react';

/** Best Google Maps link for a branch: its saved link, else a query by coordinates, else by address. */
function mapsHref(b: BranchDto): string | null {
  if (b.googleMapsUrl) return b.googleMapsUrl;
  if (b.latitude != null && b.longitude != null) {
    return `https://www.google.com/maps/search/?api=1&query=${b.latitude},${b.longitude}`;
  }
  const q = [b.name, b.addressLine, b.city, b.state, b.pincode].filter(Boolean).join(', ');
  return q ? `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(q)}` : null;
}

/** Public outlets page: pick the outlet to order from. Public page → shadcn cards, never ag-grid. */
export default function BranchesPage() {
  const { branches, selectedBranchId, selectBranch, loading } = useBranch();

  return (
    <div className="mx-auto max-w-4xl space-y-6 px-4 py-10">
      <h1 className="text-2xl font-bold">Our outlets</h1>
      {loading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : branches.length === 0 ? (
        <p className="text-sm text-muted-foreground">No outlets available yet.</p>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2">
          {branches.map((b) => {
            const selected = b.id === selectedBranchId;
            return (
              <Card key={b.id} className={selected ? 'ring-2 ring-primary' : ''}>
                <CardHeader className="flex flex-row items-center justify-between space-y-0">
                  <CardTitle className="text-base">{b.name}</CardTitle>
                  {selected && <Badge>Selected</Badge>}
                </CardHeader>
                <CardContent className="space-y-2 text-sm text-muted-foreground">
                  {b.addressLine && <p>{b.addressLine}</p>}
                  <p>{[b.city, b.state, b.pincode].filter(Boolean).join(', ')}</p>
                  {b.phone && <p>☎ {b.phone}</p>}
                  {b.openingHours && <p>🕒 {b.openingHours}</p>}
                  {b.distanceKm != null && <p>{b.distanceKm} km away</p>}
                  {mapsHref(b) && (
                    <a
                      href={mapsHref(b) as string}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="inline-flex items-center gap-1 text-sm text-primary hover:underline"
                    >
                      <MapPin size={14} /> View on Google Maps
                    </a>
                  )}
                  <Button
                    className="mt-2 w-full"
                    variant={selected ? 'secondary' : 'default'}
                    onClick={() => selectBranch(b.id)}
                  >
                    {selected ? 'Selected outlet' : 'Order from here'}
                  </Button>
                </CardContent>
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
}
