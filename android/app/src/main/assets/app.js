// Global App State
let indicatorsData = [];
let selectedItem = null;
let currentInterval = '5m';
let chartInstance = null;
let candleSeries = null;
let volumeSeries = null;
let ma5Series = null;
let ma20Series = null;
let previousPrices = {};

// DOM Elements
const gridContainer = document.getElementById('dashboard-grid');
const lastUpdatedElem = document.getElementById('last-updated-time');
const refreshBtn = document.getElementById('refresh-btn');
const widgetToggleBtn = document.getElementById('widget-toggle-btn');
const widgetScreen = document.getElementById('widget-screen');
const widgetCloseBtn = document.getElementById('widget-close-btn');
const widgetGrid = document.getElementById('widget-grid');

const detailScreen = document.getElementById('detail-screen');
const detailCloseBtn = document.getElementById('detail-close-btn');
const detailNameElem = document.getElementById('detail-name');
const detailSubElem = document.getElementById('detail-sub');
const detailPriceElem = document.getElementById('detail-price');
const detailBadgeElem = document.getElementById('detail-badge');
const chartWrapper = document.getElementById('detail-chart-wrapper');
const valueDetailView = document.getElementById('value-detail-view');
const legendMa5 = document.getElementById('legend-ma5');
const legendMa20 = document.getElementById('legend-ma20');

// Safe HTTP Get Helper (Supports AndroidBridge Native Network & Browser Fetch)
async function httpGet(url) {
  if (window.AndroidBridge && typeof window.AndroidBridge.fetchUrl === 'function') {
    try {
      const res = window.AndroidBridge.fetchUrl(url);
      if (res && !res.startsWith('ERROR:')) {
        return res;
      }
    } catch (e) {
      console.warn('AndroidBridge error:', e);
    }
  }

  const response = await fetch(url, { cache: 'no-store' });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return await response.text();
}

// Format Price
function formatPrice(val, curr) {
  if (val === null || val === undefined || val === '-' || isNaN(val)) return '-';
  const num = Number(val);
  if (curr === 'KRW') {
    return '₩' + Math.round(num).toLocaleString('ko-KR');
  } else if (curr === '$') {
    return '$' + num.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  } else {
    return num.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }
}

// Format Change Pct
function formatChangePct(pct) {
  if (pct === null || pct === undefined || isNaN(pct)) return '0.00%';
  const num = Number(pct);
  const sign = num > 0 ? '+' : '';
  return `${sign}${num.toFixed(2)}%`;
}

// Scrape IG Price Ticket from HTML string
function parseIgHtml(html) {
  try {
    const bidM = html.match(/data-field="BID"[^>]*>([^<]+)</);
    const ofrM = html.match(/data-field="OFR"[^>]*>([^<]+)</);
    const cpcM = html.match(/data-field="CPC"[^>]*>([^<]+)</);
    const px = (bidM && bidM[1]) ? bidM[1].trim() : ((ofrM && ofrM[1]) ? ofrM[1].trim() : null);
    const chg = (cpcM && cpcM[1]) ? parseFloat(cpcM[1].trim()) : 0.0;
    return { price: px, changePct: chg };
  } catch (e) {
    return { price: null, changePct: 0.0 };
  }
}

