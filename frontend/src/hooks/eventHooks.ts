// React Query hooks over the event SDK. Mutations wrap withToast for success feedback; request
// failures auto-toast via the apiClient interceptor (never add a manual toast.error).
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { eventService } from '@/services/eventService';
import { withToast } from '@/lib/apiToast';
import type { EventDto, EventFilter, UpsertEventRequest } from '@/types/event';

// ── Public ──
export function useEvents(filter: EventFilter = {}) {
  return useQuery<EventDto[]>({
    queryKey: ['events', filter],
    queryFn: () => eventService.list(filter),
  });
}

export function useEvent(id: number | null) {
  return useQuery<EventDto>({
    queryKey: ['event', id],
    queryFn: () => eventService.get(id as number),
    enabled: id != null,
  });
}

export function useEventBySlug(slug: string | null) {
  return useQuery<EventDto>({
    queryKey: ['event', 'slug', slug],
    queryFn: () => eventService.getBySlug(slug as string),
    enabled: !!slug,
  });
}

// ── Admin ──
export function useAdminEvents() {
  return useQuery<EventDto[]>({ queryKey: ['admin-events'], queryFn: eventService.adminList });
}

export function useCreateEvent() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: UpsertEventRequest) => withToast(eventService.create(req), 'Event created'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-events'] }),
  });
}

export function useUpdateEvent() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: number; req: UpsertEventRequest }) =>
      withToast(eventService.update(id, req), 'Event updated'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-events'] }),
  });
}

export function useDeleteEvent() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => withToast(eventService.remove(id), 'Event deleted'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-events'] }),
  });
}
