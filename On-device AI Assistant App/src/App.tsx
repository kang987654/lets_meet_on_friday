import { useState, useEffect, useRef } from 'react'

/* ─── Types ─────────────────────────────────────────────────── */
type Screen = 'loading' | 'chat' | 'settings' | 'calendar' | 'schedule' | 'memory'

/* ─── Design tokens ──────────────────────────────────────────── */
const C = {
  bg: '#040D1F',
  surface: '#071526',
  glass: 'rgba(255,255,255,0.05)',
  glassMid: 'rgba(255,255,255,0.08)',
  glassHigh: 'rgba(255,255,255,0.12)',
  border: 'rgba(255,255,255,0.08)',
  borderHigh: 'rgba(255,255,255,0.15)',
  cyan: '#22D3EE',
  cyanDim: 'rgba(34,211,238,0.15)',
  cyanGlow: 'rgba(34,211,238,0.3)',
  violet: '#818CF8',
  violetDim: 'rgba(129,140,248,0.15)',
  success: '#34D399',
  successDim: 'rgba(52,211,153,0.15)',
  danger: '#F87171',
  dangerDim: 'rgba(248,113,113,0.15)',
  textPrimary: '#F1F5F9',
  textSecondary: '#94A3B8',
  textMuted: '#475569',
  amber: '#FBBF24',
}

/* ─── Shared style helpers ───────────────────────────────────── */
const glass = (extra?: object) => ({
  background: C.glass,
  backdropFilter: 'blur(20px)',
  WebkitBackdropFilter: 'blur(20px)',
  border: `1px solid ${C.border}`,
  ...extra,
})

const glassMid = (extra?: object) => ({
  background: C.glassMid,
  backdropFilter: 'blur(20px)',
  WebkitBackdropFilter: 'blur(20px)',
  border: `1px solid ${C.borderHigh}`,
  ...extra,
})

/* ─── Sub-components ─────────────────────────────────────────── */

function StatusBar() {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '12px 24px 8px', flexShrink: 0 }}>
      <span style={{ fontFamily: "'Exo 2', sans-serif", fontSize: 13, fontWeight: 600, color: C.textPrimary }}>9:41</span>
      <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
        {/* Signal bars */}
        <div style={{ display: 'flex', gap: 2, alignItems: 'flex-end', height: 12 }}>
          {[4, 6, 9, 12].map((h, i) => (
            <div key={i} style={{ width: 3, height: h, borderRadius: 1, background: i < 3 ? C.textPrimary : C.textMuted }} />
          ))}
        </div>
        {/* WiFi */}
        <svg width="16" height="12" viewBox="0 0 16 12" fill="none">
          <path d="M8 9.5a1 1 0 1 1 0 2 1 1 0 0 1 0-2z" fill={C.textPrimary} />
          <path d="M4.5 7.5C5.7 6.3 6.8 5.5 8 5.5s2.3.8 3.5 2" stroke={C.textPrimary} strokeWidth="1.4" strokeLinecap="round" />
          <path d="M1.5 4.5C3.3 2.7 5.5 1.5 8 1.5s4.7 1.2 6.5 3" stroke={C.textPrimary} strokeWidth="1.4" strokeLinecap="round" />
        </svg>
        {/* Battery */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <div style={{ width: 22, height: 11, borderRadius: 3, border: `1px solid ${C.textSecondary}`, padding: '1.5px', display: 'flex', alignItems: 'center' }}>
            <div style={{ width: '80%', height: '100%', borderRadius: 2, background: C.textPrimary }} />
          </div>
          <div style={{ width: 2, height: 5, borderRadius: '0 1px 1px 0', background: C.textSecondary }} />
        </div>
      </div>
    </div>
  )
}

