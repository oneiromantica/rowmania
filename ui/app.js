// Beispieldaten nur für das Mockup
const GOAL = 600
const CREW = [
  { name: 'Anna',  color: '#e98fa8', weekly: [14, 41, 63, 84, 108, 127, 148] },
  { name: 'Jonas', color: '#5aa89d', weekly: [10, 28, 49, 66, 82, 99, 112] },
  { name: 'Mira',  color: '#9a86c2', weekly: [8, 22, 38, 55, 70, 84, 96], me: true },
  { name: 'Timo',  color: '#f6b99a', weekly: [0, 6, 15, 24, 35, 45, 56] },
]
const NS = 'http://www.w3.org/2000/svg'
const svg = (tag, attrs, text) => {
  const el = document.createElementNS(NS, tag)
  Object.entries(attrs).forEach(([k, v]) => el.setAttribute(k, v))
  if (text !== undefined) el.textContent = text
  return el
}
const km = (c) => c.weekly.at(-1)
const total = CREW.reduce((s, c) => s + km(c), 0)

document.getElementById('track').append(...CREW.map((c) => {
  const seg = document.createElement('span')
  seg.style.cssText = `width:${(km(c) / GOAL) * 100}%;background:${c.color}`
  return seg
}))

document.getElementById('contrib').innerHTML = CREW.map((c) => `
  <li><i style="background:${c.color}"></i>
  <span>${c.name}${c.me ? ' (du)' : ''}</span>
  <span><b>${km(c)} km</b><em>${Math.round((km(c) / total) * 100)} %</em></span></li>`).join('')

// Linien je Teilnehmer, kumuliert pro Woche
const chart = document.getElementById('chart')
const W = 760, H = 300, L = 36, R = 24, T = 10, B = 30
const weeks = CREW[0].weekly.length
const x = (i) => L + (i / (weeks - 1)) * (W - L - R)
const y = (v) => H - B - (v / 160) * (H - T - B)
;[0, 80, 160].forEach((v) => {
  chart.append(svg('line', { x1: L, x2: W - R, y1: y(v), y2: y(v), stroke: '#e8e1f1', 'stroke-width': 1.5 }))
  chart.append(svg('text', { x: L - 8, y: y(v) + 4, 'text-anchor': 'end' }, v))
})
for (let i = 0; i < weeks; i++) chart.append(svg('text', { x: x(i), y: H - 8, 'text-anchor': 'middle' }, `W${i + 1}`))
CREW.forEach((c) => {
  chart.append(svg('polyline', { points: c.weekly.map((v, i) => `${x(i)},${y(v)}`).join(' '), fill: 'none', stroke: c.color, 'stroke-width': 4, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }))
  chart.append(svg('text', { x: x(weeks - 1) - 8, y: y(km(c)) - 10, 'text-anchor': 'end', style: 'fill:#4b3668' }, c.name))
})

// Wechsel der Challenge-Chips
document.querySelectorAll('.chip').forEach((chip) => chip.addEventListener('click', () => {
  document.querySelectorAll('.chip').forEach((o) => o.setAttribute('aria-pressed', String(o === chip)))
}))
