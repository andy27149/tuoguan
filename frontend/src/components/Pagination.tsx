interface PaginationProps {
  page: number
  totalPages: number
  totalItems: number
  onPageChange: (page: number) => void
}

export function Pagination({ page, totalPages, totalItems, onPageChange }: PaginationProps) {
  return (
    <div className="flex items-center justify-between gap-2 pt-3 text-sm text-[#7c7391]">
      <span>
        共 {totalItems} 条 · 第 {page} 页 / 共 {totalPages} 页
      </span>
      <span className="flex gap-2">
        <button
          type="button"
          onClick={() => onPageChange(page - 1)}
          disabled={page <= 1}
          className="rounded-full border border-[#ece7de] px-3 py-1 text-xs text-[#5d5480] hover:bg-[#faf7ff] disabled:opacity-50"
        >
          上一页
        </button>
        <button
          type="button"
          onClick={() => onPageChange(page + 1)}
          disabled={page >= totalPages}
          className="rounded-full border border-[#ece7de] px-3 py-1 text-xs text-[#5d5480] hover:bg-[#faf7ff] disabled:opacity-50"
        >
          下一页
        </button>
      </span>
    </div>
  )
}
