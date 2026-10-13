import { useEffect } from 'react'

// 全站弹窗共用的 Esc 关闭逻辑（产品诊断 #69）：此前好几个弹窗各自复制粘贴同一段
// keydown 监听，抽出来避免重复维护。
export function useEscapeToClose(onClose: () => void) {
  useEffect(() => {
    const onKeydown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKeydown)
    return () => document.removeEventListener('keydown', onKeydown)
  }, [onClose])
}
