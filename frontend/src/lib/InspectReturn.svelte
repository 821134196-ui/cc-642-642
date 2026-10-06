<script>
  import { createEventDispatcher } from 'svelte';
  import { api, yuan, KIND_LABEL } from '../api.js';

  /** 逐件核对：应有 vs 实收并列，登记完好/破损，缺失自动算出 */
  export let ret;
  const dispatch = createEventDispatcher();

  // 本地编辑状态
  let rows = ret.lines.map((l) => ({
    lineId: l.id,
    sku: l.sku,
    name: l.name,
    kind: l.kind,
    expectedQty: l.expectedQty,
    receivedQty: l.receivedQty,
    intactQty: l.intactQty,
    damagedQty: l.damagedQty,
    allocatedTotalCents: l.allocatedTotalCents,
    unitAllocCents: l.unitAllocCents,
    listPriceCents: l.listPriceCents
  }));
  let inspectionPassed = ret.inspectionPassed;
  let error = '';
  let saving = false;
  let quoting = false;

  // 照片与主管意见
  let photoNote = '';
  let photoFile = null;
  let uploading = false;
  let uploadMsg = '';
  let noteAuthor = '';
  let noteText = '';
  let noting = false;
  let noteMsg = '';

  function onReceivedChange(row) {
    if (row.receivedQty < 0) row.receivedQty = 0;
    if (row.receivedQty > row.expectedQty) row.receivedQty = row.expectedQty;
    // 默认实收全部完好，柜台再调破损
    row.intactQty = row.receivedQty - row.damagedQty;
    if (row.intactQty < 0) {
      row.damagedQty = row.receivedQty;
      row.intactQty = 0;
    }
  }

  function onDamagedChange(row) {
    if (row.damagedQty < 0) row.damagedQty = 0;
    if (row.damagedQty > row.receivedQty) row.damagedQty = row.receivedQty;
    row.intactQty = row.receivedQty - row.damagedQty;
  }

  function validate() {
    for (const r of rows) {
      if (r.receivedQty < 0 || r.receivedQty > r.expectedQty) {
        return `「${r.name}」实收数量须在 0 与 ${r.expectedQty} 之间`;
      }
      if (r.intactQty + r.damagedQty !== r.receivedQty) {
        return `「${r.name}」完好 + 破损 必须等于实收`;
      }
    }
    return '';
  }

  async function save() {
    error = validate();
    if (error) return false;
    saving = true;
    try {
      await api.updateLines(
        ret.id,
        rows.map((r) => ({ lineId: r.lineId, receivedQty: r.receivedQty, intactQty: r.intactQty, damagedQty: r.damagedQty })),
        inspectionPassed
      );
      return true;
    } catch (e) {
      error = e.message;
      return false;
    } finally {
      saving = false;
    }
  }

  async function makeQuote() {
    if (quoting) return;
    quoting = true;
    try {
      const ok = await save();
      if (ok) {
        await api.quote(ret.id);
        dispatch('done');
      }
    } catch (e) {
      error = e.message;
    } finally {
      quoting = false;
    }
  }

  async function upload() {
    if (!photoFile || uploading) return;
    uploading = true;
    uploadMsg = '';
    try {
      await api.uploadPhoto(ret.id, photoFile, photoNote);
      uploadMsg = '照片已上传';
      photoFile = null;
      photoNote = '';
      dispatch('done');
    } catch (e) {
      uploadMsg = '上传失败：' + e.message;
    } finally {
      uploading = false;
    }
  }

  async function addNote() {
    if (!noteAuthor.trim() || !noteText.trim() || noting) return;
    noting = true;
    noteMsg = '';
    try {
      await api.addNote(ret.id, noteAuthor.trim(), noteText.trim());
      noteMsg = '已保存';
      noteText = '';
      dispatch('done');
    } catch (e) {
      noteMsg = '保存失败：' + e.message;
    } finally {
      noting = false;
    }
  }

  $: missingTotal = rows.reduce((s, r) => s + (r.expectedQty - r.receivedQty), 0);
  $: damagedTotal = rows.reduce((s, r) => s + r.damagedQty, 0);
</script>

