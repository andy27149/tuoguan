import { Component, type ReactNode } from 'react'

interface ErrorBoundaryProps {
  children: ReactNode
  onReset?: () => void
  resetLabel?: string
}

interface ErrorBoundaryState {
  hasError: boolean
}

// 兜底保险：任何未预料到的渲染异常（例如某个选填字段为空触发的空值访问）
// 只会让这一块界面显示出错提示，而不是让整个应用白屏。
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { hasError: false }

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true }
  }

  componentDidCatch(error: unknown, info: unknown) {
    console.error('页面渲染出错', error, info)
  }

  handleReset = () => {
    this.setState({ hasError: false })
    if (this.props.onReset) {
      this.props.onReset()
    } else {
      window.location.reload()
    }
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="flex min-h-screen flex-col items-center justify-center gap-3 bg-gray-50 px-4 text-center">
          <p className="text-base font-medium text-gray-700">页面出了点问题</p>
          <p className="text-sm text-gray-400">请重试，如果问题持续出现，请联系管理员</p>
          <button type="button" onClick={this.handleReset} className="btn-primary">
            {this.props.resetLabel ?? '刷新页面'}
          </button>
        </div>
      )
    }
    return this.props.children
  }
}