/* ─── Screen A: Loading ──────────────────────────────────────── */
function LoadingScreen({ onDone }: { onDone: () => void }) {
  const [progress, setProgress] = useState(0)
  const [phase, setPhase] = useState(0)
  const phases = ['Initializing AI Core...', 'Loading Neural Weights...', 'Calibrating Memory Index...', 'Ready']

  useEffect(() => {
    const interval = setInterval(() => {
      setProgress(p => {
        const next = p + (Math.random() * 2.5 + 0.5)
        if (next >= 100) {
          clearInterval(interval)
          setTimeout(onDone, 600)
          return 100
        }
        setPhase(Math.floor(next / 25))
        return next
      })
    }, 80)
    return () => clearInterval(interval)
  }, [onDone])

  return (
    <div style={{ position: 'relative', width: '100%', height: '100%', background: C.bg, overflow: 'hidden', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
      {/* Aurora blobs */}
      <div style={{ position: 'absolute', inset: 0, overflow: 'hidden' }}>
        <div style={{ position: 'absolute', width: 400, height: 400, borderRadius: '50%', background: 'radial-gradient(circle, rgba(34,211,238,0.18) 0%, transparent 70%)', top: '10%', left: '-20%', animation: 'aurora 8s ease-in-out infinite' }} />
        <div style={{ position: 'absolute', width: 350, height: 350, borderRadius: '50%', background: 'radial-gradient(circle, rgba(129,140,248,0.15) 0%, transparent 70%)', bottom: '5%', right: '-15%', animation: 'aurora 10s ease-in-out infinite reverse' }} />
        <div style={{ position: 'absolute', width: 200, height: 200, borderRadius: '50%', background: 'radial-gradient(circle, rgba(34,211,238,0.1) 0%, transparent 70%)', top: '50%', left: '60%', animation: 'aurora 12s ease-in-out infinite' }} />
      </div>

      {/* Grid overlay */}
      <div style={{ position: 'absolute', inset: 0, backgroundImage: `linear-gradient(rgba(34,211,238,0.03) 1px, transparent 1px), linear-gradient(90deg, rgba(34,211,238,0.03) 1px, transparent 1px)`, backgroundSize: '40px 40px' }} />

      {/* Central orb */}
      <div style={{ position: 'relative', marginBottom: 48 }}>
        {/* Outer ring 1 */}
        <div style={{ position: 'absolute', inset: -32, borderRadius: '50%', border: '1px solid rgba(34,211,238,0.2)', animation: 'ring-spin 8s linear infinite' }}>
          <div style={{ position: 'absolute', top: '10%', left: '50%', width: 6, height: 6, borderRadius: '50%', background: C.cyan, boxShadow: `0 0 8px ${C.cyan}`, transform: 'translateX(-50%)' }} />
        </div>
        {/* Outer ring 2 */}
        <div style={{ position: 'absolute', inset: -52, borderRadius: '50%', border: '1px dashed rgba(129,140,248,0.2)', animation: 'ring-spin-reverse 12s linear infinite' }}>
          <div style={{ position: 'absolute', bottom: '15%', left: '50%', width: 4, height: 4, borderRadius: '50%', background: C.violet, boxShadow: `0 0 6px ${C.violet}`, transform: 'translateX(-50%)' }} />
        </div>
        {/* Outer ring 3 */}
        <div style={{ position: 'absolute', inset: -72, borderRadius: '50%', border: '1px solid rgba(34,211,238,0.08)', animation: 'ring-spin 20s linear infinite' }} />

        {/* Core orb */}
        <div style={{ width: 96, height: 96, borderRadius: '50%', background: `radial-gradient(circle at 35% 35%, rgba(34,211,238,0.9), rgba(34,211,238,0.4) 50%, rgba(129,140,248,0.6))`, animation: 'orb-pulse 3s ease-in-out infinite', display: 'flex', alignItems: 'center', justifyContent: 'center', boxShadow: `0 0 40px rgba(34,211,238,0.4), 0 0 80px rgba(34,211,238,0.15)` }}>
          {/* K logo */}
          <svg width="42" height="42" viewBox="0 0 42 42" fill="none">
            <path d="M10 7v28M10 21l14-14M10 21l14 14" stroke="white" strokeWidth="3.5" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </div>
      </div>

      {/* App name */}
      <div style={{ textAlign: 'center', marginBottom: 48, animation: 'fade-up 0.8s ease both' }}>
        <div style={{ fontFamily: "'Exo 2', sans-serif", fontSize: 32, fontWeight: 700, letterSpacing: '0.12em', color: C.textPrimary, textTransform: 'uppercase' }}>KOSMOS</div>
        <div style={{ fontSize: 12, fontWeight: 400, color: C.textMuted, letterSpacing: '0.2em', textTransform: 'uppercase', marginTop: 4 }}>On-Device AI Assistant</div>
      </div>

      {/* Progress section */}
      <div style={{ width: 240, animation: 'fade-up 0.8s 0.3s ease both', opacity: 0 }}>
        {/* Progress bar */}
        <div style={{ width: '100%', height: 2, background: 'rgba(255,255,255,0.08)', borderRadius: 2, overflow: 'hidden', marginBottom: 12 }}>
          <div style={{ position: 'relative', height: '100%', borderRadius: 2, background: `linear-gradient(90deg, ${C.violet}, ${C.cyan})`, width: `${progress}%`, transition: 'width 0.1s ease', overflow: 'hidden' }}>
            <div style={{ position: 'absolute', inset: 0, background: 'linear-gradient(90deg, transparent, rgba(255,255,255,0.5), transparent)', animation: 'progress-shimmer 1.5s ease infinite' }} />
          </div>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span style={{ fontSize: 12, color: C.textMuted, fontFamily: "'JetBrains Mono', monospace" }}>{phases[Math.min(phase, 3)]}</span>
          <span style={{ fontSize: 12, color: C.cyan, fontFamily: "'JetBrains Mono', monospace", fontWeight: 500 }}>{Math.round(progress)}%</span>
        </div>
      </div>
    </div>
  )
}

/* ─── Screen B: Chat ─────────────────────────────────────────── */
const MESSAGES = [
  { id: 1, role: 'ai', text: "Good morning. I've reviewed your calendar — you have a team standup at 10 AM and a product review at 3 PM. How can I assist you today?", time: '9:02 AM', thinking: false },
  { id: 2, role: 'user', text: "Can you add a reminder for my quarterly review prep? Let's say tomorrow at 2 PM.", time: '9:04 AM' },
  { id: 3, role: 'ai', text: "I'll schedule that for tomorrow at 2:00 PM — 'Q3 Review Prep'. I've also drafted a suggested agenda based on your recent notes. Shall I attach it?", time: '9:04 AM', thinking: false },
]

function ThinkingDots() {
  return (
    <div style={{ display: 'flex', gap: 5, padding: '10px 14px', alignItems: 'center' }}>
      {[0, 1, 2].map(i => (
        <div key={i} style={{ width: 6, height: 6, borderRadius: '50%', background: C.cyan, animation: `dot-bounce 1.4s ${i * 0.2}s ease-in-out infinite` }} />
      ))}
    </div>
  )
}

function ChatScreen({ onOpenSettings, onOpenCalendar }: { onOpenSettings: () => void; onOpenCalendar: () => void }) {
  const [messages, setMessages] = useState(MESSAGES)
  const [input, setInput] = useState('')
  const [thinking, setThinking] = useState(false)
  const [micActive, setMicActive] = useState(false)
  const bottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => { bottomRef.current?.scrollIntoView({ behavior: 'smooth' }) }, [messages, thinking])

  const sendMessage = () => {
    if (!input.trim()) return
    const userMsg = { id: Date.now(), role: 'user' as const, text: input, time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) }
    setMessages(m => [...m, userMsg])
    setInput('')
    setThinking(true)
    setTimeout(() => {
      setThinking(false)
      const aiReplies = [
        "Understood. I've processed your request and stored it in local memory — no data leaves this device.",
        "Done. Anything else I can help you with?",
        "I found 3 relevant items in your memory. Want me to summarize them?",
      ]
      setMessages(m => [...m, { id: Date.now() + 1, role: 'ai', text: aiReplies[Math.floor(Math.random() * aiReplies.length)], time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }), thinking: false }])
    }, 1800)
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: C.bg }}>
      {/* Aurora bg */}
      <div style={{ position: 'absolute', inset: 0, pointerEvents: 'none', overflow: 'hidden' }}>
        <div style={{ position: 'absolute', width: 300, height: 300, borderRadius: '50%', background: 'radial-gradient(circle, rgba(34,211,238,0.07) 0%, transparent 70%)', top: '-60px', right: '-60px' }} />
        <div style={{ position: 'absolute', width: 250, height: 250, borderRadius: '50%', background: 'radial-gradient(circle, rgba(129,140,248,0.06) 0%, transparent 70%)', bottom: '120px', left: '-40px' }} />
      </div>

      <StatusBar />

      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '8px 20px 16px', flexShrink: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div style={{ width: 36, height: 36, borderRadius: '50%', background: `radial-gradient(circle, ${C.cyan}, rgba(129,140,248,0.8))`, display: 'flex', alignItems: 'center', justifyContent: 'center', boxShadow: `0 0 16px ${C.cyanGlow}` }}>
            <svg width="16" height="16" viewBox="0 0 42 42" fill="none">
              <path d="M10 7v28M10 21l14-14M10 21l14 14" stroke="white" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </div>
          <div>
            <div style={{ fontFamily: "'Exo 2', sans-serif", fontSize: 16, fontWeight: 700, color: C.textPrimary, letterSpacing: '0.04em' }}>KOSMOS</div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 5, marginTop: 1 }}>
              <div style={{ width: 6, height: 6, borderRadius: '50%', background: C.success, boxShadow: `0 0 6px ${C.success}`, animation: 'glow-pulse 2s ease-in-out infinite' }} />
              <span style={{ fontSize: 11, color: C.success, fontFamily: "'JetBrains Mono', monospace" }}>On-device · Private</span>
            </div>
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button onClick={onOpenCalendar} style={{ width: 36, height: 36, borderRadius: '50%', ...glass(), display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', border: `1px solid ${C.border}` }}>
            <svg width="16" height="16" fill="none" stroke={C.textSecondary} strokeWidth="1.5" viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="18" rx="2" /><path d="M16 2v4M8 2v4M3 10h18" /></svg>
          </button>
          <button onClick={onOpenSettings} style={{ width: 36, height: 36, borderRadius: '50%', ...glass(), display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', border: `1px solid ${C.border}` }}>
            <svg width="16" height="16" fill="none" stroke={C.textSecondary} strokeWidth="1.5" viewBox="0 0 24 24"><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" /></svg>
          </button>
        </div>
      </div>

      {/* Messages */}
      <div style={{ flex: 1, overflowY: 'auto', padding: '0 16px', display: 'flex', flexDirection: 'column', gap: 12 }}>
        {/* Date chip */}
        <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 4 }}>
          <div style={{ padding: '4px 12px', borderRadius: 12, ...glass(), fontSize: 11, color: C.textMuted, fontFamily: "'JetBrains Mono', monospace" }}>Today, July 17</div>
        </div>

        {messages.map((msg) => (
          <div key={msg.id} style={{ display: 'flex', flexDirection: msg.role === 'user' ? 'row-reverse' : 'row', alignItems: 'flex-end', gap: 8, animation: 'fade-up 0.3s ease both' }}>
            {msg.role === 'ai' && (
              <div style={{ width: 28, height: 28, borderRadius: '50%', background: `radial-gradient(circle, ${C.cyan}, rgba(129,140,248,0.8))`, flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 16 }}>
                <svg width="12" height="12" viewBox="0 0 42 42" fill="none">
                  <path d="M10 7v28M10 21l14-14M10 21l14 14" stroke="white" strokeWidth="5" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
              </div>
            )}
            <div style={{ maxWidth: '78%' }}>
              <div style={{
                padding: '12px 14px',
                borderRadius: msg.role === 'user' ? '18px 18px 4px 18px' : '18px 18px 18px 4px',
                ...(msg.role === 'user'
                  ? { background: `linear-gradient(135deg, rgba(34,211,238,0.25), rgba(129,140,248,0.2))`, border: `1px solid rgba(34,211,238,0.25)`, backdropFilter: 'blur(16px)', WebkitBackdropFilter: 'blur(16px)' }
                  : glass({ borderRadius: msg.role === 'ai' ? '18px 18px 18px 4px' : undefined })),
                fontSize: 14,
                lineHeight: 1.55,
                color: C.textPrimary,
              }}>
                {msg.text}
              </div>
              <div style={{ fontSize: 10, color: C.textMuted, marginTop: 4, textAlign: msg.role === 'user' ? 'right' : 'left', fontFamily: "'JetBrains Mono', monospace" }}>{msg.time}</div>
            </div>
          </div>
        ))}

        {/* Thinking */}
        {thinking && (
          <div style={{ display: 'flex', alignItems: 'flex-end', gap: 8, animation: 'fade-up 0.3s ease both' }}>
            <div style={{ width: 28, height: 28, borderRadius: '50%', background: `radial-gradient(circle, ${C.cyan}, rgba(129,140,248,0.8))`, flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <svg width="12" height="12" viewBox="0 0 42 42" fill="none"><path d="M10 7v28M10 21l14-14M10 21l14 14" stroke="white" strokeWidth="5" strokeLinecap="round" strokeLinejoin="round" /></svg>
            </div>
            <div style={{ ...glass({ borderRadius: '18px 18px 18px 4px' }), overflow: 'hidden' }}>
              <ThinkingDots />
            </div>
          </div>
        )}
        <div ref={bottomRef} />
      </div>

      {/* Input bar */}
      <div style={{ padding: '12px 16px 8px', flexShrink: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, ...glass({ borderRadius: 28, padding: '8px 8px 8px 16px', border: `1px solid ${C.borderHigh}` }) }}>
          <input
            value={input}
            onChange={e => setInput(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && sendMessage()}
            placeholder="Ask Kosmos anything..."
            style={{ flex: 1, background: 'none', border: 'none', outline: 'none', fontSize: 14, color: C.textPrimary, fontFamily: "'Inter', sans-serif' " }}
          />
          {/* Mic button */}
          <button
            onClick={() => setMicActive(m => !m)}
            style={{ width: 40, height: 40, borderRadius: '50%', background: micActive ? `rgba(34,211,238,0.2)` : 'rgba(255,255,255,0.06)', border: `1px solid ${micActive ? C.cyan : C.border}`, display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', transition: 'all 0.2s', boxShadow: micActive ? `0 0 16px ${C.cyanGlow}` : 'none' }}>
            <svg width="16" height="16" fill="none" stroke={micActive ? C.cyan : C.textSecondary} strokeWidth="1.5" viewBox="0 0 24 24"><rect x="9" y="2" width="6" height="12" rx="3" /><path d="M5 10a7 7 0 0 0 14 0M12 19v3M9 22h6" /></svg>
          </button>
          {/* Send button */}
          <button
            onClick={sendMessage}
            style={{ width: 40, height: 40, borderRadius: '50%', background: input.trim() ? `linear-gradient(135deg, ${C.cyan}, ${C.violet})` : 'rgba(255,255,255,0.06)', border: 'none', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', transition: 'all 0.2s', boxShadow: input.trim() ? `0 0 16px ${C.cyanGlow}` : 'none' }}>
            <svg width="16" height="16" fill="none" stroke={input.trim() ? 'white' : C.textMuted} strokeWidth="2" viewBox="0 0 24 24"><path d="m22 2-11 11M22 2 15 22l-4-9-9-4 20-7z" /></svg>
          </button>
        </div>
      </div>
    </div>
  )
}

/* ─── Screen C: Settings ─────────────────────────────────────── */
function SettingsScreen({ onBack }: { onBack: () => void }) {
  const [responseStyle, setResponseStyle] = useState<'concise' | 'balanced' | 'detailed'>('balanced')
  const [downloadProgress] = useState(72)
  const [contextLen, setContextLen] = useState(4096)

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: C.bg, overflowY: 'auto' }}>
      <StatusBar />
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '8px 20px 20px', flexShrink: 0 }}>
        <button onClick={onBack} style={{ width: 36, height: 36, borderRadius: '50%', ...glass(), display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', border: `1px solid ${C.border}` }}>
          <svg width="16" height="16" fill="none" stroke={C.textSecondary} strokeWidth="2" viewBox="0 0 24 24"><path d="m15 18-6-6 6-6" /></svg>
        </button>
        <div>
          <div style={{ fontFamily: "'Exo 2', sans-serif", fontSize: 18, fontWeight: 700, color: C.textPrimary, letterSpacing: '0.04em' }}>Settings</div>
          <div style={{ fontSize: 11, color: C.textMuted }}>Model & Preferences</div>
        </div>
      </div>

      <div style={{ padding: '0 16px 32px', display: 'flex', flexDirection: 'column', gap: 16 }}>
        {/* AI Model section */}
        <div style={{ ...glass({ borderRadius: 20, padding: 20 }) }}>
          <div style={{ fontSize: 11, fontWeight: 600, letterSpacing: '0.12em', color: C.textMuted, textTransform: 'uppercase', marginBottom: 16, fontFamily: "'JetBrains Mono', monospace" }}>AI Model</div>

          {/* Active model */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 20 }}>
            <div style={{ width: 44, height: 44, borderRadius: 14, background: `linear-gradient(135deg, ${C.cyanDim}, ${C.violetDim})`, border: `1px solid rgba(34,211,238,0.2)`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <svg width="20" height="20" fill="none" stroke={C.cyan} strokeWidth="1.5" viewBox="0 0 24 24"><path d="M12 2a5 5 0 0 1 5 5 5 5 0 0 1-5 5 5 5 0 0 1-5-5 5 5 0 0 1 5-5zM3 21c0-4 4-7 9-7s9 3 9 7" /></svg>
            </div>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 15, fontWeight: 600, color: C.textPrimary, fontFamily: "'Exo 2', sans-serif" }}>Gemma 3n · 4B</div>
              <div style={{ fontSize: 12, color: C.textMuted, marginTop: 2 }}>INT4 Quantized · 2.1 GB</div>
            </div>
            <div style={{ padding: '4px 10px', borderRadius: 8, background: C.successDim, border: `1px solid rgba(52,211,153,0.2)`, fontSize: 11, color: C.success, fontFamily: "'JetBrains Mono', monospace" }}>Active</div>
          </div>

          {/* Download progress for second model */}
          <div style={{ ...glassMid({ borderRadius: 16, padding: 16 }) }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 10 }}>
              <div>
                <div style={{ fontSize: 14, fontWeight: 600, color: C.textPrimary }}>Llama 3.2 · 8B</div>
                <div style={{ fontSize: 11, color: C.textMuted, marginTop: 2 }}>High-quality reasoning model</div>
              </div>
              <div style={{ fontSize: 12, color: C.cyan, fontFamily: "'JetBrains Mono', monospace", fontWeight: 500 }}>{downloadProgress}%</div>
            </div>
            {/* Progress bar */}
            <div style={{ width: '100%', height: 4, background: 'rgba(255,255,255,0.06)', borderRadius: 4, overflow: 'hidden', marginBottom: 8 }}>
              <div style={{ position: 'relative', height: '100%', borderRadius: 4, background: `linear-gradient(90deg, ${C.violet}, ${C.cyan})`, width: `${downloadProgress}%`, overflow: 'hidden' }}>
                <div style={{ position: 'absolute', inset: 0, background: 'linear-gradient(90deg, transparent, rgba(255,255,255,0.4), transparent)', animation: 'progress-shimmer 1.8s ease infinite' }} />
              </div>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: C.textMuted, fontFamily: "'JetBrains Mono', monospace" }}>
              <span>3.1 GB / 4.3 GB</span>
              <span>↓ 2.4 MB/s · ~8m left</span>
            </div>
          </div>
        </div>

        {/* Response style */}
        <div style={{ ...glass({ borderRadius: 20, padding: 20 }) }}>
          <div style={{ fontSize: 11, fontWeight: 600, letterSpacing: '0.12em', color: C.textMuted, textTransform: 'uppercase', marginBottom: 16, fontFamily: "'JetBrains Mono', monospace" }}>Response Style</div>
          {(['concise', 'balanced', 'detailed'] as const).map(style => (
            <button
              key={style}
              onClick={() => setResponseStyle(style)}
              style={{ width: '100%', display: 'flex', alignItems: 'center', gap: 14, padding: '12px 14px', borderRadius: 14, marginBottom: 8, cursor: 'pointer', border: `1px solid ${responseStyle === style ? 'rgba(34,211,238,0.3)' : C.border}`, background: responseStyle === style ? `rgba(34,211,238,0.08)` : 'transparent', transition: 'all 0.2s' }}
            >
              <div style={{ width: 18, height: 18, borderRadius: '50%', border: `2px solid ${responseStyle === style ? C.cyan : C.textMuted}`, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                {responseStyle === style && <div style={{ width: 8, height: 8, borderRadius: '50%', background: C.cyan, boxShadow: `0 0 6px ${C.cyan}` }} />}
              </div>
              <div style={{ textAlign: 'left' }}>
                <div style={{ fontSize: 14, fontWeight: 500, color: responseStyle === style ? C.textPrimary : C.textSecondary, textTransform: 'capitalize' }}>{style}</div>
                <div style={{ fontSize: 11, color: C.textMuted, marginTop: 1 }}>
                  {style === 'concise' ? 'Short, direct answers' : style === 'balanced' ? 'Clear with context' : 'Thorough explanations'}
                </div>
              </div>
            </button>
          ))}
        </div>

        {/* Context length */}
        <div style={{ ...glass({ borderRadius: 20, padding: 20 }) }}>
          <div style={{ fontSize: 11, fontWeight: 600, letterSpacing: '0.12em', color: C.textMuted, textTransform: 'uppercase', marginBottom: 4, fontFamily: "'JetBrains Mono', monospace" }}>Context Window</div>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12, marginTop: 12 }}>
            <span style={{ fontSize: 14, color: C.textSecondary }}>Token limit</span>
            <span style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: 14, color: C.cyan, fontWeight: 500 }}>{contextLen.toLocaleString()}</span>
          </div>
          <input type="range" min={1024} max={8192} step={1024} value={contextLen} onChange={e => setContextLen(Number(e.target.value))}
            style={{ width: '100%', accentColor: C.cyan }} />
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 10, color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", marginTop: 6 }}>
            <span>1K</span><span>4K</span><span>8K</span>
          </div>
        </div>

        {/* Privacy & Data */}
        <div style={{ ...glass({ borderRadius: 20, padding: 20 }) }}>
          <div style={{ fontSize: 11, fontWeight: 600, letterSpacing: '0.12em', color: C.textMuted, textTransform: 'uppercase', marginBottom: 16, fontFamily: "'JetBrains Mono', monospace" }}>Privacy & Data</div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {[
              { label: 'Export Memory Backup', sub: 'Save encrypted .kosmos file', icon: '↑', color: C.cyan },
              { label: 'Import Backup', sub: 'Restore from .kosmos file', icon: '↓', color: C.violet },
              { label: 'Clear All Data', sub: 'Wipe memory and chat history', icon: '⌫', color: C.danger },
            ].map(item => (
              <button key={item.label} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '12px 14px', borderRadius: 14, border: `1px solid ${C.border}`, background: 'rgba(255,255,255,0.03)', cursor: 'pointer', transition: 'all 0.2s', width: '100%' }}>
                <div style={{ width: 36, height: 36, borderRadius: 12, background: `${item.color}18`, border: `1px solid ${item.color}30`, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 14, color: item.color }}>{item.icon}</div>
                <div style={{ textAlign: 'left' }}>
                  <div style={{ fontSize: 14, fontWeight: 500, color: C.textPrimary }}>{item.label}</div>
                  <div style={{ fontSize: 11, color: C.textMuted, marginTop: 1 }}>{item.sub}</div>
                </div>
              </button>
            ))}
          </div>
        </div>

        {/* Version */}
        <div style={{ textAlign: 'center', fontSize: 11, color: C.textMuted, fontFamily: "'JetBrains Mono', monospace" }}>
          Kosmos v1.0.0-beta · All data on-device
        </div>
      </div>
    </div>
  )
}

