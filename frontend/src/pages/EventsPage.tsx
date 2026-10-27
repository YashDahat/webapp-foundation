import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useEvents } from '@/hooks/eventHooks';
import { useBranch } from '@/context/BranchContext';
import type { EventDto } from '@/types/event';
import { Card, CardContent, CardFooter } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';

/** "Fri, 14 Nov 2026 · 6:30 pm" style label for an event's start. */
export function formatEventDate(iso: string | null | undefined): string {
  if (!iso) return '';
  const d = new Date(iso);
  return d.toLocaleString(undefined, {
    weekday: 'short', day: 'numeric', month: 'short', year: 'numeric',
    hour: 'numeric', minute: '2-digit',
  });
}

function priceLabel(e: EventDto): string {
  if (e.tiers.length > 0) {
    const prices = e.tiers.map((t) => t.price ?? e.price);
    return `From ₹${Math.min(...prices)}`;
  }
  return e.price === 0 ? 'Free' : `₹${e.price}`;
}

/**
 * Public events grid: search + category filter over active events, soonest first. Each card shows the date,
 * venue, price and a "Sold out" badge; "View" opens the detail / sign-up page. Public page → shadcn cards.
 */
export default function EventsPage() {
  const { selectedBranchId } = useBranch();
  const { data: events, isLoading } = useEvents({ branchId: selectedBranchId ?? undefined });
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState<string | null>(null);

  const categories = useMemo(
    () => Array.from(new Set((events ?? []).map((e) => e.category).filter((c): c is string => !!c))).sort(),
    [events],
  );

  const visible = useMemo(() => {
    const q = search.trim().toLowerCase();
    return (events ?? []).filter((e) => {
      const matchesSearch = !q || e.name.toLowerCase().includes(q);
      const matchesCategory = !category || e.category === category;
      return matchesSearch && matchesCategory;
    });
  }, [events, search, category]);

  return (
    <div className="mx-auto max-w-6xl space-y-6 px-4 py-10">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <h1 className="text-2xl font-bold">Events</h1>
        <Input
          placeholder="Search events…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="sm:max-w-xs"
        />
      </div>

      {categories.length > 0 && (
        <div className="flex flex-wrap gap-2">
          <Badge
            variant={category === null ? 'default' : 'outline'}
            className="cursor-pointer"
            onClick={() => setCategory(null)}
          >
            All
          </Badge>
          {categories.map((c) => (
            <Badge
              key={c}
              variant={category === c ? 'default' : 'outline'}
              className="cursor-pointer"
              onClick={() => setCategory(c)}
            >
              {c}
            </Badge>
          ))}
        </div>
      )}

      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : visible.length === 0 ? (
        <p className="text-sm text-muted-foreground">No upcoming events.</p>
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {visible.map((e) => (
            <Card key={e.id} className="flex flex-col overflow-hidden">
              <Link to={`/events/${e.slug}`} className="block">
                {e.imageUrl ? (
                  <img src={e.imageUrl} alt={e.name} className="aspect-video w-full object-cover" />
                ) : (
                  <div className="flex aspect-video w-full items-center justify-center bg-primary/10 text-3xl font-black text-primary/40">
                    {e.name}
                  </div>
                )}
              </Link>
              <CardContent className="flex-1 space-y-1 p-4">
                <div className="flex items-center justify-between gap-2">
                  <Link to={`/events/${e.slug}`} className="font-medium hover:underline">
                    {e.name}
                  </Link>
                  {e.soldOut && <Badge variant="secondary">Sold out</Badge>}
                </div>
                <p className="text-xs text-muted-foreground">{formatEventDate(e.startsAt)}</p>
                {e.venueName && <p className="text-xs text-muted-foreground">📍 {e.venueName}</p>}
                <p className="font-semibold">{priceLabel(e)}</p>
              </CardContent>
              <CardFooter className="p-4 pt-0">
                <Button asChild variant={e.soldOut ? 'outline' : 'default'} className="w-full">
                  <Link to={`/events/${e.slug}`}>{e.soldOut ? 'View' : 'Get tickets'}</Link>
                </Button>
              </CardFooter>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
