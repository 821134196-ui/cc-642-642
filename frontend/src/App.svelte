<script>
  import { onMount } from 'svelte';
  import { api } from './api.js';
  import ReturnWorkbench from './ReturnWorkbench.svelte';
  import StockView from './StockView.svelte';

  let tab = 'returns';
  let orders = [];
  let selectedOrder = null;
  let loading = false;
  let error = '';
  let keyword = '';

  async function loadOrders() {
    loading = true;
    error = '';
    try {
      orders = await api.listOrders();
    } catch (e) {
      error = e.message;
    } finally {
      loading = false;
    }
  }

  async function search() {
    if (!keyword.trim()) {
      return loadOrders();
    }
    loading = true;
    error = '';
    try {
      const o = await api.findOrder(keyword.trim());
      orders = [o];
    } catch (e) {
      orders = [];
      error = e.message;
    } finally {
      loading = false;
    }
  }

  async function openOrder(o) {
    selectedOrder = await api.getOrder(o.id);
  }

  async function refreshOrder() {
    if (selectedOrder) {
      selectedOrder = await api.getOrder(selectedOrder.id);
    }
  }

  function back() {
    selectedOrder = null;
    loadOrders();
  }

  onMount(loadOrders);
</script>

<div class="app-header">
  <h1>百货礼盒拆包退货与组件复原系统</h1>
  <p>调出原订单 · 逐件核对商品/配件/赠品 · 组合价分摊退款 · 整盒复原与散件入库</p>
</div>

<div class="tabs">
  <button class={tab === 'returns' ? 'active' : ''} on:click={() => (tab = 'returns')}>
    退货办理
  </button>
  <button class={tab === 'stock' ? 'active' : ''} on:click={() => (tab = 'stock')}>
    库存核对
  </button>
</div>

<div class="container">
  {#if tab === 'stock'}
    <StockView />
  {:else if selectedOrder}
    <ReturnWorkbench order={selectedOrder} on:back={back} on:changed={refreshOrder} />
  {:else}
    <div class="panel">
      <h2>调出原订单</h2>
      <div class="row">
        <input type="text" placeholder="输入订单号，如 SO-1002" bind:value={keyword}
               on:keydown={(e) => e.key === 'Enter' && search()} style="width:260px" />
        <button class="btn primary" on:click={search}>查询</button>
        <button class="btn ghost" on:click={() => { keyword = ''; loadOrders(); }}>全部订单</button>
        <span class="spacer"></span>
        <button class="btn ghost sm" on:click={loadOrders}>刷新</button>
      </div>
      {#if error}<div class="flash err" style="margin-top:12px">{error}</div>{/if}
      <p class="compare-note" style="margin-top:12px">
        提示：SO-1002 / SO-1004 为优惠成交（成交价低于礼盒模板组合价），退款按<b>售出时组合价快照</b>分摊。
      </p>
    </div>

    <div class="panel">
      {#if loading}
        <p class="muted">加载中…</p>
      {:else}
        <table>
          <thead>
            <tr>
              <th>订单号</th><th>顾客</th><th>售出柜台</th><th>礼盒</th>
              <th class="num">售出组合价</th><th>退货状态</th><th>退货办理</th><th></th>
            </tr>
          </thead>
          <tbody>
            {#each orders as o (o.id)}
              <tr>
                <td><b>{o.code}</b></td>
                <td>{o.customerName}</td>
                <td>{o.counter}</td>
                <td>{o.boxName} <span class="muted">({o.boxCode})</span></td>
                <td class="num">¥{o.comboPriceYuan}</td>
                <td>
                  {#if o.returnState === 'REFUNDED'}
                    <span class="badge bad">已退款</span>
                  {:else}
                    <span class="badge ok">未退货</span>
                  {/if}
                </td>
                <td class="muted">{o.returnCases ? o.returnCases.length : 0} 笔</td>
                <td><button class="btn primary sm" on:click={() => openOrder(o)}>办理退货</button></td>
              </tr>
            {/each}
          </tbody>
        </table>
      {/if}
    </div>
  {/if}
</div>
