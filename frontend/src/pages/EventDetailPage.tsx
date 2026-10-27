import { useMemo, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { ArrowRight } from 'lucide-react';
import { toast } from 'sonner';
import { useEventBySlug } from '@/hooks/eventHooks';
import { useCart } from '@/cart';
import siteConfig from '@/config/siteConfig';
import type { EventDto, EventTierDto } from '@/types/event';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';

const MAX_PER_ORDER = 4;

function parts(iso: string | null | undefined) {
  if (!iso) return null;
  const d = new Date(iso);
  return {
    day: d.toLocaleString(undefined, { day: 'numeric' }),
    date: d.toLocaleString(undefined, { weekday: 'short', month: 'short', year: 'numeric' }),
    time: d.toLocaleString(undefined, { hour: 'numeric', minute: '2-digit' }),
  };
}

function closesLabel(iso: string | null | undefined): string | null {
  if (!iso) return null;
  const d = new Date(iso);
  if (d.getTime() <= Date.now()) return null;
  return d.toLocaleString(undefined, {
    weekday: 'short', day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit',
  });
}

/** The three "how it works" steps shown on the poster (free-event default copy). */
const STEPS = [
  { n: '01', title: 'Fill in the form', body: 'Choose how many seats you need.' },
  { n: '02', title: 'Confirm by email', body: 'We send a confirmation to hold your seats.' },
  { n: '03', title: 'Show up', body: 'Give your name at the door. No ticket to print.' },
];

/**
 * Event detail / sign-up: an editorial split — a poster-style event panel (brand-colour hero band, giant
 * title, date block, description, how-it-works) beside a "Reserve your seat" panel. A tier-less event picks
 * a guest count; a tiered event picks per-tier quantities. "Sign up" / "Get tickets" adds the seats to the
 * cart and continues to checkout (which collects contact + payment and runs the ₹0 / online order path).
 */
export default function EventDetailPage() {
  const { slug } = useParams<{ slug: string }>();
  const { data: event, isLoading, isError } = useEventBySlug(slug ?? null);
  const { addItem } = useCart();
  const navigate = useNavigate();

  if (isLoading) {
    return <p className="mx-auto max-w-5xl px-4 py-10 text-sm text-muted-foreground">Loading…</p>;
  }
  if (isError || !event) {
    return (
      <div className="mx-auto max-w-5xl px-4 py-10">
        <p className="text-sm text-muted-foreground">Event not found.</p>
        <Button asChild variant="link" className="px-0"><Link to="/events">← Back to events</Link></Button>
      </div>
    );
  }

  const start = parts(event.startsAt);
  const end = parts(event.endsAt);
  const closes = closesLabel(event.salesCloseAt ?? event.startsAt);
  const seatLabel = event.seatsLeft == null ? 'OPEN' : `${event.seatsLeft} SEATS`;
  const priceTag = event.tiers.length > 0
    ? `FROM ₹${Math.min(...event.tiers.map((t) => t.price ?? event.price))}`
    : (event.price === 0 ? 'FREE' : `₹${event.price}`);

  return (
    <div className="mx-auto grid max-w-6xl grid-cols-1 gap-0 px-0 md:grid-cols-[3fr_2fr]">
      {/* ── Left: event poster ── */}
      <div className="flex flex-col">
        <div className="flex items-center justify-between px-6 pt-6 text-xs font-bold uppercase tracking-widest text-muted-foreground">
          <span>{siteConfig.header.brandName} · Events</span>
          <span>{priceTag} · {seatLabel}</span>
        </div>

        <div className="mt-4 bg-primary px-6 py-12 text-primary-foreground">
          <h1 className="text-4xl font-black leading-[0.95] tracking-tight sm:text-6xl">{event.name}</h1>
        </div>

        {start && (
          <div className="flex items-stretch gap-4 border-b px-6 py-6">
            <DateBlock day={start.day} label="Starts" date={start.date} time={start.time} />
            {end && (
              <>
                <div className="flex items-center text-2xl text-muted-foreground">→</div>
                <DateBlock day={end.day} label="Ends" date={end.date} time={end.time} />
              </>
            )}
          </div>
        )}

        {event.description && (
          <p className="whitespace-pre-line px-6 py-6 text-lg leading-relaxed">{event.description}</p>
        )}

        <div className="grid grid-cols-1 gap-6 border-t px-6 py-6 sm:grid-cols-3">
          {STEPS.map((s) => (
            <div key={s.n}>
              <p className="text-sm font-bold text-primary">{s.n}</p>
              <p className="mt-1 font-semibold">{s.title}</p>
              <p className="mt-1 text-sm text-muted-foreground">{s.body}</p>
            </div>
          ))}
        </div>
      </div>

      {/* ── Right: reserve / tickets ── */}
      <div className="bg-muted px-6 py-10">
        {event.tiers.length > 0
          ? <TierPanel event={event} closes={closes} addItem={addItem} navigate={navigate} />
          : <GuestPanel event={event} closes={closes} addItem={addItem} navigate={navigate} />}
      </div>
    </div>
  );
}

function DateBlock({ day, label, date, time }: { day: string; label: string; date: string; time: string }) {
  return (
    <div className="flex items-center gap-3">
      <span className="text-5xl font-black leading-none">{day}</span>
      <div className="text-sm">
        <p className="font-bold uppercase tracking-wide text-primary">{label}</p>
        <p>{date}</p>
        <p className="font-semibold">{time}</p>
      </div>
    </div>
  );
}

type PanelProps = {
  event: EventDto;
  closes: string | null;
  addItem: ReturnType<typeof useCart>['addItem'];
  navigate: ReturnType<typeof useNavigate>;
};

/** Tier-less: pick a guest count, then sign up. */
function GuestPanel({ event, closes, addItem, navigate }: PanelProps) {
  const max = Math.min(MAX_PER_ORDER, event.seatsLeft ?? MAX_PER_ORDER);
  const [guests, setGuests] = useState(1);
  const disabled = event.soldOut || !event.onSale || max < 1;

  const signUp = () => {
    addItem({
      id: `EVENT:${event.id}`,
      name: event.name,
      unitPrice: event.price,
      imageUrl: event.imageUrl,
      metadata: { itemType: 'EVENT', itemId: event.id },
    }, guests);
    toast.success(`${guests} seat${guests > 1 ? 's' : ''} added`);
    navigate('/checkout');
  };

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-3xl font-bold">Reserve your seat</h2>
        {closes && <p className="mt-1 text-sm text-muted-foreground">Sign-up closes {closes}.</p>}
      </div>

      <div className="space-y-2">
        <p className="text-sm text-muted-foreground">Number of guests</p>
        <div className="grid grid-cols-4 overflow-hidden rounded-md border">
          {[1, 2, 3, 4].map((n) => {
            const nDisabled = n > max;
            return (
              <button
                key={n}
                type="button"
                disabled={nDisabled}
                onClick={() => setGuests(n)}
                className={cn(
                  'border-r py-3 text-sm font-semibold last:border-r-0 transition-colors',
                  guests === n ? 'bg-primary text-primary-foreground' : 'bg-background hover:bg-muted',
                  nDisabled && 'cursor-not-allowed opacity-40',
                )}
              >
                {n}
              </button>
            );
          })}
        </div>
      </div>

      <Button className="w-full justify-between" size="lg" disabled={disabled} onClick={signUp}>
        {event.soldOut ? 'Sold out' : !event.onSale ? 'Sign-up closed' : 'Sign up'}
        {!disabled && <ArrowRight className="h-4 w-4" />}
      </Button>
      {!disabled && event.price > 0 && (
        <p className="text-xs text-muted-foreground">You'll confirm contact details and payment at checkout.</p>
      )}
    </div>
  );
}