// Universal Indicator Fetcher
async function fetchAllIndicators() {
  const isLocalFile = window.location.protocol === 'file:';

  if (!isLocalFile && !window.AndroidBridge) {
    try {
      const res = await fetch('/api/indicators', { cache: 'no-store' });
      if (res.ok) {
        const data = await res.json();
        if (data.items && data.items.length > 0) return data.items;
      }
    } catch (e) {
      console.log('Local server fallback');
    }
  }

  try {
    const [worldText, marketHtml, igTechHtml, igOilHtml] = await Promise.allSettled([
      httpGet('https://hlkr.co.kr/api/world'),
      httpGet('https://hlkr.co.kr/market'),
      httpGet('https://www.ig.com/en/indices/markets-indices/weekend-us-tech-100-e1'),
      httpGet('https://www.ig.com/en/indices/markets-indices/weekend-oil---us-crude')
    ]);

    let worldQuotes = {};
    if (worldText.status === 'fulfilled' && worldText.value) {
      try { worldQuotes = JSON.parse(worldText.value).quotes || {}; } catch (e) {}
    }

    const marketMap = {};
    if (marketHtml.status === 'fulfilled' && marketHtml.value) {
      const unescaped = marketHtml.value.replace(/\\"/g, '"').replace(/\\\//g, '/');
      const matches = unescaped.matchAll(/\{"asset":\{"symbol":"([^"]+)"[^}]*\},"hl":(\{.*?\})?,"refPriceKrw":([0-9\.]+)?,"refSource":"([^"]+)",("naver":(\{.*?\})?,)?"perpKrw":([0-9\.]+)?,"premiumPct":([0-9\.-]+)?/g);
      for (const m of matches) {
        try {
          const sym = m[1];
          const hlObj = m[2] ? JSON.parse(m[2]) : null;
          const naverObj = m[6] ? JSON.parse(m[6]) : null;
          marketMap[sym] = { hl: hlObj, naver: naverObj };
        } catch (e) {}
      }
    }

    function getStock(sym, fallbackPx, fallbackChg) {
      const item = marketMap[sym];
      if (item && item.hl) {
        const px = item.hl.markPx || fallbackPx;
        const prev = item.hl.prevDayPx;
        const chg = prev ? Number(((px - prev) / prev * 100).toFixed(2)) : fallbackChg;
        return [px, chg];
      }
      return [fallbackPx, fallbackChg];
    }

    const [kr200_px, kr200_chg] = getStock('KR200', 882.18, 0.55);

    const nqQuote = worldQuotes['NQ=F'] || worldQuotes['%5EIXIC'] || {};
    const nasdaq_px = nqQuote.price || 28534;
    const nasdaq_chg = nqQuote.changePct !== undefined ? nqQuote.changePct : -3.48;

    let igTech = { price: 28488.5, changePct: 0.39 };
    if (igTechHtml.status === 'fulfilled' && igTechHtml.value) {
      const parsed = parseIgHtml(igTechHtml.value);
      if (parsed.price) igTech = parsed;
    }

    let igOil = { price: 10198.6, changePct: -0.32 };
    if (igOilHtml.status === 'fulfilled' && igOilHtml.value) {
      const parsed = parseIgHtml(igOilHtml.value);
      if (parsed.price) igOil = parsed;
    }

    const [skhx_px, skhx_chg] = getStock('SKHX', 1057.3, 1.53);
    const [smsn_px, smsn_chg] = getStock('SMSN', 164.17, 1.41);

    let sem_px = 1196000, sem_chg = 0.0;
    if (marketMap['SAMSUNGEM'] && marketMap['SAMSUNGEM'].naver) {
      const n = marketMap['SAMSUNGEM'].naver;
      sem_px = n.krxClose || sem_px;
      if (n.prevClose) sem_chg = Number(((sem_px - n.prevClose) / n.prevClose * 100).toFixed(2));
    }

    const [mu_px, mu_chg] = getStock('MU', 905.19, 0.82);
    const [pltr_px, pltr_chg] = getStock('PLTR', 185.29, 0.38);
    const [nvda_px, nvda_chg] = getStock('NVDA', 204.67, 0.78);
    const [meta_px, meta_chg] = getStock('META', 615.04, 0.16);
    const [tsla_px, tsla_chg] = getStock('TSLA', 400.73, 0.47);

    return [
      { id: "kr200", index: 1, name: "위켄드 코스피200", symbol: "KR200", source: "hlkr.co.kr", hasChart: true, price: kr200_px, changePct: kr200_chg, currency: "pt" },
      { id: "nasdaq", index: 2, name: "위켄드 나스닥100", symbol: "NQ100", source: "hlkr.co.kr", hasChart: false, price: nasdaq_px, changePct: nasdaq_chg, currency: "pt" },
      { id: "ig_tech100", index: 3, name: "Weekend US Tech 100", symbol: "TECH100", source: "ig.com", hasChart: false, price: igTech.price, changePct: igTech.changePct, currency: "pt" },
      { id: "ig_oil", index: 4, name: "Weekend Oil - US Crude", symbol: "OIL (WTI)", source: "ig.com", hasChart: false, price: igOil.price, changePct: igOil.changePct, currency: "pt" },
      { id: "skhx", index: 5, name: "SK하이닉스", symbol: "SKHX", source: "hlkr.co.kr", hasChart: true, price: skhx_px, changePct: skhx_chg, currency: "$" },
      { id: "smsn", index: 6, name: "삼성전자", symbol: "SMSN", source: "hlkr.co.kr", hasChart: true, price: smsn_px, changePct: smsn_chg, currency: "$" },
      { id: "samsungem", index: 7, name: "삼성전기", symbol: "SAMSUNGEM", source: "hlkr.co.kr", hasChart: false, price: sem_px, changePct: sem_chg, currency: "KRW" },
      { id: "mu", index: 8, name: "마이크론", symbol: "MU", source: "hlkr.co.kr/stocks/mu", hasChart: true, price: mu_px, changePct: mu_chg, currency: "$" },
      { id: "pltr", index: 9, name: "팔란티어", symbol: "PLTR", source: "hlkr.co.kr/stocks/pltr", hasChart: true, price: pltr_px, changePct: pltr_chg, currency: "$" },
      { id: "nvda", index: 10, name: "엔비디아", symbol: "NVDA", source: "hlkr.co.kr/stocks/nvda", hasChart: true, price: nvda_px, changePct: nvda_chg, currency: "$" },
      { id: "meta", index: 11, name: "메타", symbol: "META", source: "hlkr.co.kr/stocks/meta", hasChart: true, price: meta_px, changePct: meta_chg, currency: "$" },
      { id: "tsla", index: 12, name: "테슬라", symbol: "TSLA", source: "hlkr.co.kr/stocks/tsla", hasChart: true, price: tsla_px, changePct: tsla_chg, currency: "$" }
    ];
  } catch (err) {
    console.error('All fetch error:', err);
    return [];
  }
}

// Fetch and Render
async function fetchIndicators() {
  try {
    const data = await fetchAllIndicators();
    if (data && data.length > 0) {
      indicatorsData = data;
      renderDashboard(indicatorsData);
      renderWidget(indicatorsData);

      const now = new Date();
      const timeStr = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}:${String(now.getSeconds()).padStart(2, '0')}`;
      if (lastUpdatedElem) lastUpdatedElem.textContent = timeStr;

      if (selectedItem && detailScreen.classList.contains('open')) {
        const updated = indicatorsData.find(i => i.id === selectedItem.id);
        if (updated) {
          selectedItem = updated;
          updateDetailHeader(updated);
        }
      }
    }
  } catch (err) {
    console.error('Fetch error:', err);
  }
}

// Render Dashboard Grid (2 columns x 6 rows)
// 현재값은 작게, 등락률은 2배 이상(23px) 굵고 큼직하게 표시
function renderDashboard(items) {
  gridContainer.innerHTML = '';
  items.forEach(item => {
    const card = document.createElement('div');
    card.className = 'stock-card';
    card.id = `card-${item.id}`;

    const prevPx = previousPrices[item.id];
    const currPx = Number(item.price);
    if (prevPx !== undefined && !isNaN(currPx) && !isNaN(prevPx)) {
      if (currPx > prevPx) card.classList.add('flash-up');
      else if (currPx < prevPx) card.classList.add('flash-down');
    }
    previousPrices[item.id] = currPx;

    const chgClass = item.changePct > 0 ? 'up' : (item.changePct < 0 ? 'down' : 'flat');

    card.innerHTML = `
      <div class="card-top">
        <div class="card-title-group">
          <span class="card-index">${item.index}.</span>
          <span class="card-name">${item.name}</span>
          <span class="card-symbol">${item.symbol}</span>
        </div>
        ${item.hasChart ? '<span class="chart-badge">차트</span>' : ''}
      </div>
      <div class="card-body">
        <div class="card-price-box">
          <span class="card-price">${formatPrice(item.price, item.currency)}</span>
          <span class="card-curr">${item.currency === 'KRW' ? 'KRW' : (item.currency === '$' ? 'USD' : '지수')}</span>
        </div>
        <div class="card-change-large ${chgClass}">
          ${formatChangePct(item.changePct)}
        </div>
      </div>
    `;

    card.addEventListener('click', () => openDetail(item));
    gridContainer.appendChild(card);
  });
}

// Render 6x6 Widget View
function renderWidget(items) {
  if (!widgetGrid) return;
  widgetGrid.innerHTML = '';
  items.forEach(item => {
    const itemElem = document.createElement('div');
    itemElem.className = 'widget-item';
    const chgClass = item.changePct > 0 ? 'up' : (item.changePct < 0 ? 'down' : 'flat');
    const color = item.changePct > 0 ? 'var(--accent-up)' : (item.changePct < 0 ? 'var(--accent-down)' : 'var(--accent-flat)');

    itemElem.innerHTML = `
      <div class="widget-item-left">
        <span class="widget-item-name">${item.index}. ${item.name}</span>
        <span class="widget-item-price">${formatPrice(item.price, item.currency)}</span>
      </div>
      <span class="widget-item-pct" style="color: ${color}">${formatChangePct(item.changePct)}</span>
    `;

    itemElem.addEventListener('click', () => {
      widgetScreen.classList.remove('open');
      openDetail(item);
    });
    widgetGrid.appendChild(itemElem);
  });
}

// Open Detail Screen
function openDetail(item) {
  selectedItem = item;
  updateDetailHeader(item);

  detailScreen.classList.add('open');

  if (item.hasChart) {
    chartWrapper.style.display = 'block';
    valueDetailView.style.display = 'none';
    document.querySelector('.detail-timeframe-bar').style.display = 'flex';
    
    // Animate open first, then init chart with exact dimensions
    setTimeout(() => {
      initChart(item.symbol, currentInterval);
    }, 150);
  } else {
    chartWrapper.style.display = 'none';
    valueDetailView.style.display = 'flex';
    document.querySelector('.detail-timeframe-bar').style.display = 'none';
    renderValueDetail(item);
  }
}

// Update Detail Header
function updateDetailHeader(item) {
  detailNameElem.textContent = `${item.index}. ${item.name}`;
  detailSubElem.textContent = `${item.symbol} · ${item.source}`;
  detailPriceElem.textContent = `현재가: ${formatPrice(item.price, item.currency)}`;

  const chgClass = item.changePct > 0 ? 'up' : (item.changePct < 0 ? 'down' : 'flat');
  detailBadgeElem.className = `detail-main-change ${chgClass}`;
  detailBadgeElem.textContent = formatChangePct(item.changePct);
}

// Render Value Details for Non-Chart items
function renderValueDetail(item) {
  valueDetailView.innerHTML = `
    <div class="value-stat-card">
      <div class="stat-row">
        <span class="stat-label">종목 코드/심볼</span>
        <span class="stat-val">${item.symbol}</span>
      </div>
      <div class="stat-row">
        <span class="stat-label">실시간 현재가</span>
        <span class="stat-val" style="color: #38bdf8; font-size: 15px;">${formatPrice(item.price, item.currency)}</span>
      </div>
      <div class="stat-row">
        <span class="stat-label">금일 등락률 (24h)</span>
        <span class="stat-val" style="font-size: 20px; font-weight: 900; color: ${item.changePct > 0 ? 'var(--accent-up)' : 'var(--accent-down)'}">${formatChangePct(item.changePct)}</span>
      </div>
      <div class="stat-row">
        <span class="stat-label">데이터 수집 출처</span>
        <span class="stat-val">${item.source}</span>
      </div>
      <div class="stat-row">
        <span class="stat-label">수집 유형</span>
        <span class="stat-val">실시간 호가/시세 크롤링</span>
      </div>
    </div>
    <p class="value-notice">
      ℹ️ 본 지표는 거래소 웹사이트의 실시간 호가/시세를 크롤링한 전용 지표로,<br>
      규칙에 따라 캔들 차트 대신 정확한 실시간 수치를 제공합니다.
    </p>
  `;
}

// Initialize Lightweight Charts
async function initChart(symbol, interval) {
  const container = document.getElementById('tv-chart');
  if (!container) return;
  container.innerHTML = '';

  if (chartInstance) {
    try { chartInstance.remove(); } catch (e) {}
    chartInstance = null;
  }

  // Safety check for LightweightCharts library
  if (!window.LightweightCharts) {
    container.innerHTML = '<div style="display:flex;height:100%;align-items:center;justify-content:center;color:#94a3b8;font-size:12px;">차트 엔진 로드 중...</div>';
    setTimeout(() => initChart(symbol, interval), 500);
    return;
  }

  const containerW = container.clientWidth || 360;
  const containerH = container.clientHeight || 340;

  const chartOptions = {
    width: containerW,
    height: containerH,
    layout: {
      textColor: '#94a3b8',
      background: { type: 'solid', color: '#08090c' },
      fontSize: 10.5,
      fontFamily: '-apple-system, BlinkMacSystemFont, "Pretendard", sans-serif'
    },
    grid: {
      vertLines: { color: 'rgba(255, 255, 255, 0.04)' },
      horzLines: { color: 'rgba(255, 255, 255, 0.04)' }
    },
    crosshair: {
      mode: LightweightCharts.CrosshairMode.Normal,
      vertLine: { color: '#38bdf8', width: 1, style: 2 },
      horzLine: { color: '#38bdf8', width: 1, style: 2 }
    },
    rightPriceScale: {
      borderColor: '#1e2433',
      scaleMargins: { top: 0.1, bottom: 0.2 }
    },
    timeScale: {
      borderColor: '#1e2433',
      timeVisible: true,
      secondsVisible: false,
      rightOffset: 5
    },
    handleScroll: { mouseWheel: true, pressedMouseMove: true, horzTouchDrag: true, vertTouchDrag: false },
    handleScale: { axisPressedMouseMove: true, mouseWheel: true, pinch: true }
  };

  chartInstance = LightweightCharts.createChart(container, chartOptions);

  candleSeries = chartInstance.addCandlestickSeries({
    upColor: '#ff3b30',
    downColor: '#0a84ff',
    borderVisible: false,
    wickUpColor: '#ff3b30',
    wickDownColor: '#0a84ff'
  });

  volumeSeries = chartInstance.addHistogramSeries({
    color: '#26a69a',
    priceFormat: { type: 'volume' },
    priceScaleId: '',
    scaleMargins: { top: 0.82, bottom: 0 }
  });

  ma5Series = chartInstance.addLineSeries({
    color: '#f59e0b',
    lineWidth: 1.5,
    title: 'MA5'
  });

  ma20Series = chartInstance.addLineSeries({
    color: '#3b82f6',
    lineWidth: 1.5,
    title: 'MA20'
  });

  await loadChartData(symbol, interval);

  const resizeObserver = new ResizeObserver(() => {
    if (chartInstance && container && container.clientWidth > 0 && container.clientHeight > 0) {
      chartInstance.applyOptions({
        width: container.clientWidth,
        height: container.clientHeight
      });
    }
  });
  resizeObserver.observe(container);

  chartInstance.subscribeCrosshairMove(param => {
    if (!param || !param.time || !param.seriesData) return;
    const m5 = param.seriesData.get(ma5Series);
    const m20 = param.seriesData.get(ma20Series);
    if (m5 && legendMa5) legendMa5.textContent = `MA5: ${m5.value.toFixed(2)}`;
    if (m20 && legendMa20) legendMa20.textContent = `MA20: ${m20.value.toFixed(2)}`;
  });

  setTimeout(() => {
    if (chartInstance && container) {
      chartInstance.applyOptions({
        width: container.clientWidth,
        height: container.clientHeight
      });
      chartInstance.timeScale().fitContent();
    }
  }, 250);
}

function calculateSMA(data, period) {
  const sma = [];
  for (let i = 0; i < data.length; i++) {
    if (i < period - 1) continue;
    let sum = 0;
    for (let j = 0; j < period; j++) {
      sum += data[i - j].close;
    }
    sma.push({ time: data[i].time, value: sum / period });
  }
  return sma;
}

async function loadChartData(symbol, interval) {
  try {
    let rawCandles = [];
    const targetUrl = `https://hlkr.co.kr/api/candles/${symbol}?interval=${interval}`;
    const rawText = await httpGet(targetUrl);
    if (rawText) {
      const json = JSON.parse(rawText);
      rawCandles = json.candles || [];
    }

    if (rawCandles.length === 0) {
      console.warn('No candles returned for', symbol);
      return;
    }

    const formattedCandles = rawCandles.map(c => ({
      time: c.time,
      open: Number(c.open),
      high: Number(c.high),
      low: Number(c.low),
      close: Number(c.close)
    }));

    const formattedVolume = rawCandles.map(c => ({
      time: c.time,
      value: Number(c.volume || 0),
      color: c.close >= c.open ? 'rgba(255, 59, 48, 0.4)' : 'rgba(10, 132, 255, 0.4)'
    }));

    candleSeries.setData(formattedCandles);
    volumeSeries.setData(formattedVolume);

    const ma5Data = calculateSMA(formattedCandles, 5);
    const ma20Data = calculateSMA(formattedCandles, 20);

    ma5Series.setData(ma5Data);
    ma20Series.setData(ma20Data);

    chartInstance.timeScale().fitContent();
  } catch (err) {
    console.error('Error loading candles:', err);
  }
}

// Event Listeners
detailCloseBtn.addEventListener('click', () => {
  detailScreen.classList.remove('open');
  if (chartInstance) {
    try { chartInstance.remove(); } catch (e) {}
    chartInstance = null;
  }
});

refreshBtn.addEventListener('click', () => {
  refreshBtn.style.transform = 'rotate(180deg)';
  setTimeout(() => { refreshBtn.style.transform = 'none'; }, 300);
  fetchIndicators();
});

widgetToggleBtn.addEventListener('click', () => {
  widgetScreen.classList.add('open');
});

widgetCloseBtn.addEventListener('click', () => {
  widgetScreen.classList.remove('open');
});

document.querySelectorAll('.tf-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('.tf-btn').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    currentInterval = btn.dataset.tf;
    if (selectedItem && selectedItem.hasChart) {
      loadChartData(selectedItem.symbol, currentInterval);
    }
  });
});

fetchIndicators();
setInterval(fetchIndicators, 5000);
