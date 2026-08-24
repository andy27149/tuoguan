import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { ParentSharePage } from './pages/ParentSharePage.tsx'
import { resolveShareToken } from './shareRoute.ts'

const shareToken = resolveShareToken(window.location.pathname)

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    {shareToken ? <ParentSharePage token={shareToken} /> : <App />}
  </StrictMode>,
)
