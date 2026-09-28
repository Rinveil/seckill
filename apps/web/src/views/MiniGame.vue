<template>
  <el-card class="page-panel" shadow="never">
    <template #header>
      <div class="card-head">
        <div>
          <div class="title">小游戏 · 接住石头</div>
          <div class="hint">左右移动托盘接石头，躲开「售罄」炸弹 · 最高分存在本机</div>
        </div>
        <el-button type="primary" @click="restart">再来一局</el-button>
      </div>
    </template>

    <div class="hud">
      <span>得分 <strong>{{ score }}</strong></span>
      <span>连击 <strong>{{ combo }}</strong></span>
      <span>生命 <strong>{{ lives }}</strong></span>
      <span>最高 <strong>{{ best }}</strong></span>
    </div>

    <div class="stage-wrap">
      <canvas
        ref="canvasRef"
        class="stage"
        tabindex="0"
        @pointerdown="onPointer"
        @pointermove="onPointer"
      />
      <div v-if="phase !== 'run'" class="overlay" @click="restart">
        <p class="overlay-title">{{ phase === 'idle' ? '接住石头' : '本局结束' }}</p>
        <p v-if="phase === 'over'" class="overlay-score">得分 {{ score }}</p>
        <p class="overlay-tip">← → 或 A D 移动 · 鼠标也可拖 · 点击开始</p>
      </div>
    </div>

    <p class="rules">
      铜色石头 +10，玉石 +50 并加速连击；漏接或接到红色「售罄」扣一条命。速度会随得分加快。
    </p>
  </el-card>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'

const BEST_KEY = 'stone-mall-game-best'
const W = 800
const H = 480

const canvasRef = ref(null)
const score = ref(0)
const combo = ref(0)
const lives = ref(5)
const best = ref(Number(localStorage.getItem(BEST_KEY) || 0))
const phase = ref('idle')

let ctx = null
let raf = 0
let paddleX = W / 2
let keys = { left: false, right: false }
let drops = []
let sparks = []
let spawnAcc = 0
let lastTs = 0

function paddle() {
  return { w: 128, h: 18, y: H - 36 }
}

function resetRound() {
  score.value = 0
  combo.value = 0
  lives.value = 5
  paddleX = W / 2
  drops = []
  sparks = []
  spawnAcc = 0
  lastTs = 0
}

function restart() {
  resetRound()
  spawn()
  phase.value = 'run'
  canvasRef.value?.focus()
}

function burst(x, y, color) {
  for (let i = 0; i < 8; i++) {
    const a = (Math.PI * 2 * i) / 8
    sparks.push({
      x, y,
      vx: Math.cos(a) * (1.4 + Math.random()),
      vy: Math.sin(a) * (1.4 + Math.random()),
      life: 18,
      color
    })
  }
}

function spawn() {
  const roll = Math.random()
  let kind = 'stone'
  if (roll < 0.12) kind = 'bomb'
  else if (roll < 0.22) kind = 'jade'
  const spread = Math.min(W / 2 - r, 70 + score.value * 0.35)
  const x = Math.min(W - r, Math.max(r, paddleX - spread + Math.random() * spread * 2))
  drops.push({
    x,
    y: -r,
    r,
    kind,
    vy: 1.55 + score.value / 140 + Math.random() * 0.45
  })
}

function hitTest(d) {
  const p = paddle()
  const left = paddleX - p.w / 2
  const right = paddleX + p.w / 2
  const top = p.y
  const nx = Math.max(left, Math.min(d.x, right))
  const ny = Math.max(top, Math.min(d.y, top + p.h))
  const dx = d.x - nx
  const dy = d.y - ny
  return dx * dx + dy * dy <= d.r * d.r
}

function persistBest() {
  if (score.value > best.value) {
    best.value = score.value
    localStorage.setItem(BEST_KEY, String(best.value))
  }
}

function loseLife() {
  combo.value = 0
  lives.value -= 1
  if (lives.value <= 0) {
    lives.value = 0
    persistBest()
    phase.value = 'over'
  }
}

function catchDrop(d) {
  if (d.kind === 'bomb') {
    burst(d.x, d.y, '#c45656')
    loseLife()
    return
  }
  combo.value += 1
  const base = d.kind === 'jade' ? 50 : 10
  score.value += base * Math.max(1, Math.min(combo.value, 8))
  burst(d.x, d.y, d.kind === 'jade' ? '#3f6b5c' : '#b86b3d')
}

function step(dt) {
  const p = paddle()
  const speed = 420 * dt
  if (keys.left) paddleX -= speed
  if (keys.right) paddleX += speed
  paddleX = Math.max(p.w / 2, Math.min(W - p.w / 2, paddleX))

  spawnAcc += dt
  const interval = Math.max(0.28, 0.78 - score.value / 800)
  if (spawnAcc >= interval) {
    spawnAcc = 0
    spawn()
  }

  for (const d of drops) d.y += d.vy * (60 * dt)

  const next = []
  for (const d of drops) {
    if (hitTest(d)) {
      catchDrop(d)
      continue
    }
    if (d.y - d.r > H) {
      if (d.kind !== 'bomb') loseLife()
      continue
    }
    next.push(d)
  }
  drops = next

  sparks = sparks.filter((s) => {
    s.x += s.vx
    s.y += s.vy
    s.vy += 0.08
    s.life -= 1
    return s.life > 0
  })
}

