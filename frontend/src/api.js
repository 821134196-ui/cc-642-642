const BASE = '';

async function request(method, url, body, headers = {}) {
  const opts = { method, headers: { ...headers } };
  if (body !== undefined) {
    if (body instanceof Uint8Array || body instanceof ArrayBuffer) {
      opts.body = body;
    } else {
      opts.headers['Content-Type'] = 'application/json';
      opts.body = JSON.stringify(body);
    }
  }
  const res = await fetch(BASE + url, opts);
  const ct = res.headers.get('content-type') || '';
  const data = ct.includes('json') ? await res.json() : await res.text();
  if (!res.ok) {
    const msg = data && data.error ? data.error : `请求失败 ${res.status}`;
    throw new Error(msg);
  }
  return data;
}

export const api = {
  listOrders: () => request('GET', '/api/orders'),
  getOrder: (id) => request('GET', `/api/orders/${id}`),
  findOrder: (code) => request('GET', `/api/orders/by-code/${encodeURIComponent(code)}`),
  createReturn: (orderId, counter) =>
    request('POST', '/api/returns', { orderId, counter }),
  getReturn: (id) => request('GET', `/api/returns/${id}`),
  updateLine: (rid, lid, payload) =>
    request('PUT', `/api/returns/${rid}/lines/${lid}`, payload),
  addNote: (rid, payload) =>
    request('POST', `/api/returns/${rid}/supervisor-notes`, payload),
  confirm: (rid) => request('POST', `/api/returns/${rid}/confirm`),
  complete: (rid) => request('POST', `/api/returns/${rid}/complete`),
  replay: (receipt) => request('POST', `/api/returns/replay-receipt/${encodeURIComponent(receipt)}`),
  stock: () => request('GET', '/api/stock'),
  async uploadPhoto(rid, file, uploadedBy) {
    const buf = new Uint8Array(await file.arrayBuffer());
    return request('POST', `/api/returns/${rid}/photos`, buf, {
      'Content-Type': file.type || 'application/octet-stream',
      // HTTP 头只接受 Latin-1，中文文件名需编码后由后端解码
      'X-Filename': encodeURIComponent(file.name),
      'X-Uploaded-By': encodeURIComponent(uploadedBy || '柜台')
    });
  }
};
