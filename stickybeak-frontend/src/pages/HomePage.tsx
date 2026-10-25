import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { PADDYS_MARKET } from '../lib/brand';
import { useI18n, type TranslationKey } from '../lib/i18n';

const DOORS = [
  { category: 'bus-sign', image: '/mock-products/aussie-uni-bus-stop-set/01.jpg' },
  { category: 'train', image: '/mock-products/sydney-t9-train-sign/01.jpg' },
  { category: 'bird', image: '/mock-products/chip-thief-seagull/01.jpg' },
] as const;

export default function HomePage() {
  const { t } = useI18n();

  useEffect(() => {
    const id = window.location.hash.replace('#', '');
    if (!id) return;
    const timer = window.setTimeout(() => {
      document.getElementById(id)?.scrollIntoView({ behavior: 'smooth' });
    }, 80);
    return () => window.clearTimeout(timer);
  }, []);

  const specs = [
    { title: t('home.craft1Title'), desc: t('home.craft1Desc') },
    { title: t('home.craft2Title'), desc: t('home.craft2Desc') },
    { title: t('home.craft3Title'), desc: t('home.craft3Desc') },
  ];

  return (
    <div>
      <section className="relative min-h-[calc(100svh-4.5rem)] bg-[#1c1916] text-[#f6f1e8]">
        <img
          src="/editorial/hero.jpg"
          alt=""
          className="absolute inset-0 h-full w-full object-cover object-[center_42%]"
        />
        <div className="absolute inset-0 bg-gradient-to-r from-black/65 via-black/25 to-transparent" />
        <div className="absolute inset-0 bg-gradient-to-t from-black/45 via-transparent to-black/15" />
        <div className="relative mx-auto flex min-h-[calc(100svh-4.5rem)] max-w-[1440px] flex-col justify-end px-6 pb-16 pt-28 sm:px-10 lg:px-16 lg:pb-20">
          <p className="eyebrow text-[#f6f1e8]/80">{t('hero.kicker')}</p>
          <p className="mt-3 text-[12px] tracking-[0.22em] text-[#f6f1e8]/70">{t('hero.est')}</p>
          <h1 className="font-display mt-6 max-w-3xl whitespace-pre-line text-5xl leading-[0.92] sm:text-7xl lg:text-8xl">
            {t('hero.display')}
          </h1>
          <p className="font-display mt-4 text-2xl tracking-wide sm:text-3xl">{t('hero.subtitle')}</p>
          <p className="mt-5 max-w-md text-sm font-light leading-7 text-[#f6f1e8]/85">{t('hero.lead')}</p>
          <div className="mt-8 flex flex-wrap gap-3">
            <Link to="/products" className="btn-cream">
              {t('nav.exploreCta')}
            </Link>
            <a href="#story" className="btn-ghost">
              {t('hero.secondary')}
            </a>
          </div>
        </div>
        <a
          href="#series"
          className="absolute bottom-8 right-6 hidden flex-col items-center text-[10px] tracking-[0.28em] text-[#f6f1e8]/80 sm:flex lg:right-12"
        >
          {t('hero.scroll')}
          <span className="mt-3 h-12 w-px bg-[#f6f1e8]/70" />
        </a>
      </section>

      <section id="series" className="tone-paper">
        <div className="mx-auto max-w-[1440px] px-6 py-20 sm:px-10 lg:px-16 lg:py-28">
          <div className="grid items-end gap-8 md:grid-cols-[1.15fr_0.85fr]">
            <div>
              <p className="eyebrow muted">{t('home.seriesIndex')}</p>
              <h2 className="font-display mt-4 text-4xl sm:text-6xl">{t('home.seriesTitle')}</h2>
            </div>
            <p className="max-w-sm text-sm leading-7 muted md:ml-auto md:text-right">{t('home.seriesBlurb')}</p>
          </div>

          <div className="mt-14 grid gap-8 sm:grid-cols-3">
            {DOORS.map((door, index) => {
              const catKey = `cat.${door.category}` as TranslationKey;
              return (
                <Link key={door.category} to={`/products?category=${door.category}`} className="group block">
                  <div className="aspect-[4/5] overflow-hidden bg-[#e7e0d2] dark:bg-[#221f1b]">
                    <img
                      src={door.image}
                      alt={t(catKey)}
                      className="h-full w-full object-cover transition duration-700 group-hover:scale-[1.03]"
                    />
                  </div>
                  <div className="mt-4 flex items-baseline justify-between text-[11px] tracking-[0.18em] uppercase muted">
                    <span>{String(index + 1).padStart(2, '0')}</span>
                    <span>{t(catKey)}</span>
                  </div>
                </Link>
              );
            })}
          </div>

          <div className="mt-16">
            <Link to="/products" className="border-b border-current pb-1 text-[12px] tracking-[0.22em]">
              {t('products.title')}
            </Link>
          </div>
        </div>
      </section>

      <section id="craft" className="bg-[#1b1916] text-[#f4efe6]">
        <div className="mx-auto grid max-w-[1440px] items-center gap-12 px-6 py-20 sm:px-10 lg:grid-cols-2 lg:gap-20 lg:px-16 lg:py-28">
          <div className="overflow-hidden bg-[#11100e]">
            <img
              src="/mock-products/chip-thief-seagull/01.jpg"
              alt=""
              className="aspect-[4/5] w-full object-cover object-[center_82%] sm:aspect-[5/4] lg:aspect-[4/5]"
            />
          </div>
          <div>
            <p className="eyebrow text-[#f4efe6]/55">{t('home.craftIndex')}</p>
            <h2 className="font-display mt-4 text-4xl leading-tight sm:text-6xl">{t('home.craftTitle')}</h2>
            <p className="mt-6 max-w-md text-sm font-light leading-7 text-[#f4efe6]/75">{t('home.craftDesc')}</p>
            <ul className="mt-10 border-t border-white/15">
              {specs.map((spec) => (
                <li key={spec.title} className="border-b border-white/15 py-5">
                  <p className="font-display text-2xl">{spec.title}</p>
                  <p className="mt-1 text-sm leading-6 text-[#f4efe6]/60">{spec.desc}</p>
                </li>
              ))}
            </ul>
          </div>
        </div>
      </section>

      <section id="story" className="tone-paper">
        <div className="mx-auto grid max-w-[1100px] gap-10 px-6 py-20 sm:px-10 md:grid-cols-12 lg:py-28">
          <div className="md:col-span-5">
            <p className="eyebrow muted">{t('home.storyIndex')}</p>
            <h2 className="font-display mt-4 text-4xl leading-tight sm:text-6xl">{t('home.storyTitle')}</h2>
          </div>
          <div className="md:col-span-6 md:col-start-7">
            <p className="font-display text-2xl leading-snug sm:text-3xl">{t('home.storyBody')}</p>
            <p className="mt-8 text-sm leading-7 muted">{t('home.storyNote')}</p>
          </div>
        </div>
      </section>

      <section id="visit" className="relative min-h-[72vh] bg-[#14120f] text-[#f6f1e8]">
        <img
          src="/editorial/closer.jpg"
          alt=""
          className="absolute inset-0 h-full w-full object-cover object-center"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/45 to-black/25" />
        <div className="relative mx-auto flex min-h-[72vh] max-w-3xl flex-col items-center justify-center px-6 py-24 text-center">
          <p className="eyebrow text-[#f6f1e8]/70">{t('home.closerKicker')}</p>
          <h2 className="font-display mt-5 whitespace-pre-line text-4xl leading-[1.05] sm:text-6xl">
            {t('home.closerTitle')}
          </h2>
          <p className="mt-5 max-w-md text-sm font-light leading-7 text-[#f6f1e8]/80">{t('home.closerDesc')}</p>
          <div className="mt-8 flex flex-wrap justify-center gap-3">
            <Link to="/products" className="btn-cream">
              {t('home.closerPrimary')}
            </Link>
            <a href={PADDYS_MARKET.mapsUrl} target="_blank" rel="noopener noreferrer" className="btn-ghost">
              {t('home.closerSecondary')}
            </a>
          </div>
        </div>
      </section>
    </div>
  );
}
