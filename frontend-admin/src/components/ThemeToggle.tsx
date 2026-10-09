import { MoonIcon, SunIcon } from '@phosphor-icons/react'
import { useTheme } from '../theme/ThemeContext'

export default function ThemeToggle({ className = '' }: { className?: string }) {
  const { theme, toggleTheme } = useTheme()
  const label = theme === 'dark' ? 'Chuyển sang giao diện sáng' : 'Chuyển sang giao diện tối'
  return (
    <button type="button" className={`icon-btn theme-toggle ${className}`} onClick={toggleTheme} aria-label={label} title={label}>
      {theme === 'dark' ? <SunIcon size={20} aria-hidden /> : <MoonIcon size={20} aria-hidden />}
    </button>
  )
}
