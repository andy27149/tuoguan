interface MascotProps {
  /** "greeting"：家长打开分享链接时的问候（单手挥手）；"celebrating"：当日任务
   * 全部完成时的庆祝（双手举起+星星点缀）。两种表情定义见 docs/logo.html。 */
  variant: 'greeting' | 'celebrating'
  /** 控制宽度，高度按原始 240:280 的比例自动换算。 */
  size?: number
  className?: string
}

// 吉祥物"小奕"（docs/logo.html）。颜色直接写死而不是读 CSS 变量，和 BrandMark.tsx
// 保持同一种做法——这几个十六进制色值就是 logo.html 色彩系统里定义的品牌紫系。
export function Mascot({ variant, size = 120, className }: MascotProps) {
  const height = Math.round((size * 280) / 240)
  const celebrating = variant === 'celebrating'

  return (
    <svg
      width={size}
      height={height}
      viewBox="0 0 240 280"
      aria-hidden="true"
      className={className}
      style={{ flexShrink: 0 }}
    >
      <ellipse cx="120" cy="165" rx="92" ry="90" fill="#9c8ee8" />
      <ellipse cx="85" cy="256" rx="22" ry="11" fill="#4e3fa8" />
      <ellipse cx="155" cy="256" rx="22" ry="11" fill="#4e3fa8" />
      {celebrating ? (
        <>
          <path d="M45 152 Q15 118 33 82" stroke="#4e3fa8" strokeWidth="16" strokeLinecap="round" fill="none" />
          <path d="M195 152 Q225 118 207 82" stroke="#4e3fa8" strokeWidth="16" strokeLinecap="round" fill="none" />
          <circle cx="33" cy="80" r="11" fill="#9c8ee8" />
          <circle cx="207" cy="80" r="11" fill="#9c8ee8" />
        </>
      ) : (
        <>
          <path d="M40 168 Q18 192 34 216" stroke="#4e3fa8" strokeWidth="16" strokeLinecap="round" fill="none" />
          <path d="M200 162 Q226 132 210 96" stroke="#4e3fa8" strokeWidth="16" strokeLinecap="round" fill="none" />
          <circle cx="210" cy="94" r="11" fill="#9c8ee8" />
        </>
      )}
      <path d="M120 76 C120 65 120 55 120 46" stroke="#4e3fa8" strokeWidth="7" strokeLinecap="round" fill="none" />
      <path d="M120 58 C108 58 99 50 101 38 C113 39 120 47 120 58 Z" fill="#4e3fa8" />
      <path d="M120 52 C131 51 140 43 138 31 C126 33 119 41 120 52 Z" fill="#6d5bd0" />
      <path d="M120 14 L124 21 L131 24 L124 27 L120 34 L116 27 L109 24 L116 21 Z" fill="#f0a93e" />
      {celebrating && (
        <path
          d="M90 138 L93 148 L83 152 L93 156 L90 166 L97 158 L107 162 L101 152 L107 142 L97 146 Z"
          fill="#f0a93e"
          opacity="0.9"
        />
      )}
      <circle cx="90" cy="150" r="10" fill="#241f3d" />
      <circle cx="150" cy="150" r="10" fill="#241f3d" />
      <circle cx="93" cy="146" r="3" fill="#ffffff" />
      <circle cx="153" cy="146" r="3" fill="#ffffff" />
      <circle cx="68" cy="175" r="12" fill="#f3a9be" opacity="0.7" />
      <circle cx="172" cy="175" r="12" fill="#f3a9be" opacity="0.7" />
      {celebrating ? (
        <path d="M88 176 Q120 202 152 176" stroke="#241f3d" strokeWidth="6" strokeLinecap="round" fill="none" />
      ) : (
        <path d="M95 178 Q120 194 145 178" stroke="#241f3d" strokeWidth="6" strokeLinecap="round" fill="none" />
      )}
    </svg>
  )
}
