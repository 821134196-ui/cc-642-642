<script>
  import { onMount } from 'svelte';
  import { api, yuan } from '../api.js';

  let orders = [];
  let orderNo = '';
  let error = '';
  let loading = false;

  onMount(async () => {
    try {
      orders = await api.orders();
    } catch (e) {
      error = e.message;
    }
  });

  function go() {
    if (!orderNo.trim()) return;
    window.location.hash = '#/order/' + encodeURIComponent(orderNo.trim());
  }
</script>

<div class="card">
  <h2>调出原订单</h2>
  <form on:submit|preventDefault={go}>
    <input bind:value={orderNo} placeholder="输入订单号，如 ORD-1001" size="28" />
    <button type="submit" disabled={!orderNo.trim()}>查询</button>
  </form>
  {#if error}<div class="error">{error}</div>{/if}
</div>

<div class="card">
  <h2>近期订单</h2>
  <table>
    <thead>
      <tr><th>订单号</th><th>顾客</th><th>下单时间</th><th>行数</th><th>金额</th><th></th></tr>
    </thead>
    <tbody>
      {#each orders as o}
        <tr>
          <td>{o.orderNo}</td>
          <td>{o.customerName}</td>
          <td class="muted">{o.createdAt}</td>
          <td>{o.itemCount}</td>
          <td class="money">{yuan(o.totalCents)}</td>
          <td><a href={'#/order/' + o.orderNo}>办理退货 →</a></td>
        </tr>
      {/each}
    </tbody>
  </table>
  {#if loading}<div class="muted">加载中…</div>{/if}
</div>
