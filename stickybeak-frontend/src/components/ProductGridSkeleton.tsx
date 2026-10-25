/** 商品网格骨架屏 */
export default function ProductGridSkeleton({ count = 8 }: { count?: number }) {
  return (
    <div className="grid grid-cols-1 gap-x-6 gap-y-12 sm:grid-cols-2 lg:grid-cols-3">
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className="animate-pulse">
          <div className="aspect-[4/5] bg-[#e4dccb] dark:bg-[#221f1b]" />
          <div className="mt-4 h-6 w-2/3 bg-[#e4dccb] dark:bg-[#221f1b]" />
          <div className="mt-3 h-4 w-1/3 bg-[#e4dccb] dark:bg-[#221f1b]" />
        </div>
      ))}
    </div>
  );
}
