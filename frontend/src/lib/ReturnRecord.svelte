<script>
  import { onMount } from 'svelte';
  import { api, yuan, KIND_LABEL, CATEGORY_LABEL, STATUS_LABEL } from '../api.js';

  /** 原订单下回看的退货处理记录（只读）：核对、扣减、退款、照片、主管意见 */
  export let returnId;

  let ret = null;
  let error = '';

  onMount(async () => {
    try {
      ret = await api.getReturn(returnId);
    } catch (e) {
      error = e.message;
    }
  });
</script>

{#if error}<div class="error">{error}</div>{/if}

{#if ret}
  <div class="record">
    <h3>退货处理记录 · 退货单 #{ret.id} <span class="tag">{STATUS_LABEL[ret.status]}</span></h3>

    <table>
      <thead>
        <tr>
          <th>组件</th><th>类型</th><th>应有</th><th>实收</th><th>完好</th><th>破损</th><th>缺失</th><th>分摊价</th>
        </tr>
      </thead>
      <tbody>
        {#each ret.lines as l}
          <tr>
            <td>{l.name}</td>
            <td><span class="tag {l.kind.toLowerCase()}">{KIND_LABEL[l.kind]}</span></td>
            <td>{l.expectedQty}</td>
            <td>{l.receivedQty}</td>
            <td>{l.intactQty}</td>
            <td class:warn={l.damagedQty > 0}>{l.damagedQty}</td>
            <td class:warn={l.missingQty > 0}>{l.missingQty}</td>
            <td class="money">{yuan(l.allocatedTotalCents)}</td>
          </tr>
        {/each}
      </tbody>
    </table>
    <div class="muted" style="margin-top:6px">
      检查结论：{ret.inspectionPassed ? '合格' : '不合格/未通过'} · 办理柜台：{ret.counterId}
    </div>

    {#if ret.deductions.length > 0}
      <h3>扣减明细</h3>
      <table>
        <thead><tr><th>类别</th><th>组件</th><th>数量</th><th>金额</th><th>依据</th></tr></thead>
        <tbody>
          {#each ret.deductions as d}
            <tr>
              <td>{CATEGORY_LABEL[d.category]}</td>
              <td>{d.name}</td>
              <td>×{d.qty}</td>
              <td class="money">−{yuan(d.amountCents)}</td>
              <td class="muted">{d.basis}</td>
            </tr>
          {/each}
        </tbody>
      </table>
    {/if}

    {#if ret.refund}
      <h3>退款</h3>
      <div>
        回执号 <b>{ret.refund.receiptNo}</b> · 金额 <b class="money">{yuan(ret.refund.amountCents)}</b>
        · 支付流水 {ret.refund.paymentTxnId} · {ret.refund.createdAt} · 柜台 {ret.refund.counterId}
      </div>
    {/if}

    {#if ret.photos.length > 0}
      <h3>检查照片</h3>
      <div class="photos">
        {#each ret.photos as p}
          <figure>
            <a href={p.url} target="_blank" rel="noreferrer"><img src={p.url} alt={p.note || p.originalName} /></a>
            {#if p.note}<figcaption>{p.note}</figcaption>{/if}
          </figure>
        {/each}
      </div>
    {/if}

    {#if ret.notes.length > 0}
      <h3>主管意见</h3>
      {#each ret.notes as n}
        <div class="note"><b>{n.author}</b>：<span>{n.text}</span> <span class="muted">（{n.createdAt}）</span></div>
      {/each}
    {/if}
  </div>
{/if}

<style>
  .record { margin-top: 16px; border-top: 1px dashed #e0e3e8; padding-top: 12px; }
  .warn { color: #c0392b; font-weight: 600; }
  .photos { display: flex; gap: 12px; flex-wrap: wrap; }
  figure { margin: 0; width: 140px; }
  figure img { width: 140px; height: 100px; object-fit: cover; border-radius: 8px; border: 1px solid #e3e6ea; }
  figcaption { font-size: 12px; color: #777; margin-top: 4px; }
  .note { font-size: 14px; margin: 4px 0; }
</style>
