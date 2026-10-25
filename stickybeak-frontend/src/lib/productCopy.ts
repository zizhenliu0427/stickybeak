import type { Locale } from './i18n';

/**
 * 商品资料目前只有中文。英文界面用这份对照，避免英文页面里冒出中文标题。
 * key 是在售 SKU 的 slug（见 scripts/sku-review/canonical-skus.json）；新上架的商品记得补一条。
 */
const EN: Record<string, { name: string; description: string }> = {
  // supermarket
  'coles-special-coke-2-for-520': {
    name: 'Coles Special · Coke No Sugar 2 for $5.20',
    description: 'The red shelf ticket, printed: Coca-Cola Zero Sugar soft drink, any 2 for $5.20.',
  },
  'coles-half-price-connoisseur-6': {
    name: 'Coles ½ Price · Connoisseur $6',
    description: 'Matcha Green Tea & White Choc. Was $12, save $6.',
  },
  'coles-down-down-olive-oil': {
    name: 'Coles Down Down · Olive Oil $16',
    description: 'Classic Mild Olive Oil, 1 litre, $16. Only in Australia.',
  },
  'coles-reduced-to-clear-milk': {
    name: 'Coles Reduced to Clear · Milk $3.10',
    description: 'Reduced Fat Milk, 2 litres, $3.10 — the yellow sticker you went looking for.',
  },
  'woolies-quick-sale-roast-chicken': {
    name: 'Woolies Quick Sale · Roast Chicken $6.38',
    description: 'Country Style Roast Chicken, marked down to $6.38. Dinner, sorted.',
  },
  'woolies-clearance-tote': {
    name: 'Woolies Clearance · Tote Bag $2',
    description: 'Classic Tote Bag, $2, save $0. The ticket is the joke.',
  },
  'woolies-special-ginger-beer': {
    name: 'Woolies Special · Ginger Beer $5.20',
    description: 'Brewed Ginger Beer, 4 × 375 mL, $5.20.',
  },
  'woolies-half-price-tim-tam': {
    name: 'Woolies ½ Price · Tim Tam $2.50',
    description: "Arnott's Tim Tam Original 200 g at $2.50. Time to stock up.",
  },

  // bus-sign
  'bus-unsw-2031164': {
    name: 'UNSW bus stop 2031164',
    description: '370 Coogee, 390X Bondi Junction, 348 Prince of Wales Hospital, 392 Little Bay.',
  },
  'bus-monash-university': {
    name: 'Monash University bus stop',
    description: 'Routes 862, 733, 900 and 601.',
  },
  'bus-anu-stop-4529': {
    name: 'ANU bus stop 4529',
    description: 'ACT Government stop 4529: R3, R4, R7, R10, R2, R6.',
  },
  'bus-uq-lakes-stop-a': {
    name: 'UQ Lakes, Stop A',
    description: 'TransLink blue: M2, 169, 179, 29.',
  },
  'bus-uq-lakes-stop-b': {
    name: 'UQ Lakes, Stop B · purple',
    description: 'TransLink purple: 66, 139, M2, 169.',
  },
  'bus-uq-chancellors-place': {
    name: "UQ Chancellor's Place bus stop",
    description: 'Zone D: 412, 411, 428, 414.',
  },
  'metro-macquarie-uni': {
    name: 'Macquarie University Metro sign',
    description: 'The station name board, with the Metro line strip underneath.',
  },
  'bus-macquarie-centre': {
    name: 'Macquarie Centre bus stop 211316',
    description: 'Routes 197, 410, 550 and 611.',
  },
  'bus-usyd-city-rd-370': {
    name: 'USYD City Rd stop · 370 edition',
    description: 'Stop 205038: 461X Burwood, 370 Coogee, 428, 430.',
  },
  'bus-usyd-city-rd-423': {
    name: 'USYD City Rd stop · original routes',
    description: 'Stop 205038 with the older route set: 423, 426, 428, 430.',
  },
  'bus-uts-broadway': {
    name: 'UTS Broadway bus stop 2022117',
    description: '412 Martin Place, 440 Bondi Junction, 430 All Destinations.',
  },

  // train
  'train-t9-gordon': {
    name: 'Sydney Trains · T9 Gordon via Central',
    description: 'The next-train board, stopped on the service you were waiting for.',
  },
  'train-t9-hornsby': {
    name: 'Sydney Trains · T9 Hornsby via Central',
    description: 'The next-train board, stopped on the service you were waiting for.',
  },
  'train-t9-wynyard-late': {
    name: 'Sydney Trains · T9 Wynyard, running late',
    description: 'T9 Wynyard via Central. Departs in 3 hr 30 min.',
  },
  'train-t8-city-circle': {
    name: 'Sydney Trains · T8 City Circle via Airport',
    description: 'Next train: T8 City Circle via the Airport line.',
  },
  'train-t8-macarthur': {
    name: 'Sydney Trains · T8 Macarthur via Airport',
    description: 'Next train: T8 Macarthur via the Airport line.',
  },
  'train-t7-olympic-park': {
    name: 'Sydney Trains · T7 Olympic Park, Tay Tay Express',
    description: 'The concert special. On the board: Accor, Giant, Qudos, Engie.',
  },
  'train-t2-city-circle': {
    name: 'Sydney Trains · T2 City Circle via Museum',
    description: 'The next-train board, stopped on the service you were waiting for.',
  },

  // tram
  'tram-unimelb-6-1': {
    name: 'UniMelb tram stop · Routes 6 & 1',
    description: 'Swanston St. Route 6 Flinders Street, Route 1 State Library.',
  },
  'tram-unimelb-72-16': {
    name: 'UniMelb tram stop · Routes 72 & 16',
    description: 'Swanston St. Route 72 Flinders Street, Route 16 Town Hall.',
  },

  // bird
  'bird-no-bin-chicken': {
    name: 'NO BIN CHICKEN',
    description: 'A white prohibition sign for the ibis with its head in the bin.',
  },
  'bird-do-not-feed-gull': {
    name: 'DO NOT FEED · seagull',
    description: 'Yellow warning triangle: a silver gull halfway through a chip heist.',
  },
  'bird-pelican-parking': {
    name: 'PELICAN PARKING',
    description: "Blue parking sign: 8:30–5:30 Mon–Sun, fish free. Printed 'PALICAN', just like that.",
  },
  'bird-no-stopping-cockatoo': {
    name: 'NO STOPPING · cockatoo',
    description: 'Red road sign: a sulphur-crested cockatoo making off with a croissant.',
  },

  // road-sign
  'sign-alcohol-free-area': {
    name: 'ALCOHOL FREE AREA',
    description: 'The white council sign, shrunk for the fridge door.',
  },
  'sign-wrong-way-go-back': {
    name: 'WRONG WAY GO BACK',
    description: 'The red wrong-way sign from every freeway off-ramp.',
  },
  'sign-speed-will-be-checked': {
    name: 'YOUR SPEED WILL BE CHECKED',
    description: 'The speed-camera warning sign.',
  },
  'sign-kangaroo-warning': {
    name: 'Kangaroo warning sign',
    description: 'The yellow diamond with the leaping roo.',
  },
  'btn-crossing-blue': {
    name: 'Pedestrian crossing button · blue',
    description: 'Blue plate, white arrow, black housing with the round button.',
  },
  'btn-crossing-white': {
    name: 'Pedestrian crossing button · white',
    description: 'The same button in the white-arrow colourway.',
  },

  // achievement
  'ach-fare-evasion': {
    name: 'Student achievement · Fare Dodger',
    description: 'Skipped the fare on public transport. 70.6% of players have this achievement.',
  },
  'ach-bargain-hunter': {
    name: 'Student achievement · Bargain Hunter',
    description: 'Bought only marked-down groceries for a week straight. 49.8% of players have this achievement.',
  },
  'ach-rookie-chef': {
    name: 'Student achievement · Rookie Chef',
    description: 'Set off the smoke alarm. 86.4% of players have this achievement.',
  },
  'ach-muddy-waters': {
    name: 'Student achievement · Mixed Bag',
    description:
      'Slipped some ginger in with the potatoes at the self-serve scales. 25.6% of players have this achievement.',
  },
  'ach-empty-room': {
    name: 'Student achievement · Hard Mode Start',
    description:
      'Moved into a room with no furniture, power, water or internet. 10.5% of players have this achievement.',
  },

  // slogan
  'slogan-no-worries': {
    name: 'NO WORRIES',
    description: 'Two words in mismatched coloured letters. The national motto, more or less.',
  },
};

export function productName(slug: string, fallback: string, locale: Locale): string {
  if (locale === 'en-AU') return EN[slug]?.name ?? fallback;
  return fallback;
}

export function productDescription(slug: string, fallback: string, locale: Locale): string {
  if (locale === 'en-AU') return EN[slug]?.description ?? fallback;
  return fallback;
}