/** Tiered: per-tier quantity cards + running subtotal, then get tickets. */
function TierPanel({ event, closes, addItem, navigate }: PanelProps) {
  const [qty, setQty] = useState<Record<number, number>>({});
  const total = useMemo(
    () => event.tiers.reduce((sum, t) => sum + (qty[t.id] ?? 0) * (t.price ?? event.price), 0),
    [qty, event],
  );
  const count = Object.values(qty).reduce((a, b) => a + b, 0);
  const disabled = count < 1 || !event.onSale;

  const setTier = (t: EventTierDto, n: number) => {
    const capacity = t.available ?? MAX_PER_ORDER;
    const clamped = Math.max(0, Math.min(n, Math.min(capacity, MAX_PER_ORDER)));
    setQty((q) => ({ ...q, [t.id]: clamped }));
  };

  const getTickets = () => {
    event.tiers.forEach((t) => {
      const n = qty[t.id] ?? 0;
      if (n > 0) {
        addItem({
          id: `EVENT:${event.id}`,
          name: `${event.name} (${t.label})`,
          unitPrice: t.price ?? event.price,
          imageUrl: event.imageUrl,
          variantKey: String(t.id),
          metadata: { itemType: 'EVENT', itemId: event.id, variantLabel: t.label },
        }, n);
      }
    });
    toast.success(`${count} ticket${count > 1 ? 's' : ''} added`);
    navigate('/checkout');
  };

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-3xl font-bold">Get your tickets</h2>
        {closes && <p className="mt-1 text-sm text-muted-foreground">Sign-up closes {closes}.</p>}
      </div>

      <div className="space-y-3">
        {event.tiers.map((t) => {
          const soldOut = t.available != null && t.available <= 0;
          const n = qty[t.id] ?? 0;
          return (
            <div key={t.id} className="rounded-md border bg-background p-4">
              <div className="flex items-center justify-between">
                <div>
                  <p className="font-semibold">{t.label}</p>
                  <p className="text-xs text-muted-foreground">
                    {soldOut ? 'Sold out' : t.available != null ? `${t.available} left` : 'Available'}
                  </p>
                </div>
                <p className="font-semibold">₹{t.price ?? event.price}</p>
              </div>
              {!soldOut && (
                <div className="mt-3 flex items-center gap-3">
                  <Button type="button" variant="outline" size="icon" onClick={() => setTier(t, n - 1)}>–</Button>
                  <span className="w-6 text-center font-semibold">{n}</span>
                  <Button type="button" variant="outline" size="icon" onClick={() => setTier(t, n + 1)}>+</Button>
                </div>
              )}
            </div>
          );
        })}
      </div>

      <div className="flex items-center justify-between border-t pt-4 text-sm">
        <span className="text-muted-foreground">{count} ticket{count === 1 ? '' : 's'}</span>
        <span className="font-semibold">₹{total}</span>
      </div>

      <Button className="w-full justify-between" size="lg" disabled={disabled} onClick={getTickets}>
        {!event.onSale ? 'Sign-up closed' : 'Get tickets'}
        {!disabled && <ArrowRight className="h-4 w-4" />}
      </Button>
    </div>
  );
}
