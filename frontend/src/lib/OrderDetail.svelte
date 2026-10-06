<script>
  import { onMount } from 'svelte';
  import { api, yuan } from '../api.js';
  import ReturnRecord from './ReturnRecord.svelte';

  export let orderNo;
  export let counterId;

  let order = null;
  let error = '';
  let busyItem = null;
  /** 每个订单行一个幂等键：重复点击/重试都指向同一笔退货单 */
  const idemKeys = new Map();

  async function load() {
    error = '';
    try {
      order = await api.order(orderNo);
    } catch (e) {
      error = e.message;
    }
  }

  onMount(load);

  async function startReturn(item) {
    if (busyItem) return;
    busyItem = item.id;
    error = '';
    try {
      if (!idemKeys.has(item.id)) {
        idemKeys.set(item.id, crypto.randomUUID());
      }
      const ret = await api.createReturn(item.id, counterId, idemKeys.get(item.id));
      window.location.hash = '#/return/' + ret.id;
    } catch (e) {
      error = e.message;
      await load();
    } finally {
      busyItem = null;
    }
  }
</script>

{#if error}<div class="card error">{error}</div>{/if}

{#if order}
  <div class="card">
    <h2>订单 {order.orderNo}</h2>
    <div class="muted">顾客：{order.customerName}（{order.customerPhone || '—'}） · 下单：{order.createdAt}</div>
  </div>

  {#each order.items as item}
    <div class="card">
      <h2>
        {item.giftBoxName}
        <span class="tag">{item.giftBoxSku}</span>
        {#if item.status === 'RETURNED'}<span class="tag gift">已退货</span>{/if}
      </h2>
      <div class="muted">数量 ×{item.qty} · 售出组合价 <span class="money">{yuan(item.comboPriceCents)}</span></div>
      <div style="margin-top:12px">
        {#if item.returnId}
          <a class="btn-link" href={'#/return/' + item.returnId}>查看退货处理（退货单 #{item.returnId}）→</a>
        {:else}
          <button on:click={() => startReturn(item)} disabled={busyItem === item.id}>
            {busyItem === item.id ? '建单中…' : '办理退货'}
          </button>
        {/if}
      </div>

      {#if item.returnId}
        <ReturnRecord returnId={item.returnId} />
      {/if}
    </div>
  {/each}
{/if}

<style>
  .btn-link { color: #b07400; text-decoration: none; font-size: 14px; }
</style>
