import { Link } from 'react-router-dom';

export default function NotFoundPage() {
  return (
    <div className="py-16 text-center">
      <p className="font-display text-6xl">404</p>
      <Link to="/" className="mt-4 inline-block text-sm tracking-[0.16em]">
        StickyBeak
      </Link>
    </div>
  );
}
