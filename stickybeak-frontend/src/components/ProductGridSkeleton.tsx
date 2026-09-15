/** 商品网格骨架屏 */
export default function ProductGridSkeleton({ count = 8 }: { count?: number }) {
  return (
    <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
      {Array.from({ length: count }).map((_, i) => (
        <div
          key={i}
          className="animate-pulse overflow-hidden rounded-xl border border-sand-200 bg-white"
        >
          <div className="aspect-square bg-sand-100" />
          <div className="space-y-2 p-3">
            <div className="h-4 w-3/4 rounded bg-sand-100" />
            <div className="h-4 w-1/3 rounded bg-sand-100" />
          </div>
        </div>
      ))}
    </div>
  );
}