/* ─── Screen D: Calendar Action Card ────────────────────────── */
function CalendarCard({ onClose }: { onClose: () => void }) {
  return (
    <div style={{ position: 'absolute', inset: 0, zIndex: 50 }}>
      {/* Backdrop */}
      <div onClick={onClose} style={{ position: 'absolute', inset: 0, background: 'rgba(4,13,31,0.7)', backdropFilter: 'blur(8px)', WebkitBackdropFilter: 'blur(8px)' }} />

      {/* Partial chat preview */}
      <div style={{ position: 'absolute', top: 0, left: 0, right: 0, height: '45%', overflow: 'hidden', opacity: 0.6 }}>
        <div style={{ padding: '80px 16px 0', display: 'flex', flexDirection: 'column', gap: 10 }}>
          <div style={{ display: 'flex', gap: 8 }}>
            <div style={{ width: 28, height: 28, borderRadius: '50%', background: `radial-gradient(circle, ${C.cyan}, rgba(129,140,248,0.8))`, flexShrink: 0 }} />
            <div style={{ ...glass({ borderRadius: '18px 18px 18px 4px', padding: '10px 14px' }), fontSize: 13, color: C.textSecondary, maxWidth: '80%' }}>
              I can schedule the Q3 Review Prep for tomorrow at 2 PM. Would you like me to create this event?
            </div>
          </div>
        </div>
      </div>

      {/* Bottom sheet */}
      <div style={{ position: 'absolute', bottom: 0, left: 0, right: 0, animation: 'sheet-up 0.4s cubic-bezier(0.16, 1, 0.3, 1) both' }}>
        {/* Handle */}
        <div style={{ display: 'flex', justifyContent: 'center', paddingBottom: 8 }}>
          <div style={{ width: 36, height: 4, borderRadius: 2, background: 'rgba(255,255,255,0.2)' }} />
        </div>

        <div style={{ background: '#0A1628', borderRadius: '28px 28px 0 0', border: `1px solid ${C.borderHigh}`, borderBottom: 'none', padding: '24px 20px 36px', boxShadow: `0 -20px 60px rgba(0,0,0,0.6)` }}>
          {/* AI suggestion label */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 20 }}>
            <div style={{ width: 20, height: 20, borderRadius: '50%', background: `radial-gradient(circle, ${C.cyan}, rgba(129,140,248,0.8))`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <svg width="9" height="9" viewBox="0 0 42 42" fill="none"><path d="M10 7v28M10 21l14-14M10 21l14 14" stroke="white" strokeWidth="6" strokeLinecap="round" strokeLinejoin="round" /></svg>
            </div>
            <span style={{ fontSize: 12, color: C.textMuted, fontFamily: "'JetBrains Mono', monospace" }}>Kosmos suggests an event</span>
          </div>

          {/* Event card */}
          <div style={{ ...glassMid({ borderRadius: 20, padding: 20 }), marginBottom: 20, position: 'relative', overflow: 'hidden' }}>
            {/* Color accent bar */}
            <div style={{ position: 'absolute', left: 0, top: 0, bottom: 0, width: 3, background: `linear-gradient(180deg, ${C.cyan}, ${C.violet})`, borderRadius: '20px 0 0 20px' }} />

            <div style={{ paddingLeft: 12 }}>
              <div style={{ fontFamily: "'Exo 2', sans-serif", fontSize: 18, fontWeight: 700, color: C.textPrimary, marginBottom: 8 }}>Q3 Review Prep</div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <svg width="14" height="14" fill="none" stroke={C.cyan} strokeWidth="1.5" viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="18" rx="2" /><path d="M16 2v4M8 2v4M3 10h18" /></svg>
                  <span style={{ fontSize: 14, color: C.textSecondary }}>Tomorrow, July 18</span>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <svg width="14" height="14" fill="none" stroke={C.cyan} strokeWidth="1.5" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10" /><path d="M12 6v6l4 2" /></svg>
                  <span style={{ fontSize: 14, color: C.textSecondary }}>2:00 PM – 3:00 PM</span>
                </div>
                <div style={{ display: 'flex', alignItems: 'flex-start', gap: 8 }}>
                  <svg width="14" height="14" fill="none" stroke={C.textMuted} strokeWidth="1.5" viewBox="0 0 24 24" style={{ marginTop: 2 }}><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" /></svg>
                  <span style={{ fontSize: 13, color: C.textMuted, lineHeight: 1.5 }}>Based on chat context — prep materials for Q3 results presentation</span>
                </div>
              </div>
            </div>
          </div>

          {/* Action buttons */}
          <div style={{ display: 'flex', gap: 10 }}>
            <button onClick={onClose} style={{ flex: 1, padding: '14px', borderRadius: 16, border: `1px solid ${C.border}`, background: 'rgba(248,113,113,0.1)', color: C.danger, fontSize: 15, fontWeight: 600, cursor: 'pointer', fontFamily: "'Exo 2', sans-serif", transition: 'all 0.2s' }}>
              Reject
            </button>
            <button onClick={onClose} style={{ flex: 2, padding: '14px', borderRadius: 16, border: `1px solid rgba(34,211,238,0.3)`, background: `linear-gradient(135deg, rgba(34,211,238,0.2), rgba(129,140,248,0.2))`, color: C.cyan, fontSize: 15, fontWeight: 600, cursor: 'pointer', fontFamily: "'Exo 2', sans-serif", boxShadow: `0 0 20px ${C.cyanGlow}`, transition: 'all 0.2s' }}>
              Approve & Save
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

/* ─── Screen E: Schedule ─────────────────────────────────────── */
const DATES = ['14 Mon', '15 Tue', '16 Wed', '17 Thu', '18 Fri', '19 Sat', '20 Sun']
const TODAY_EVENTS = [
  { time: '10:00 AM', duration: '30m', title: 'Team Standup', type: 'meeting', color: C.cyan },
  { time: '2:00 PM', duration: '1h', title: 'Product Review', type: 'review', color: C.violet },
  { time: '4:30 PM', duration: '30m', title: 'Coffee Chat · Alex', type: 'personal', color: C.amber },
]
const UPCOMING_EVENTS = [
  { date: 'Tomorrow', time: '2:00 PM', title: 'Q3 Review Prep', color: C.cyan },
  { date: 'Fri, Jul 19', time: '10:00 AM', title: 'Design Sync', color: C.violet },
  { date: 'Mon, Jul 22', time: '9:00 AM', title: 'Sprint Planning', color: C.success },
  { date: 'Mon, Jul 22', time: '3:00 PM', title: 'Board Presentation', color: C.amber },
]

function ScheduleScreen() {
  const [selectedDate, setSelectedDate] = useState(3)

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: C.bg }}>
      <StatusBar />
      {/* Header */}
      <div style={{ padding: '8px 20px 16px', flexShrink: 0 }}>
        <div style={{ fontFamily: "'Exo 2', sans-serif", fontSize: 22, fontWeight: 700, color: C.textPrimary, letterSpacing: '0.03em' }}>Schedule</div>
        <div style={{ fontSize: 13, color: C.textMuted, marginTop: 2 }}>July 2026</div>
      </div>

      {/* Date strip */}
      <div style={{ display: 'flex', gap: 8, padding: '0 16px 20px', flexShrink: 0, overflowX: 'auto' }}>
        {DATES.map((d, i) => {
          const [num, day] = d.split(' ')
          const isSelected = i === selectedDate
          return (
            <button key={i} onClick={() => setSelectedDate(i)} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4, padding: '10px 14px', borderRadius: 16, border: `1px solid ${isSelected ? 'rgba(34,211,238,0.3)' : C.border}`, background: isSelected ? `linear-gradient(135deg, rgba(34,211,238,0.2), rgba(129,140,248,0.15))` : 'rgba(255,255,255,0.03)', cursor: 'pointer', flexShrink: 0, boxShadow: isSelected ? `0 0 20px ${C.cyanGlow}` : 'none', transition: 'all 0.2s' }}>
              <span style={{ fontSize: 11, color: isSelected ? C.cyan : C.textMuted, fontFamily: "'JetBrains Mono', monospace" }}>{day}</span>
              <span style={{ fontSize: 18, fontWeight: 700, color: isSelected ? C.textPrimary : C.textSecondary, fontFamily: "'Exo 2', sans-serif" }}>{num}</span>
              {i === selectedDate && <div style={{ width: 4, height: 4, borderRadius: '50%', background: C.cyan }} />}
            </button>
          )
        })}
      </div>

      <div style={{ flex: 1, overflowY: 'auto', padding: '0 16px 16px' }}>
        {/* Today section */}
        <div style={{ fontSize: 11, fontWeight: 600, letterSpacing: '0.12em', color: C.textMuted, textTransform: 'uppercase', marginBottom: 12, fontFamily: "'JetBrains Mono', monospace" }}>
          Today · {TODAY_EVENTS.length} events
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 28 }}>
          {TODAY_EVENTS.map((event, i) => (
            <div key={i} style={{ ...glass({ borderRadius: 18, padding: '14px 16px' }), display: 'flex', alignItems: 'center', gap: 14, position: 'relative', overflow: 'hidden' }}>
              <div style={{ position: 'absolute', left: 0, top: 0, bottom: 0, width: 3, background: event.color, borderRadius: '18px 0 0 18px' }} />
              <div style={{ paddingLeft: 8 }}>
                <div style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: 12, color: event.color, fontWeight: 500 }}>{event.time}</div>
                <div style={{ fontSize: 14, color: C.textPrimary, fontWeight: 500, marginTop: 2 }}>{event.title}</div>
              </div>
              <div style={{ marginLeft: 'auto', padding: '3px 8px', borderRadius: 8, background: `${event.color}18`, border: `1px solid ${event.color}30`, fontSize: 11, color: event.color, fontFamily: "'JetBrains Mono', monospace" }}>{event.duration}</div>
            </div>
          ))}
        </div>

        {/* Upcoming section */}
        <div style={{ fontSize: 11, fontWeight: 600, letterSpacing: '0.12em', color: C.textMuted, textTransform: 'uppercase', marginBottom: 12, fontFamily: "'JetBrains Mono', monospace" }}>Upcoming</div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 1, ...glass({ borderRadius: 18, overflow: 'hidden', padding: 0 }) }}>
          {UPCOMING_EVENTS.map((event, i) => (
            <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 14, padding: '13px 16px', borderBottom: i < UPCOMING_EVENTS.length - 1 ? `1px solid ${C.border}` : 'none' }}>
              <div style={{ width: 8, height: 8, borderRadius: '50%', background: event.color, flexShrink: 0, boxShadow: `0 0 6px ${event.color}` }} />
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: 14, fontWeight: 500, color: C.textPrimary }}>{event.title}</div>
                <div style={{ fontSize: 11, color: C.textMuted, marginTop: 2, fontFamily: "'JetBrains Mono', monospace" }}>{event.date} · {event.time}</div>
              </div>
              <svg width="14" height="14" fill="none" stroke={C.textMuted} strokeWidth="1.5" viewBox="0 0 24 24"><path d="m9 18 6-6-6-6" /></svg>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

