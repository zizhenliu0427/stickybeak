import { useParams } from 'react-router-dom';

export default function ProductDetailPage() {
  const { slug } = useParams();
  return <div>TODO(Sprint 2): product detail for {slug}.</div>;
}
