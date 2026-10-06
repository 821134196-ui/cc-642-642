const BASE = '/api';

async function req(path, opts = {}) {
  const init = { method: opts.method || 'GET' };
  if (opts.body instanceof FormData) {
    init.body = opts.body;
  } else if (opts.body !== undefined) {
    init.headers = { 'Content-Type': 'application/json' };
    init.body = JSON.stringify(opts.body);
  }
  const res = await fetch(BASE + path, init);
  if (!res.ok) {
    let msg = res.statusText;
    try {
      const j = await res.json();
      if (j && j.error) msg = j.error;
    } catch (_) { /* keep default */ }
    const err = new Error(msg);
    err.status = res.status;
    throw err;
  }
  return res.json();
}

export const api = {
  orders: () => req('/orders'),
  order: (no) => req('/orders/' + encodeURIComponent(no)),
  inventory: () => req('/inventory'),
  getReturn: (id) => req('/returns/' + id),
  createReturn: (orderItemId, counterId, idempotencyKey) =>
    req('/returns', { method: 'POST', body: { orderItemId, counterId, idempotencyKey } }),
  updateLines: (id, lines, inspectionPassed) =>
    req(`/returns/${id}/lines`, { method: 'PUT', body: { lines, inspectionPassed } }),
  quote: (id) => req(`/returns/${id}/quote`, { method: 'POST', body: {} }),
  confirm: (id, counterId) => req(`/returns/${id}/confirm`, { method: 'POST', body: { counterId } }),
  uploadPhoto: (id, file, note) => {
    const fd = new FormData();
    fd.append('file', file);
    if (note) fd.append('note', note);
    return req(`/returns/${id}/photos`, { method: 'POST', body: fd });
  },
  addNote: (id, author, text) => req(`/returns/${id}/notes`, { method: 'POST', body: { author, text } })
};

/** 分 → 元 */
export function yuan(cents) {
  return '¥' + (cents / 100).toFixed(2);
}

export const KIND_LABEL = { MAIN: '商品', ACCESSORY: '配件', GIFT: '赠品' };
export const CATEGORY_LABEL = {
  PRODUCT_MISSING: '商品缺失',
  PRODUCT_DAMAGED: '商品破损',
  ACCESSORY_MISSING: '配件缺失',
  ACCESSORY_DAMAGED: '配件破损',
  GIFT_MISSING: '赠品缺失',
  GIFT_DAMAGED: '赠品破损'
};
export const STATUS_LABEL = { INSPECTING: '核对中', QUOTED: '待顾客确认', REFUNDED: '已退款' };