<div class="card">
  <h2>组件核对 <span class="muted">（左列为礼盒应有组件，右列登记实际收到情况）</span></h2>
  <table>
    <thead>
      <tr>
        <th>组件</th><th>类型</th><th>应有</th>
        <th>实收</th><th>完好</th><th>破损</th><th>缺失</th>
        <th>零售标价</th><th>分摊价/件</th>
      </tr>
    </thead>
    <tbody>
      {#each rows as r}
        <tr>
          <td>{r.name}</td>
          <td><span class="tag {r.kind.toLowerCase()}">{KIND_LABEL[r.kind]}</span></td>
          <td>{r.expectedQty}</td>
          <td>
            <input type="number" min="0" max={r.expectedQty} bind:value={r.receivedQty}
                   on:change={() => onReceivedChange(r)} class="qty" />
          </td>
          <td class="ok">{r.intactQty}</td>
          <td>
            <input type="number" min="0" max={r.receivedQty} bind:value={r.damagedQty}
                   on:change={() => onDamagedChange(r)} class="qty" />
          </td>
          <td class:warn={r.expectedQty - r.receivedQty > 0}>{r.expectedQty - r.receivedQty}</td>
          <td class="money muted">{yuan(r.listPriceCents)}</td>
          <td class="money">{yuan(r.unitAllocCents)}</td>
        </tr>
      {/each}
    </tbody>
  </table>

  <div class="summary">
    <label class="pass">
      <input type="checkbox" bind:checked={inspectionPassed} />
      检查合格（仅当组件齐全且无破损时，才会按整盒复原入库）
    </label>
    <span class="muted">缺失合计 {missingTotal} 件 · 破损合计 {damagedTotal} 件</span>
  </div>

  {#if error}<div class="error">{error}</div>{/if}

  <div style="margin-top:12px; display:flex; gap:10px">
    <button class="ghost" on:click={save} disabled={saving}>{saving ? '保存中…' : '保存核对结果'}</button>
    <button on:click={makeQuote} disabled={quoting || saving}>{quoting ? '计算中…' : '生成退款报价 →'}</button>
  </div>
</div>

<div class="card">
  <h2>检查照片</h2>
  {#if ret.photos.length > 0}
    <div class="photos">
      {#each ret.photos as p}
        <figure>
          <a href={p.url} target="_blank" rel="noreferrer"><img src={p.url} alt={p.note || p.originalName} /></a>
          {#if p.note}<figcaption>{p.note}</figcaption>{/if}
        </figure>
      {/each}
    </div>
  {/if}
  <div class="upload-row">
    <input type="file" accept="image/*" on:change={(e) => (photoFile = e.target.files[0] || null)} />
    <input bind:value={photoNote} placeholder="照片说明（可选）" size="20" />
    <button class="ghost" on:click={upload} disabled={!photoFile || uploading}>{uploading ? '上传中…' : '上传'}</button>
  </div>
  {#if uploadMsg}<div class="muted">{uploadMsg}</div>{/if}
</div>

<div class="card">
  <h2>主管意见</h2>
  {#each ret.notes as n}
    <div class="note"><b>{n.author}</b>：{n.text} <span class="muted">（{n.createdAt}）</span></div>
  {/each}
  <div class="upload-row">
    <input bind:value={noteAuthor} placeholder="主管姓名" size="10" />
    <input bind:value={noteText} placeholder="意见内容" size="40" />
    <button class="ghost" on:click={addNote} disabled={!noteAuthor.trim() || !noteText.trim() || noting}>添加</button>
  </div>
  {#if noteMsg}<div class="muted">{noteMsg}</div>{/if}
</div>

<style>
  .qty { width: 64px; }
  .ok { color: #1e7a38; }
  .warn { color: #c0392b; font-weight: 600; }
  .summary { display: flex; justify-content: space-between; align-items: center; margin-top: 10px; }
  .pass { font-size: 14px; display: flex; align-items: center; gap: 6px; }
  .upload-row { display: flex; gap: 10px; align-items: center; margin-top: 10px; flex-wrap: wrap; }
  .photos { display: flex; gap: 12px; flex-wrap: wrap; margin-bottom: 8px; }
  figure { margin: 0; width: 140px; }
  figure img { width: 140px; height: 100px; object-fit: cover; border-radius: 8px; border: 1px solid #e3e6ea; }
  figcaption { font-size: 12px; color: #777; margin-top: 4px; }
  .note { font-size: 14px; margin: 4px 0; }
</style>
