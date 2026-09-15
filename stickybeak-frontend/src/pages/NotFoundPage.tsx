import { Link } from 'react-router-dom';

export default function NotFoundPage() {
  return (
    <div className="py-16 text-center">
      <p className="text-2xl font-semibold">404</p>
      <Link to="/" className="text-blue-600 hover:underline">
        Back home
      </Link>
    </div>
  );
}
