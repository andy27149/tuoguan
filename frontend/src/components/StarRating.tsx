const STAR_PATH =
  'M10 1.6l2.53 5.4 5.87.58-4.4 4.03 1.26 5.79L10 14.4l-5.26 3-1.26-5.79-4.4-4.03 5.87-.58z'

const VALUES = [1, 2, 3, 4, 5]

interface StarRatingProps {
  value: number
  onChange?: (value: number) => void
  readOnly?: boolean
}

export function StarRating({ value, onChange, readOnly = false }: StarRatingProps) {
  const rating = value || 0

  if (readOnly) {
    return (
      <div className="star-rating star-rating--readonly">
        {VALUES.map((v) => (
          <span key={v} className={`star-rating__star${v <= rating ? ' is-filled' : ''}`}>
            <svg viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
              <path d={STAR_PATH} />
            </svg>
          </span>
        ))}
      </div>
    )
  }

  return (
    <div className="star-rating">
      {VALUES.map((v) => (
        <button
          key={v}
          type="button"
          className={`star-rating__btn${v <= rating ? ' is-filled' : ''}`}
          aria-pressed={v <= rating}
          aria-label={`评${v}星`}
          onClick={() => onChange?.(rating === v ? 0 : v)}
        >
          <svg viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
            <path d={STAR_PATH} />
          </svg>
        </button>
      ))}
    </div>
  )
}