function drawStone(d) {
  const palette = {
    stone: { fill: '#8d6a4a', ring: '#5c4030', text: '石' },
    jade: { fill: '#4d7c6a', ring: '#2d4f43', text: '玉' },
    bomb: { fill: '#b54a4a', ring: '#6e2424', text: '售' }
  }[d.kind]
  ctx.beginPath()
  ctx.arc(d.x, d.y, d.r, 0, Math.PI * 2)
  ctx.fillStyle = palette.fill
  ctx.fill()
  ctx.lineWidth = 2
  ctx.strokeStyle = palette.ring
  ctx.stroke()
  ctx.fillStyle = '#f7f1ea'
  ctx.font = 'bold 12px "Noto Serif SC", serif'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText(palette.text, d.x, d.y + 1)
}

function paint() {
  if (!ctx) return
  ctx.clearRect(0, 0, W, H)
  const g = ctx.createLinearGradient(0, 0, 0, H)
  g.addColorStop(0, '#243038')
  g.addColorStop(1, '#162026')
  ctx.fillStyle = g
  ctx.fillRect(0, 0, W, H)

  ctx.fillStyle = 'rgba(184, 107, 61, 0.08)'
  for (let i = 0; i < 6; i++) {
    ctx.beginPath()
    ctx.arc(80 + i * 130, 40 + (i % 2) * 28, 46, 0, Math.PI * 2)
    ctx.fill()
  }

  for (const s of sparks) {
    ctx.globalAlpha = s.life / 18
    ctx.fillStyle = s.color
    ctx.fillRect(s.x, s.y, 3, 3)
    ctx.globalAlpha = 1
  }

  for (const d of drops) drawStone(d)

  const p = paddle()
  const x = paddleX - p.w / 2
  ctx.fillStyle = '#b86b3d'
  ctx.beginPath()
  if (typeof ctx.roundRect === 'function') {
    ctx.roundRect(x, p.y, p.w, p.h, 8)
  } else {
    ctx.rect(x, p.y, p.w, p.h)
  }
  ctx.fill()
  ctx.fillStyle = '#f7f1ea'
  ctx.font = '11px "Noto Sans SC", sans-serif'
  ctx.textAlign = 'center'
  ctx.fillText('托盘', paddleX, p.y + 12)
}

function loop(ts) {
  raf = requestAnimationFrame(loop)
  if (!lastTs) lastTs = ts
  const dt = Math.min(0.05, (ts - lastTs) / 1000)
  lastTs = ts
  if (phase.value === 'run') step(dt)
  if (ctx) paint()
}

function canvasPoint(ev) {
  const rect = canvasRef.value.getBoundingClientRect()
  return ((ev.clientX - rect.left) / rect.width) * W
}

function onPointer(ev) {
  if (!canvasRef.value || phase.value !== 'run') return
  paddleX = canvasPoint(ev)
}

function onKeyDown(ev) {
  if (ev.key === ' ' || ev.code === 'Space') {
    if (phase.value !== 'run') restart()
    ev.preventDefault()
    return
  }
  if (ev.key === 'ArrowLeft' || ev.key === 'a' || ev.key === 'A') {
    keys.left = true
    ev.preventDefault()
  }
  if (ev.key === 'ArrowRight' || ev.key === 'd' || ev.key === 'D') {
    keys.right = true
    ev.preventDefault()
  }
}

function onKeyUp(ev) {
  if (ev.key === 'ArrowLeft' || ev.key === 'a' || ev.key === 'A') keys.left = false
  if (ev.key === 'ArrowRight' || ev.key === 'd' || ev.key === 'D') keys.right = false
}

function fitCanvas() {
  const canvas = canvasRef.value
  if (!canvas) return
  const dpr = Math.min(window.devicePixelRatio || 1, 2)
  canvas.width = W * dpr
  canvas.height = H * dpr
  ctx = canvas.getContext('2d')
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
}

onMounted(() => {
  fitCanvas()
  window.addEventListener('keydown', onKeyDown)
  window.addEventListener('keyup', onKeyUp)
  raf = requestAnimationFrame(loop)
})

onUnmounted(() => {
  cancelAnimationFrame(raf)
  window.removeEventListener('keydown', onKeyDown)
  window.removeEventListener('keyup', onKeyUp)
})
</script>

<style scoped>
.hud {
  display: flex;
  flex-wrap: wrap;
  gap: 18px 28px;
  margin-bottom: 14px;
  color: var(--muted);
  font-size: 13px;
  letter-spacing: 0.04em;
}
.hud strong {
  color: var(--stone-ink);
  font-variant-numeric: tabular-nums;
  margin-left: 4px;
}
.stage-wrap {
  position: relative;
  max-width: 800px;
}
.stage {
  display: block;
  width: 100%;
  height: auto;
  aspect-ratio: 800 / 480;
  border-radius: 12px;
  cursor: pointer;
  outline: none;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.08);
}
.overlay {
  position: absolute;
  inset: 0;
  display: grid;
  place-content: center;
  text-align: center;
  background: rgba(22, 32, 38, 0.55);
  border-radius: 12px;
  color: #f7f1ea;
  cursor: pointer;
}
.overlay-title {
  margin: 0;
  font-family: "Noto Serif SC", serif;
  font-size: 32px;
  letter-spacing: 0.12em;
}
.overlay-score {
  margin: 10px 0 0;
  font-size: 18px;
}
.overlay-tip {
  margin: 12px 0 0;
  font-size: 13px;
  color: rgba(247, 241, 234, 0.75);
}
.rules {
  max-width: 800px;
  margin: 12px 0 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.6;
}
</style>