/* ─── Screen F: Memory & Tasks ───────────────────────────────── */
const MEMORIES = [
  { id: 1, text: 'Prefers morning meetings before 11 AM', tag: 'Work', tagColor: C.cyan, date: 'Jul 15' },
  { id: 2, text: 'Drinks black coffee — no sugar, no milk', tag: 'Personal', tagColor: C.violet, date: 'Jul 12' },
  { id: 3, text: 'Working on a React Native project with Expo', tag: 'Dev', tagColor: C.success, date: 'Jul 10' },
  { id: 4, text: 'Allergic to shellfish; vegetarian on weekdays', tag: 'Health', tagColor: C.amber, date: 'Jul 8' },
]

const INITIAL_TASKS = [
  { id: 1, text: 'Review Q3 project proposal', done: false, priority: 'high' },
  { id: 2, text: 'Send weekly report to team', done: true, priority: 'medium' },
  { id: 3, text: 'Buy groceries (oat milk, bread)', done: false, priority: 'low' },
  { id: 4, text: 'Book dentist appointment', done: false, priority: 'medium' },
  { id: 5, text: 'Read Gemma 3n release notes', done: true, priority: 'low' },
]

function MemoryScreen() {
  const [tasks, setTasks] = useState(INITIAL_TASKS)
  const [memories] = useState(MEMORIES)
  const [activeSection, setActiveSection] = useState<'memory' | 'tasks'>('memory')

  const toggleTask = (id: number) => setTasks(t => t.map(task => task.id === id ? { ...task, done: !task.done } : task))

  const priorityColor = (p: string) => p === 'high' ? C.danger : p === 'medium' ? C.amber : C.textMuted

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: C.bg }}>
      <StatusBar />
      <div style={{ padding: '8px 20px 16px', flexShrink: 0 }}>
        <div style={{ fontFamily: "'Exo 2', sans-serif", fontSize: 22, fontWeight: 700, color: C.textPrimary }}>Memory & Tasks</div>
        <div style={{ fontSize: 13, color: C.textMuted, marginTop: 2 }}>Stored on-device · Private</div>
      </div>

      {/* Toggle tabs */}
      <div style={{ display: 'flex', padding: '0 16px 20px', gap: 8, flexShrink: 0 }}>
        {(['memory', 'tasks'] as const).map(tab => (
          <button key={tab} onClick={() => setActiveSection(tab)} style={{ flex: 1, padding: '10px', borderRadius: 14, border: `1px solid ${activeSection === tab ? 'rgba(34,211,238,0.3)' : C.border}`, background: activeSection === tab ? `rgba(34,211,238,0.1)` : 'rgba(255,255,255,0.03)', color: activeSection === tab ? C.cyan : C.textSecondary, fontSize: 14, fontWeight: 600, cursor: 'pointer', transition: 'all 0.2s', fontFamily: "'Exo 2', sans-serif", letterSpacing: '0.03em' }}>
            {tab === 'memory' ? '🧠 Memory' : '✓ Tasks'}
          </button>
        ))}
      </div>

      <div style={{ flex: 1, overflowY: 'auto', padding: '0 16px 16px' }}>
        {activeSection === 'memory' ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            <div style={{ fontSize: 11, color: C.textMuted, marginBottom: 4, fontFamily: "'JetBrains Mono', monospace" }}>{memories.length} memories stored</div>
            {memories.map((mem) => (
              <div key={mem.id} style={{ ...glass({ borderRadius: 18, padding: '16px 16px' }), animation: 'fade-up 0.3s ease both' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 8 }}>
                  <div style={{ padding: '3px 10px', borderRadius: 8, background: `${mem.tagColor}18`, border: `1px solid ${mem.tagColor}30`, fontSize: 11, color: mem.tagColor, fontFamily: "'JetBrains Mono', monospace", fontWeight: 500 }}>{mem.tag}</div>
                  <span style={{ fontSize: 11, color: C.textMuted, fontFamily: "'JetBrains Mono', monospace" }}>{mem.date}</span>
                </div>
                <div style={{ fontSize: 14, color: C.textPrimary, lineHeight: 1.5 }}>{mem.text}</div>
              </div>
            ))}

            {/* Add memory prompt */}
            <div style={{ ...glass({ borderRadius: 18, padding: '14px 16px', border: `1px dashed rgba(255,255,255,0.1)` }), display: 'flex', alignItems: 'center', gap: 10, cursor: 'pointer' }}>
              <div style={{ width: 28, height: 28, borderRadius: '50%', border: `1.5px dashed ${C.textMuted}`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <span style={{ fontSize: 18, color: C.textMuted, lineHeight: 1 }}>+</span>
              </div>
              <span style={{ fontSize: 13, color: C.textMuted }}>Tell Kosmos something to remember...</span>
            </div>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
              <span style={{ fontSize: 11, color: C.textMuted, fontFamily: "'JetBrains Mono', monospace" }}>{tasks.filter(t => !t.done).length} pending · {tasks.filter(t => t.done).length} done</span>
              <div style={{ width: 56, height: 4, borderRadius: 4, background: 'rgba(255,255,255,0.06)', overflow: 'hidden' }}>
                <div style={{ height: '100%', background: C.success, width: `${(tasks.filter(t => t.done).length / tasks.length) * 100}%`, borderRadius: 4, transition: 'width 0.4s ease' }} />
              </div>
            </div>

            {tasks.map((task) => (
              <button
                key={task.id}
                onClick={() => toggleTask(task.id)}
                style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '14px 16px', borderRadius: 16, ...glass({ border: `1px solid ${task.done ? C.border : C.borderHigh}` }), cursor: 'pointer', textAlign: 'left', transition: 'all 0.2s', opacity: task.done ? 0.6 : 1 }}
              >
                {/* Checkbox */}
                <div style={{ width: 22, height: 22, borderRadius: 7, border: `2px solid ${task.done ? C.success : C.border}`, background: task.done ? C.successDim : 'transparent', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, transition: 'all 0.2s' }}>
                  {task.done && (
                    <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                      <path d="M2 6l3 3 5-5" stroke={C.success} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                    </svg>
                  )}
                </div>
                <span style={{ flex: 1, fontSize: 14, color: task.done ? C.textMuted : C.textPrimary, textDecoration: task.done ? 'line-through' : 'none', lineHeight: 1.4, transition: 'all 0.2s' }}>{task.text}</span>
                <div style={{ width: 6, height: 6, borderRadius: '50%', background: priorityColor(task.priority), flexShrink: 0, boxShadow: `0 0 6px ${priorityColor(task.priority)}` }} />
              </button>
            ))}

            {/* Add task */}
            <div style={{ ...glass({ borderRadius: 16, padding: '14px 16px', border: `1px dashed rgba(255,255,255,0.1)` }), display: 'flex', alignItems: 'center', gap: 10, cursor: 'pointer' }}>
              <div style={{ width: 22, height: 22, borderRadius: 7, border: `1.5px dashed ${C.textMuted}`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <span style={{ fontSize: 14, color: C.textMuted, lineHeight: 1 }}>+</span>
              </div>
              <span style={{ fontSize: 13, color: C.textMuted }}>Add new task...</span>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

/* ─── Bottom Navigation Bar ──────────────────────────────────── */
function BottomNav({ active, onChange }: { active: Screen; onChange: (s: Screen) => void }) {
  const tabs = [
    {
      id: 'chat' as Screen, label: 'Chat',
      icon: (active: boolean) => <svg width="22" height="22" fill="none" stroke={active ? C.cyan : C.textMuted} strokeWidth="1.5" viewBox="0 0 24 24"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" /></svg>
    },
    {
      id: 'schedule' as Screen, label: 'Schedule',
      icon: (active: boolean) => <svg width="22" height="22" fill="none" stroke={active ? C.cyan : C.textMuted} strokeWidth="1.5" viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="18" rx="2" /><path d="M16 2v4M8 2v4M3 10h18" /></svg>
    },
    {
      id: 'memory' as Screen, label: 'Memory',
      icon: (active: boolean) => <svg width="22" height="22" fill="none" stroke={active ? C.cyan : C.textMuted} strokeWidth="1.5" viewBox="0 0 24 24"><path d="M12 2a10 10 0 1 0 0 20A10 10 0 0 0 12 2z" /><path d="M12 8v4l3 3" /></svg>
    },
  ]

  return (
    <div style={{ flexShrink: 0, ...glass({ borderRadius: 0, border: 'none', borderTop: `1px solid ${C.border}` }), padding: '10px 8px 28px', display: 'flex', justifyContent: 'space-around' }}>
      {tabs.map(tab => {
        const isActive = active === tab.id
        return (
          <button key={tab.id} onClick={() => onChange(tab.id)} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4, padding: '8px 20px', borderRadius: 16, border: 'none', background: isActive ? `rgba(34,211,238,0.08)` : 'transparent', cursor: 'pointer', transition: 'all 0.2s' }}>
            {tab.icon(isActive)}
            <span style={{ fontSize: 10, color: isActive ? C.cyan : C.textMuted, fontFamily: "'Exo 2', sans-serif", fontWeight: isActive ? 600 : 400, letterSpacing: '0.06em' }}>{tab.label}</span>
          </button>
        )
      })}
    </div>
  )
}

/* ─── Demo Navigator ─────────────────────────────────────────── */
const DEMO_SCREENS = [
  { id: 'loading' as Screen, label: 'A · Loading' },
  { id: 'chat' as Screen, label: 'B · Chat' },
  { id: 'settings' as Screen, label: 'C · Settings' },
  { id: 'calendar' as Screen, label: 'D · Calendar Card' },
  { id: 'schedule' as Screen, label: 'E · Schedule' },
  { id: 'memory' as Screen, label: 'F · Memory' },
]

/* ─── Root App ───────────────────────────────────────────────── */
export default function App() {
  const [screen, setScreen] = useState<Screen>('loading')
  const [showCalendar, setShowCalendar] = useState(false)
  const [prevScreen, setPrevScreen] = useState<Screen>('chat')

  const navigate = (s: Screen) => {
    setScreen(s)
    if (s === 'calendar') setShowCalendar(true)
    else setShowCalendar(false)
  }

  const bottomNavScreens: Screen[] = ['chat', 'schedule', 'memory']
  const showBottomNav = bottomNavScreens.includes(screen) && !showCalendar

  const handleSettingsOpen = () => { setPrevScreen(screen); setScreen('settings') }
  const handleSettingsBack = () => setScreen(prevScreen)
  const handleCalendarOpen = () => setShowCalendar(true)
  const handleCalendarClose = () => setShowCalendar(false)

  return (
    <div style={{ minHeight: '100vh', background: '#020912', display: 'flex', flexDirection: 'column', alignItems: 'center', padding: '20px 16px 40px', fontFamily: "'Inter', system-ui, sans-serif" }}>
      {/* Page label */}
      <div style={{ marginBottom: 16, textAlign: 'center' }}>
        <div style={{ fontFamily: "'Exo 2', sans-serif", fontSize: 13, fontWeight: 700, letterSpacing: '0.2em', color: 'rgba(34,211,238,0.6)', textTransform: 'uppercase', marginBottom: 4 }}>Kosmos · Design Showcase</div>
        <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.2)', fontFamily: "'JetBrains Mono', monospace" }}>On-Device AI Assistant · 6 Screens</div>
      </div>

      {/* Demo screen selector */}
      <div style={{ display: 'flex', gap: 6, marginBottom: 24, flexWrap: 'wrap', justifyContent: 'center', maxWidth: 600 }}>
        {DEMO_SCREENS.map(s => {
          const isActive = screen === s.id || (s.id === 'calendar' && showCalendar)
          return (
            <button
              key={s.id}
              onClick={() => navigate(s.id)}
              style={{ padding: '6px 14px', borderRadius: 20, border: `1px solid ${isActive ? C.cyan : 'rgba(255,255,255,0.12)'}`, background: isActive ? `rgba(34,211,238,0.12)` : 'rgba(255,255,255,0.03)', color: isActive ? C.cyan : '#64748B', fontSize: 12, fontWeight: 500, cursor: 'pointer', transition: 'all 0.2s', fontFamily: "'JetBrains Mono', monospace", boxShadow: isActive ? `0 0 12px rgba(34,211,238,0.2)` : 'none' }}
            >
              {s.label}
            </button>
          )
        })}
      </div>

      {/* Phone frame */}
      <div style={{ position: 'relative', width: 390, flexShrink: 0 }}>
        {/* Glow under phone */}
        <div style={{ position: 'absolute', bottom: -20, left: '50%', transform: 'translateX(-50%)', width: 280, height: 40, background: 'radial-gradient(ellipse, rgba(34,211,238,0.2) 0%, transparent 70%)', filter: 'blur(20px)' }} />

        {/* Phone outer shell */}
        <div style={{ width: 390, height: 844, borderRadius: 52, background: '#0A0A14', border: '1px solid rgba(255,255,255,0.15)', overflow: 'hidden', position: 'relative', boxShadow: `0 0 0 1px rgba(0,0,0,0.8), 0 0 0 9px #0D1625, 0 0 0 10px rgba(255,255,255,0.08), 0 40px 80px rgba(0,0,0,0.9), inset 0 1px 0 rgba(255,255,255,0.1)` }}>
          {/* Notch / Dynamic Island */}
          <div style={{ position: 'absolute', top: 12, left: '50%', transform: 'translateX(-50%)', width: 120, height: 34, background: '#000', borderRadius: 20, zIndex: 100, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}>
            <div style={{ width: 10, height: 10, borderRadius: '50%', background: '#1a1a1a', border: '1px solid rgba(255,255,255,0.05)' }} />
            <div style={{ width: 8, height: 8, borderRadius: '50%', background: '#111' }} />
          </div>

          {/* Screen content */}
          <div style={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column', background: C.bg }}>
            {screen === 'loading' && (
              <LoadingScreen onDone={() => setScreen('chat')} />
            )}
            {(screen === 'chat' || showCalendar) && (
              <div style={{ display: 'flex', flexDirection: 'column', height: '100%', position: 'relative' }}>
                <ChatScreen onOpenSettings={handleSettingsOpen} onOpenCalendar={handleCalendarOpen} />
                {showCalendar && <CalendarCard onClose={handleCalendarClose} />}
                <BottomNav active={screen} onChange={s => { setShowCalendar(false); setScreen(s) }} />
              </div>
            )}
            {screen === 'settings' && (
              <SettingsScreen onBack={handleSettingsBack} />
            )}
            {screen === 'schedule' && (
              <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
                <div style={{ flex: 1, overflow: 'hidden' }}>
                  <ScheduleScreen />
                </div>
                <BottomNav active={screen} onChange={setScreen} />
              </div>
            )}
            {screen === 'memory' && (
              <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
                <div style={{ flex: 1, overflow: 'hidden' }}>
                  <MemoryScreen />
                </div>
                <BottomNav active={screen} onChange={setScreen} />
              </div>
            )}
          </div>

          {/* Screen glare */}
          <div style={{ position: 'absolute', inset: 0, background: 'linear-gradient(135deg, rgba(255,255,255,0.03) 0%, transparent 50%)', pointerEvents: 'none', borderRadius: 52 }} />
        </div>
      </div>

      {/* Legend */}
      <div style={{ marginTop: 32, display: 'flex', gap: 20, flexWrap: 'wrap', justifyContent: 'center' }}>
        {[{ color: C.cyan, label: 'Primary — Electric Cyan' }, { color: C.violet, label: 'Secondary — Soft Violet' }, { color: C.success, label: 'Success — Emerald' }, { color: C.danger, label: 'Danger — Rose' }].map(item => (
          <div key={item.label} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <div style={{ width: 8, height: 8, borderRadius: '50%', background: item.color, boxShadow: `0 0 8px ${item.color}` }} />
            <span style={{ fontSize: 11, color: 'rgba(255,255,255,0.3)', fontFamily: "'JetBrains Mono', monospace" }}>{item.label}</span>
          </div>
        ))}
      </div>
    </div>
  )
}
