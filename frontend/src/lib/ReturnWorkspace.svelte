<script>
  import { onMount } from 'svelte';
  import { api } from '../api.js';
  import InspectReturn from './InspectReturn.svelte';
  import QuoteView from './QuoteView.svelte';
  import ReceiptView from './ReceiptView.svelte';

  export let returnId;
  export let counterId;

  let ret = null;
  let error = '';

  async function load() {
    error = '';
    try {
      ret = await api.getReturn(returnId);
    } catch (e) {
      error = e.message;
    }
  }

  onMount(load);
</script>

{#if error}<div class="card error">{error}</div>{/if}

{#if ret}
  <div class="card">
    <h2>
      退货单 #{ret.id} · {ret.giftBoxName}
      <span class="tag">{ret.orderNo}</span>
    </h2>
    <div class="muted">
      售出组合价 {(ret.totalComboCents / 100).toFixed(2)} 元 × 共 {ret.boxQty} 盒 · 建单柜台 {ret.counterId} · {ret.createdAt}
    </div>
    <div style="margin-top:8px">
      <a href={'#/order/' + ret.orderNo}>← 返回订单</a>
    </div>
  </div>

  {#if ret.status === 'INSPECTING'}
    <InspectReturn {ret} on:done={load} />
  {:else if ret.status === 'QUOTED'}
    <QuoteView {ret} {counterId} on:done={load} />
  {:else if ret.status === 'REFUNDED'}
    <ReceiptView {ret} />
  {/if}
{/if}
