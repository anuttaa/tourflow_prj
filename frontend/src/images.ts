/** Подбор обложки тура по названию направления. */
const DESTINATION_IMAGES: { match: string[]; url: string }[] = [
  { match: ['прага', 'богем', 'чехия'], url: '/tour-prague-painting.png' },
  { match: ['тоскан', 'флоренц', 'италия'], url: '/tour-tuscany-painting.png' },
  { match: ['доломит', 'альпы', 'больцано'], url: '/tour-dolomites-painting.png' },
  { match: ['стамбул', 'турц'], url: 'https://images.unsplash.com/photo-1524231757912-21f4fe3a7200?auto=format&fit=crop&w=900&q=80' },
  { match: ['барселон'], url: 'https://images.unsplash.com/photo-1583422409516-2895a77efded?auto=format&fit=crop&w=900&q=80' },
  { match: ['париж', 'франц'], url: 'https://images.unsplash.com/photo-1502602898657-3e91760cbb34?auto=format&fit=crop&w=900&q=80' },
  { match: ['рим'], url: 'https://images.unsplash.com/photo-1552832230-c0197dd311b5?auto=format&fit=crop&w=900&q=80' },
  { match: ['дубай', 'оаэ'], url: 'https://images.unsplash.com/photo-1512453979798-5ea266f8880c?auto=format&fit=crop&w=900&q=80' },
  { match: ['египет', 'каир'], url: 'https://images.unsplash.com/photo-1539650116574-8efeb43e2750?auto=format&fit=crop&w=900&q=80' },
  { match: ['грец', 'афин'], url: 'https://images.unsplash.com/photo-1570077188670-e3a8d69ac5ff?auto=format&fit=crop&w=900&q=80' },
  { match: ['токио', 'япон'], url: 'https://images.unsplash.com/photo-1503899036084-c55cdd92da26?auto=format&fit=crop&w=900&q=80' },
  { match: ['минск', 'беларус'], url: 'https://images.unsplash.com/photo-1568084680786-a84f91d1153c?auto=format&fit=crop&w=900&q=80' },
  { match: ['мальдив'], url: 'https://images.unsplash.com/photo-1514282401047-d79a71a590e8?auto=format&fit=crop&w=900&q=80' },
];

const FALLBACK = 'https://images.unsplash.com/photo-1488646953014-85cb44e25828?auto=format&fit=crop&w=900&q=80';

export function destinationImage(destination?: string): string {
  const d = (destination ?? '').toLowerCase();
  for (const { match, url } of DESTINATION_IMAGES) {
    if (match.some((k) => d.includes(k))) return url;
  }
  return FALLBACK;
}
