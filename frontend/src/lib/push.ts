import { notifications } from '@/api/endpoints';

export function pushSupported(): boolean {
  return typeof window !== 'undefined' && 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
}

function urlBase64ToUint8Array(base64: string): Uint8Array {
  const padding = '='.repeat((4 - (base64.length % 4)) % 4);
  const raw = atob((base64 + padding).replace(/-/g, '+').replace(/_/g, '/'));
  return Uint8Array.from(raw, (c) => c.charCodeAt(0));
}

export async function currentSubscription(): Promise<PushSubscription | null> {
  if (!pushSupported()) return null;
  const reg = await navigator.serviceWorker.getRegistration();
  return (await reg?.pushManager.getSubscription()) ?? null;
}

/**
 * Web Push opt-in. Requires an explicit user gesture; on iOS (16.4+) only
 * works when the app is installed to the home screen. Email stays the
 * guaranteed channel either way.
 */
export async function enablePush(): Promise<boolean> {
  if (!pushSupported()) return false;
  const permission = await Notification.requestPermission();
  if (permission !== 'granted') return false;
  const reg = await navigator.serviceWorker.ready;
  const keyResponse = await notifications.vapidKey();
  const key = typeof keyResponse === 'string' ? keyResponse : keyResponse.publicKey;
  const sub = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: urlBase64ToUint8Array(key) });
  const json = sub.toJSON() as { endpoint: string; keys: { p256dh: string; auth: string } };
  await notifications.subscribe({ endpoint: json.endpoint, keys: json.keys });
  return true;
}

export async function disablePush(): Promise<void> {
  const sub = await currentSubscription();
  if (!sub) return;
  await notifications.unsubscribe(sub.endpoint);
  await sub.unsubscribe();
}
