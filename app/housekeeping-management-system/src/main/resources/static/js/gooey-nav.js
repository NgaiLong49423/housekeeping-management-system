/**
 * GooeyNav - Ported to Vanilla JavaScript & WebGL2 from React Bits
 * Features WebGL2 shader physics, SDF droplets, bezier flight curves & liquid gooey pill blending.
 *
 * Supports both programmatic instantiation and declarative SSR markup:
 *
 * Programmatic usage (matches React Bits <GooeyNav />):
 * GooeyNav(container, {
 *   items: [
 *     { label: 'Home', href: '#' },
 *     { label: 'Work', href: '#' },
 *     { label: 'About', href: '#' },
 *     { label: 'Contact', href: '#' }
 *   ],
 *   initialActiveIndex: 0,
 *   theme: "dark",
 *   size: "md",
 *   frame: true,
 *   particleCount: 15,
 *   spread: 56,
 *   animationTime: 600,
 *   timeVariance: 300,
 *   gooeyness: 0.5,
 *   wobble: 0.5,
 *   hoverEffect: true,
 *   onChange: (index, item) => console.log(index, item.label)
 * });
 */
(() => {
  "use strict";

  const THEMES = {
    dark: {
      '--gn-frame': 'rgba(38, 35, 46, 0.66)',
      '--gn-frame-edge': 'rgba(255, 255, 255, 0.08)',
      '--gn-frame-highlight': 'rgba(255, 255, 255, 0.07)',
      '--gn-frame-shadow': '0 22px 44px -20px rgba(0, 0, 0, 0.8), 0 6px 16px -8px rgba(0, 0, 0, 0.5)',
      '--gn-ink': '#f4f4f5',
      '--gn-muted': 'rgba(244, 244, 245, 0.62)',
      '--gn-pill': '#9fe3cf',
      '--gn-active-ink': '#13201c'
    },
    light: {
      '--gn-frame': 'rgba(240, 240, 243, 0.8)',
      '--gn-frame-edge': 'rgba(24, 24, 27, 0.07)',
      '--gn-frame-highlight': 'rgba(255, 255, 255, 0.95)',
      '--gn-frame-shadow': '0 22px 44px -22px rgba(24, 24, 27, 0.3), 0 6px 16px -10px rgba(24, 24, 27, 0.16)',
      '--gn-ink': '#27272a',
      '--gn-muted': 'rgba(39, 39, 42, 0.62)',
      '--gn-pill': '#27272a',
      '--gn-active-ink': '#ffffff'
    },
    gonnha: {
      '--gn-frame': 'rgba(22, 33, 29, 0.66)',
      '--gn-frame-edge': 'rgba(243, 239, 230, 0.14)',
      '--gn-frame-highlight': 'rgba(255, 255, 255, 0.18)',
      '--gn-frame-shadow': '0 16px 36px -12px rgba(0, 0, 0, 0.45)',
      '--gn-ink': '#ffffff',
      '--gn-muted': 'rgba(243, 239, 230, 0.82)',
      '--gn-pill': '#9fe3cf',
      '--gn-active-ink': '#13201c'
    }
  };

  const PILLS = {
    dark: { color: '#9fe3cf', hover: 'rgba(255, 255, 255, 0.08)', shadow: 0.4 },
    light: { color: '#27272a', hover: 'rgba(24, 24, 27, 0.06)', shadow: 0.2 },
    gonnha: { color: '#9fe3cf', hover: 'rgba(159, 227, 207, 0.14)', shadow: 0.3 }
  };

  const MAX_DROPS = 32;
  const MAX_PILLS = 4;
  const MARGIN = 72;

  const VERTEX = `#version 300 es
in vec2 aPosition;
void main() {
  gl_Position = vec4(aPosition, 0.0, 1.0);
}
`;

  const FRAGMENT = `#version 300 es
precision highp float;

uniform vec2 uSize;
uniform float uRatio;
uniform vec4 uPills[${MAX_PILLS}];
uniform vec4 uHover;
uniform vec4 uDrops[${MAX_DROPS}];
uniform vec2 uTails[${MAX_DROPS}];
uniform vec3 uTints[${MAX_DROPS}];
uniform int uCount;
uniform float uSmooth;
uniform vec3 uPill;
uniform vec4 uHoverColor;
uniform float uShadow;

out vec4 outColor;

float sdPill(vec2 p, vec4 box) {
  vec2 extent = max(box.zw, vec2(0.0));
  float r = min(extent.x, extent.y);
  vec2 q = abs(p - box.xy) - extent + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

float sdCapsule(vec2 p, vec2 a, vec2 b, float r) {
  vec2 pa = p - a;
  vec2 ba = b - a;
  float h = clamp(dot(pa, ba) / max(dot(ba, ba), 0.0001), 0.0, 1.0);
  return length(pa - ba * h) - r;
}

vec4 blend(vec4 a, vec4 b, float k) {
  float h = clamp(0.5 + 0.5 * (b.w - a.w) / k, 0.0, 1.0);
  return vec4(mix(b.rgb, a.rgb, h), mix(b.w, a.w, h) - k * h * (1.0 - h));
}

vec4 field(vec2 p) {
  vec4 shape = vec4(uPill, 1e5);
  for (int i = 0; i < ${MAX_PILLS}; i++) {
    vec4 pill = uPills[i];
    if (pill.z <= 0.0) continue;
    shape = blend(shape, vec4(uPill, sdPill(p, pill)), uSmooth);
  }
  for (int i = 0; i < ${MAX_DROPS}; i++) {
    if (i >= uCount) break;
    vec4 drop = uDrops[i];
    if (drop.z <= 0.0) continue;
    float d = sdCapsule(p, drop.xy, drop.xy - uTails[i], drop.z);
    if (d > shape.w + uSmooth) continue;
    shape = blend(shape, vec4(uTints[i], d), uSmooth);
  }
  return shape;
}

void main() {
  vec2 p = vec2(gl_FragCoord.x, uSize.y * uRatio - gl_FragCoord.y) / uRatio;
  vec4 shape = field(p);
  float d = shape.w;
  float aa = max(fwidth(d), 0.35);
  float body = clamp(0.5 - d / aa, 0.0, 1.0);

  vec2 grad = vec2(dFdx(d), -dFdy(d));
  vec2 n = length(grad) > 0.0001 ? normalize(grad) : vec2(0.0, 1.0);
  float rim = (1.0 - smoothstep(0.0, 1.6, -d)) * max(0.0, -n.y) * 0.3;
  vec3 fill = mix(shape.rgb, vec3(1.0), rim) * (1.0 - 0.06 * max(0.0, n.y) * (1.0 - smoothstep(0.0, 6.0, -d)));

  float lifted = field(p - vec2(0.0, 3.0)).w;
  float shadow = (1.0 - smoothstep(-3.0, 9.0, lifted)) * uShadow * (1.0 - body);

  float hover = clamp(0.5 - sdPill(p, uHover) / aa, 0.0, 1.0) * uHoverColor.a;

  vec4 color = vec4(0.0, 0.0, 0.0, shadow);
  color = vec4(uHoverColor.rgb * hover, hover) + color * (1.0 - hover);
  color = vec4(fill * body, body) + color * (1.0 - body);
  outColor = color;
}
`;

  const parse = value => {
    const match = /^#?([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(String(value).trim());
    if (match) {
      const hex = match[1].length === 3 ? match[1].replace(/./g, d => d + d) : match[1];
      return [0, 2, 4].map(idx => parseInt(hex.slice(idx, idx + 2), 16) / 255).concat(1);
    }
    const rgba = /rgba?\(([^)]+)\)/i.exec(String(value));
    if (rgba) {
      const parts = rgba[1].split(',').map(part => parseFloat(part));
      return [parts[0] / 255, parts[1] / 255, parts[2] / 255, parts[3] ?? 1];
    }
    return [1, 1, 1, 1];
  };

  const contrastInk = rgb => (0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2] > 0.55 ? '#13201c' : '#ffffff');
  const clamp01 = value => Math.min(1, Math.max(0, value));

  const travelEase = t => {
    const x = clamp01(t);
    return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
  };

  const bezier = (a, b, c, d, t) => {
    const u = 1 - t;
    return u * u * u * a + 3 * u * u * t * b + 3 * u * t * t * c + t * t * t * d;
  };

  const pillPath = (x, y, w, h) => {
    if (w <= 0.5 || h <= 0.5) return '';
    const r = Math.min(w, h) / 2;
    return `M${x + r},${y}H${x + w - r}A${r},${r} 0 0 1 ${x + w},${y + r}V${y + h - r}A${r},${r} 0 0 1 ${x + w - r},${y + h}H${x + r}A${r},${r} 0 0 1 ${x},${y + h - r}V${y + r}A${r},${r} 0 0 1 ${x + r},${y}Z`;
  };

  const compile = (gl, type, source) => {
    const shader = gl.createShader(type);
    gl.shaderSource(shader, source);
    gl.compileShader(shader);
    return shader;
  };

  const UNIFORMS = [
    'uSize',
    'uRatio',
    'uPills',
    'uHover',
    'uDrops',
    'uTails',
    'uTints',
    'uCount',
    'uSmooth',
    'uPill',
    'uHoverColor',
    'uShadow'
  ];

  const build = gl => {
    const program = gl.createProgram();
    gl.attachShader(program, compile(gl, gl.VERTEX_SHADER, VERTEX));
    gl.attachShader(program, compile(gl, gl.FRAGMENT_SHADER, FRAGMENT));
    gl.linkProgram(program);
    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
      gl.deleteProgram(program);
      return null;
    }
    const uniforms = Object.fromEntries(UNIFORMS.map(name => [name, gl.getUniformLocation(program, name)]));
    const buffer = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
    return { program, uniforms, buffer, position: gl.getAttribLocation(program, 'aPosition') };
  };

  function setupGooeyNav(navEl, userOptions = {}) {
    if (!navEl) return null;
    if (navEl._gooeyNavInitialized) return navEl._gooeyNavEngine;

    // Build DOM structure dynamically if items array is passed
    if (Array.isArray(userOptions.items) && userOptions.items.length) {
      const sizeClass = `gooey-nav--${userOptions.size || 'md'}`;
      navEl.classList.add('gooey-nav', sizeClass);
      if (userOptions.frame !== false) navEl.classList.add('gooey-nav--framed');
      if (userOptions.className) navEl.className += ' ' + userOptions.className;

      const itemsMarkup = userOptions.items.map((it, idx) => {
        const isInitActive = idx === (userOptions.initialActiveIndex || 0);
        const iconMarkup = it.icon ? `<span class="gooey-nav__icon">${it.icon}</span>` : '';
        const href = it.href || '#';
        return `
          <li class="gooey-nav__item ${isInitActive ? 'active' : ''}" data-gooey-index="${idx}" ${isInitActive ? 'data-active=""' : ''}>
            <a class="gooey-nav__link nav-link" href="${href}">
              ${iconMarkup}
              <span class="gooey-nav__label">${it.label || ''}</span>
            </a>
          </li>
        `;
      }).join('');

      navEl.innerHTML = `
        ${userOptions.frame !== false ? '<span class="gooey-nav__rim"></span>' : ''}
        <div class="gooey-nav__stage">
          <canvas class="gooey-nav__canvas" aria-hidden="true"></canvas>
          <span class="gooey-nav__fallback" aria-hidden="true"></span>
          <ul class="gooey-nav__list">
            ${itemsMarkup}
          </ul>
        </div>
      `;
    }

    navEl._gooeyNavInitialized = true;

    const themeName = userOptions.theme || navEl.dataset.theme || 'dark';
    const palette = THEMES[themeName] ? themeName : 'dark';
    const pillColor = userOptions.color || navEl.dataset.color || PILLS[palette].color;
    const pillRgb = parse(pillColor);
    const textColor = userOptions.activeTextColor || navEl.dataset.activeTextColor || contrastInk(pillRgb);

    // Apply theme variables
    const themeVars = THEMES[palette] || THEMES.dark;
    for (const [k, v] of Object.entries(themeVars)) {
      navEl.style.setProperty(k, v);
    }
    navEl.style.setProperty('--gn-pill', pillColor);
    navEl.style.setProperty('--gn-active-ink', textColor);

    // Ensure .gooey-nav__rim exists
    if (!navEl.querySelector('.gooey-nav__rim')) {
      const rim = document.createElement('span');
      rim.className = 'gooey-nav__rim';
      navEl.prepend(rim);
    }

    const stage = navEl.querySelector('.gooey-nav__stage');
    if (!stage) return null;

    let canvas = stage.querySelector('.gooey-nav__canvas');
    if (!canvas) {
      canvas = document.createElement('canvas');
      canvas.className = 'gooey-nav__canvas';
      canvas.setAttribute('aria-hidden', 'true');
      stage.prepend(canvas);
    }

    let fallback = stage.querySelector('.gooey-nav__fallback');
    if (!fallback) {
      fallback = document.createElement('span');
      fallback.className = 'gooey-nav__fallback';
      fallback.setAttribute('aria-hidden', 'true');
      stage.insertBefore(fallback, canvas.nextSibling);
    }

    const list = stage.querySelector('.gooey-nav__list');
    if (!list) return null;

    // Index list items and find active index
    const items = Array.from(list.children);
    let initialActive = userOptions.activeIndex ?? userOptions.initialActiveIndex ?? 0;
    let foundActiveFromDOM = false;

    items.forEach((item, idx) => {
      item.setAttribute('data-gooey-index', String(idx));
      if (item.hasAttribute('data-active') || item.classList.contains('active')) {
        initialActive = idx;
        foundActiveFromDOM = true;
        item.setAttribute('data-active', '');
      }
    });

    if (!foundActiveFromDOM && items[initialActive]) {
      items[initialActive].setAttribute('data-active', '');
    }

    const settings = {
      animationTime: Number(userOptions.animationTime ?? navEl.dataset.animationTime ?? 600),
      particleCount: Number(userOptions.particleCount ?? navEl.dataset.particleCount ?? 15),
      timeVariance: Number(userOptions.timeVariance ?? navEl.dataset.timeVariance ?? 300),
      spread: Number(userOptions.spread ?? navEl.dataset.spread ?? 56),
      gooeyness: Number(userOptions.gooeyness ?? navEl.dataset.gooeyness ?? 0.5),
      wobble: Number(userOptions.wobble ?? navEl.dataset.wobble ?? 0.5),
      hoverEffect: (userOptions.hoverEffect ?? navEl.dataset.hoverEffect) !== false && (userOptions.hoverEffect ?? navEl.dataset.hoverEffect) !== 'false',
      pillRgb,
      tints: (userOptions.colors && userOptions.colors.length) ? userOptions.colors.map(parse) : [pillRgb, parse('#b85d38'), parse('#52b788')],
      hoverRgb: parse(PILLS[palette]?.hover || PILLS.dark.hover),
      shadow: PILLS[palette]?.shadow || PILLS.dark.shadow
    };

    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const gl = canvas.getContext('webgl2', { alpha: true, premultipliedAlpha: true, antialias: false });
    const pipeline = gl ? build(gl) : null;
    fallback.style.display = pipeline ? 'none' : 'block';
    canvas.style.display = pipeline ? 'block' : 'none';

    const view = { width: 0, height: 0 };
    let rects = [];
    let target = initialActive;
    let current = initialActive;
    const pills = new Map();
    const drops = [];
    const hover = { x: 0, y: 0, w: 0, h: 0, alpha: 0, index: -1 };
    const pillData = new Float32Array(MAX_PILLS * 4);
    const dropData = new Float32Array(MAX_DROPS * 4);
    const tailData = new Float32Array(MAX_DROPS * 2);
    const tintData = new Float32Array(MAX_DROPS * 3);
    let raf = 0;
    let last = 0;

    const measure = () => {
      const box = stage.getBoundingClientRect();
      if (box.width === 0 || box.height === 0) return;
      const ratio = Math.min(window.devicePixelRatio || 1, 2);
      view.width = box.width;
      view.height = box.height;
      const outerWidth = box.width + MARGIN * 2;
      const outerHeight = box.height + MARGIN * 2;
      Object.assign(canvas.style, {
        left: `${-MARGIN}px`,
        top: `${-MARGIN}px`,
        width: `${outerWidth}px`,
        height: `${outerHeight}px`
      });
      const width = Math.max(1, Math.round(outerWidth * ratio));
      const height = Math.max(1, Math.round(outerHeight * ratio));
      if (canvas.width !== width || canvas.height !== height) {
        canvas.width = width;
        canvas.height = height;
      }
      rects = Array.from(list.children).map(item => {
        const rect = item.getBoundingClientRect();
        return { x: rect.left - box.left, y: rect.top - box.top, w: rect.width, h: rect.height };
      });
    };

    const pill = idx => {
      if (!pills.has(idx)) pills.set(idx, { mass: 0, scale: 0, velocity: 0, outgoing: 0 });
      return pills.get(idx);
    };

    const updateFallback = idx => {
      if (idx < 0 || !rects || !rects[idx]) return;
      const rect = rects[idx];
      if (rect.w <= 0 || rect.h <= 0) return;
      fallback.style.transform = `translate(${rect.x.toFixed(1)}px, ${rect.y.toFixed(1)}px)`;
      fallback.style.width = `${rect.w}px`;
      fallback.style.height = `${rect.h}px`;
      fallback.style.opacity = '1';
    };

    const shape = (idx, amount) => {
      const rect = rects[idx];
      if (!rect || amount <= 0.002) return null;
      const h = rect.h * Math.min(1.08, 0.42 + 0.58 * Math.sqrt(Math.max(0, amount)));
      const w = Math.max(h, rect.w * Math.pow(Math.max(0, amount), 0.75));
      return { x: rect.x + rect.w / 2 - w / 2, y: rect.y + rect.h / 2 - h / 2, w, h };
    };

    const plan = (drop, idx, now, fresh) => {
      const s = settings;
      const rect = rects[idx];
      if (!rect) return;
      const reach = Math.max(0, s.spread);
      const ex = rect.x + rect.w / 2 + (Math.random() - 0.5) * rect.w * 0.45;
      const ey = rect.y + rect.h / 2 + (Math.random() - 0.5) * rect.h * 0.2;
      const base = Math.max(0.15, s.animationTime / 1000);
      const variance = (Math.random() - 0.5) * (Math.max(0, s.timeVariance) / 1000);
      const duration = Math.max(0.15, base * (0.62 + Math.random() * 0.32) + variance * 0.6);
      const out = Math.random() * Math.PI * 2;
      const into = Math.random() * Math.PI * 2;
      const burst = reach * (0.7 + Math.random() * 0.6);
      const swing = reach * (0.55 + Math.random() * 0.55);
      const carry = Math.min(duration / 3, 0.12);
      drop.index = idx;
      drop.start = now;
      drop.duration = duration;
      drop.path = [
        drop.x,
        drop.y,
        fresh ? drop.x + Math.cos(out) * burst * 1.6 : drop.x + drop.vx * carry,
        fresh ? drop.y + Math.sin(out) * burst * 1.6 : drop.y + drop.vy * carry,
        ex + Math.cos(into) * swing * 1.5,
        ey + Math.sin(into) * swing * 1.5,
        ex,
        ey
      ];
    };

    const emit = (from, to, now) => {
      const s = settings;
      const source = pills.get(from);
      const rect = rects[from];
      if (!source || !rect || !rects[to]) return;
      const available = source.mass - source.outgoing;
      if (available <= 0.002) return;
      const room = MAX_DROPS - drops.length;
      const count = Math.min(room, Math.max(1, Math.round(Math.max(1, s.particleCount) * available)));
      if (count <= 0) return;
      const win = Math.max(0.15, s.animationTime / 1000) * 0.16;
      for (let i = 0; i < count; i++) {
        const mass = available / count;
        source.outgoing += mass;
        drops.push({
          x: rect.x + rect.w / 2 + (Math.random() - 0.5) * rect.w * 0.6,
          y: rect.y + rect.h / 2 + (Math.random() - 0.5) * rect.h * 0.3,
          vx: 0,
          vy: 0,
          mass,
          size: 0.8 + Math.random() * 0.4,
          given: 0,
          source: from,
          index: to,
          leave: now + Math.random() * win * 1000,
          emerged: 0,
          absorbed: 0,
          state: 'waiting',
          tint: s.tints.length ? s.tints[Math.floor(Math.random() * s.tints.length)] : null
        });
      }
    };

    const render = () => {
      const s = settings;
      const height = rects[target]?.h ?? rects[0]?.h ?? 36;
      const smooth = height * (0.18 + 0.5 * clamp01(s.gooeyness));
      const boxes = [];
      const blobs = [];
      for (const [idx, state] of pills) {
        const box = shape(idx, state.scale);
        if (box) boxes.push({ cx: box.x + box.w / 2, cy: box.y + box.h / 2, hw: box.w / 2, hh: box.h / 2 });
      }
      for (const drop of drops) {
        if (drop.state === 'waiting') continue;
        const radius = 0.26 * height * drop.size * Math.min(drop.emerged, 1 - drop.absorbed);
        if (radius <= 0.05) continue;
        const speed = Math.hypot(drop.vx, drop.vy);
        const length = Math.min(radius * 1.1, speed * 0.018);
        blobs.push({
          x: drop.x,
          y: drop.y,
          r: radius,
          tx: speed > 0.01 ? (drop.vx / speed) * length : 0,
          ty: speed > 0.01 ? (drop.vy / speed) * length : 0,
          tint: drop.tint
        });
      }

      if (!pipeline || !gl) {
        updateFallback(target);
        return;
      }

      let slot = 0;
      for (const box of boxes) {
        if (slot >= MAX_PILLS) break;
        pillData[slot * 4] = box.cx + MARGIN;
        pillData[slot * 4 + 1] = box.cy + MARGIN;
        pillData[slot * 4 + 2] = box.hw;
        pillData[slot * 4 + 3] = box.hh;
        slot += 1;
      }
      pillData.fill(0, slot * 4);
      let count = 0;
      for (const blob of blobs) {
        if (count >= MAX_DROPS) break;
        dropData[count * 4] = blob.x + MARGIN;
        dropData[count * 4 + 1] = blob.y + MARGIN;
        dropData[count * 4 + 2] = blob.r;
        dropData[count * 4 + 3] = 0;
        tailData[count * 2] = blob.tx;
        tailData[count * 2 + 1] = blob.ty;
        const tint = blob.tint || s.pillRgb;
        tintData[count * 3] = tint[0];
        tintData[count * 3 + 1] = tint[1];
        tintData[count * 3 + 2] = tint[2];
        count += 1;
      }

      const { program, uniforms, buffer, position } = pipeline;
      gl.viewport(0, 0, canvas.width, canvas.height);
      gl.clearColor(0, 0, 0, 0);
      gl.clear(gl.COLOR_BUFFER_BIT);
      gl.useProgram(program);
      gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
      gl.enableVertexAttribArray(position);
      gl.vertexAttribPointer(position, 2, gl.FLOAT, false, 0, 0);
      gl.uniform2f(uniforms.uSize, view.width + MARGIN * 2, view.height + MARGIN * 2);
      gl.uniform1f(uniforms.uRatio, canvas.width / Math.max(1, view.width + MARGIN * 2));
      gl.uniform4fv(uniforms.uPills, pillData);
      gl.uniform4f(uniforms.uHover, hover.x + MARGIN, hover.y + MARGIN, hover.w / 2, hover.h / 2);
      gl.uniform4fv(uniforms.uDrops, dropData);
      gl.uniform2fv(uniforms.uTails, tailData);
      gl.uniform3fv(uniforms.uTints, tintData);
      gl.uniform1i(uniforms.uCount, count);
      gl.uniform1f(uniforms.uSmooth, smooth);
      gl.uniform3f(uniforms.uPill, s.pillRgb[0], s.pillRgb[1], s.pillRgb[2]);
      gl.uniform4f(uniforms.uHoverColor, s.hoverRgb[0], s.hoverRgb[1], s.hoverRgb[2], s.hoverRgb[3] * hover.alpha);
      gl.uniform1f(uniforms.uShadow, s.shadow);
      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
    };

    const frame = now => {
      raf = 0;
      const s = settings;
      const dt = Math.min(0.05, (now - last) / 1000 || 0);
      last = now;
      const emergeTime = 0.09;
      const absorbTime = 0.12;

      for (let i = drops.length - 1; i >= 0; i--) {
        const drop = drops[i];
        if (drop.state === 'waiting') {
          if (now < drop.leave) continue;
          drop.state = 'flying';
          plan(drop, drop.index, now, true);
        }
        if (drop.state === 'flying') {
          drop.emerged = Math.min(1, drop.emerged + dt / emergeTime);
          const source = pills.get(drop.source);
          if (source && drop.given < drop.mass) {
            const step = Math.min(drop.mass * (dt / emergeTime), drop.mass - drop.given);
            source.mass = Math.max(0, source.mass - step);
            source.outgoing = Math.max(0, source.outgoing - step);
            drop.given += step;
          }
          const t = clamp01((now - drop.start) / 1000 / drop.duration);
          const eased = travelEase(t);
          const [x0, y0, x1, y1, x2, y2, x3, y3] = drop.path;
          const x = bezier(x0, x1, x2, x3, eased);
          const y = bezier(y0, y1, y2, y3, eased);
          if (dt > 0) {
            drop.vx = (x - drop.x) / dt;
            drop.vy = (y - drop.y) / dt;
          }
          drop.x = x;
          drop.y = y;
          if (t >= 1) drop.state = 'absorbing';
        }
        if (drop.state === 'absorbing') {
          const step = Math.min(1 - drop.absorbed, dt / absorbTime);
          drop.absorbed += step;
          drop.vx *= 0.8;
          drop.vy *= 0.8;
          const host = pill(drop.index);
          host.mass = Math.min(1, host.mass + drop.mass * step);
          if (drop.absorbed >= 1) drops.splice(i, 1);
        }
      }

      const zeta = reduced ? 1 : 1 - 0.72 * clamp01(s.wobble);
      const omega = 26;
      const steps = Math.max(1, Math.ceil(dt / (1 / 240)));
      const h = dt / steps;
      for (const [idx, state] of pills) {
        for (let n = 0; n < steps; n++) {
          state.velocity += (omega * omega * (state.mass - state.scale) - 2 * zeta * omega * state.velocity) * h;
          state.scale += state.velocity * h;
        }
        if (idx !== target && state.mass <= 0.002 && state.outgoing <= 0.002 && Math.abs(state.scale) < 0.004) {
          pills.delete(idx);
        }
      }

      if (hover.index >= 0 && rects[hover.index]) {
        const rect = rects[hover.index];
        const k = 1 - Math.exp(-dt / 0.06);
        hover.x += (rect.x + rect.w / 2 - hover.x) * k;
        hover.y += (rect.y + rect.h / 2 - hover.y) * k;
        hover.w += (rect.w - hover.w) * k;
        hover.h += (rect.h - hover.h) * k;
      }
      const hoverGoal = s.hoverEffect && hover.index >= 0 && hover.index !== target ? 1 : 0;
      hover.alpha += (hoverGoal - hover.alpha) * (1 - Math.exp(-dt / 0.08));

      render();

      const host = pills.get(target);
      const resting =
        drops.length === 0 &&
        pills.size === 1 &&
        host &&
        Math.abs(host.mass - host.scale) < 0.0008 &&
        Math.abs(host.velocity) < 0.004;
      const hoverRect = rects[hover.index];
      const hovering =
        Math.abs(hoverGoal - hover.alpha) > 0.003 ||
        (hoverRect && Math.abs(hoverRect.x + hoverRect.w / 2 - hover.x) + Math.abs(hoverRect.w - hover.w) > 0.1);
      if (!resting || hovering) raf = requestAnimationFrame(frame);
      else {
        host.mass = 1;
        host.scale = 1;
        host.velocity = 0;
        hover.alpha = hoverGoal;
        render();
      }
    };

    const wake = () => {
      if (raf) return;
      last = performance.now();
      raf = requestAnimationFrame(frame);
    };

    const settle = idx => {
      pills.clear();
      drops.length = 0;
      target = idx;
      current = idx;
      const state = pill(idx);
      state.mass = 1;
      state.scale = 1;
      state.velocity = 0;
      items.forEach((el, i) => {
        if (i === idx) {
          el.setAttribute('data-active', '');
          el.classList.add('active');
        } else {
          el.removeAttribute('data-active');
          el.classList.remove('active');
        }
      });
      if (!pipeline || !gl) updateFallback(idx);
      render();
    };

    const go = (idx, animate) => {
      if (idx === target) return;
      if (!rects[idx] || !animate || reduced || target < 0) {
        settle(idx);
        return;
      }
      const now = performance.now();
      target = idx;
      current = idx;
      pill(idx);
      if (!pipeline || !gl) updateFallback(idx);
      for (const drop of drops) {
        drop.index = idx;
        if (drop.state === 'flying') plan(drop, idx, now, false);
        if (drop.state === 'absorbing') {
          drop.state = 'flying';
          drop.mass *= 1 - drop.absorbed;
          drop.absorbed = 0;
          plan(drop, idx, now, false);
        }
      }
      for (const from of [...pills.keys()]) {
        if (from !== idx) emit(from, idx, now);
      }
      wake();
    };

    const onOver = event => {
      const item = event.target.closest('[data-gooey-index]');
      if (!item || !list.contains(item)) return;
      const idx = Number(item.dataset.gooeyIndex);
      const rect = rects[idx];
      if (!rect) return;
      if (hover.alpha < 0.01) {
        hover.x = rect.x + rect.w / 2;
        hover.y = rect.y + rect.h / 2;
        hover.w = rect.w;
        hover.h = rect.h;
      }
      hover.index = idx;
      wake();
    };

    const onLeave = () => {
      hover.index = -1;
      wake();
    };

    // Item click: smoothly animate pill before navigating
    list.addEventListener('click', event => {
      const link = event.target.closest('.gooey-nav__link');
      if (!link) return;
      const item = link.closest('[data-gooey-index]');
      if (!item) return;
      const idx = Number(item.dataset.gooeyIndex);
      if (isNaN(idx)) return;

      const href = link.getAttribute('href');
      const isHashOrEmpty = !href || href === '#' || href.startsWith('javascript:');

      // Trigger onChange callback
      if (typeof userOptions.onChange === 'function') {
        userOptions.onChange(idx, { label: link.textContent.trim(), href });
      }

      // Synchronize active attributes
      items.forEach((el, i) => {
        if (i === idx) {
          el.setAttribute('data-active', '');
          el.classList.add('active');
        } else {
          el.removeAttribute('data-active');
          el.classList.remove('active');
        }
      });
      if (!pipeline || !gl) updateFallback(idx);

      if (isHashOrEmpty) {
        event.preventDefault();
        go(idx, true);
        return;
      }

      // Normal navigation: trigger gooey liquid droplet flight before navigating
      if (!event.ctrlKey && !event.metaKey && !event.shiftKey && event.button === 0) {
        event.preventDefault();
        go(idx, true);
        setTimeout(() => {
          window.location.href = href;
        }, Math.min(settings.animationTime * 0.45, 280));
      }
    });

    const resizeObserver = new ResizeObserver(() => {
      measure();
      if (!drops.length) {
        settle(target < 0 ? initialActive : target);
      } else {
        if (!pipeline || !gl) updateFallback(target);
        render();
      }
    });
    resizeObserver.observe(stage);
    resizeObserver.observe(list);

    list.addEventListener('pointerover', onOver);
    list.addEventListener('pointerleave', onLeave);

    measure();
    settle(initialActive);
    document.fonts?.ready?.then(() => {
      measure();
      if (!drops.length) settle(target);
    });

    const engine = {
      go: index => go(index, true),
      settle: index => settle(index),
      refresh: () => {
        measure();
        render();
        wake();
      }
    };
    navEl._gooeyNavEngine = engine;
    return engine;
  }

  /**
   * Universal GooeyNav Component API:
   *
   * @param {HTMLElement|string|Object} containerOrOptions Target element or configuration options
   * @param {Object} [maybeOptions] Configuration options if target element was first argument
   * @returns {Object|null} GooeyNav engine instance
   */
  function GooeyNav(containerOrOptions, maybeOptions) {
    let target = containerOrOptions;
    let options = maybeOptions || {};
    if (typeof containerOrOptions === 'object' && !(containerOrOptions instanceof HTMLElement) && !containerOrOptions.nodeType) {
      options = containerOrOptions;
      target = options.target || options.container || null;
    }
    if (typeof target === 'string') {
      target = document.querySelector(target);
    }
    if (!target) {
      target = document.querySelector('.gooey-nav');
    }
    if (!target) return null;
    return setupGooeyNav(target, options);
  }

  GooeyNav.init = setupGooeyNav;

  // Auto initialize on DOM ready for any .gooey-nav present in template
  const init = () => {
    document.querySelectorAll('.gooey-nav').forEach(el => setupGooeyNav(el));
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

  window.GooeyNav = GooeyNav;
})();
