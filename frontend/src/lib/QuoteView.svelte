<script>
  import { createEventDispatcher } from 'svelte';
  import { api, yuan, CATEGORY_LABEL } from '../api.js';

  /** 退款报价：赠品缺失与商品破损分组展示报价和依据，顾客确认后办理 */
  export let ret;
  export let counterId;
  const dispatch = createEventDispatcher();

  let confirming = false;
  let editing = false;
  let error = '';

  // 分组顺序：商品破损、赠品缺失优先单独展示，其余随后
  const GROUP_ORDER = [
    'PRODUCT_DAMAGED', 'GIFT_MISSING',
    'PRODUCT_MISSING', 'ACCESSORY_MISSING', 'ACCESSORY_DAMAGED', 'GIFT_DAMAGED'
  ];

  $: groups = GROUP_ORDER
    .map((cat) => ({ cat, label: CATEGORY_LABEL[cat], items: ret.deductions.filter((d) => d.category === cat) }))
    .filter((g) => g.items.length > 0);
  $: totalDeductions = ret.deductions.reduce((s, d) => s + d.amountCents, 0);

  async function confirm() {
    if (confirming) return;
    confirming = true;
    error = '';
    try {
      await api.confirm(ret.id, counterId);
      dispatch('done');
    } catch (e) {
      error = e.message;
    } finally {
      confirming = false;
    }
  }

  async function backToEdit() {
    if (editing) return;
    editing = true;
    error = '';
    try {
      // 原值回写以退回“核对中”状态，旧报价作废
      await api.updateLines(
        ret.id,
        ret.lines.map((l) => ({ lineId: l.id, receivedQty: l.receivedQty, intactQty: l.intactQty, damagedQty: l.damagedQty })),
        ret.inspectionPassed
      );
      dispatch('done');
    } catch (e) {
      error = e.message;
    } finally {
      editing = false;
    }
  }
</script>

<div class="card">
  <h2>退款报价（待顾客确认）</h2>

  <div class="formula">
    <div><span class="muted">售出组合价</span><b class="money">{yuan(ret.totalComboCents)}</b></div>
    <div class="op">−</div>
    <div><span class="muted">扣减合计</span><b class="money deduct">{yuan(totalDeductions)}</b></div>
    <div class="op">=</div>
    <div><span class="muted">应退金额</span><b class="money refund">{yuan(ret.quotedAmountCents)}</b></div>
  </div>

  {#if groups.length === 0}
    <div class="muted" style="margin-top:10px">组件齐全完好，无扣减，全额退款。</div>
  {/if}

  {#each groups as g}
    <h3>{g.label}</h3>
    <table>
      <thead><tr><th>组件</th><th>数量</th><th>分摊单价</th><th>扣减金额</th><th>依据</th></tr></thead>
      <tbody>
        {#each g.items as d}
          <tr>
            <td>{d.name}</td>
            <td>×{d.qty}</td>
            <td class="money">{yuan(d.unitPriceCents)}</td>
            <td class="money deduct">−{yuan(d.amountCents)}</td>
            <td class="muted">{d.basis}</td>
          </tr>
        {/each}
      </tbody>
    </table>
  {/each}

  {#if error}<div class="error">{error}</div>{/if}

  <div style="margin-top:14px; display:flex; gap:10px">
    <button on:click={confirm} disabled={confirming}>
      {confirming ? '办理中…' : `顾客确认，退款 ${yuan(ret.quotedAmountCents)}`}
    </button>
    <button class="ghost" on:click={backToEdit} disabled={editing}>
      {editing ? '退回中…' : '← 返回修改核对'}
    </button>
  </div>
  <div class="muted" style="margin-top:8px">确认后即办理退款并按核对结果复原库存；同一退货单重复确认只会生效一次。</div>
</div>

<style>
  .formula { display: flex; gap: 18px; align-items: baseline; padding: 12px; background: #faf8f2; border-radius: 8px; }
  .formula b { display: block; font-size: 20px; margin-top: 2px; }
  .op { font-size: 20px; color: #999; }
  .deduct { color: #c0392b; }
  .refund { color: #1e7a38; }
</style>
