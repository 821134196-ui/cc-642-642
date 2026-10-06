<script>
  import { onMount } from 'svelte';
  import { api } from '../api.js';

  let view = null;
  let error = '';

  async function load() {
    try {
      view = await api.inventory();
    } catch (e) {
      error = e.message;
    }
  }

  onMount(load);

  $: boxes = view ? view.rows.filter((r) => r.skuKind === 'BOX') : [];
  $: components = view ? view.rows.filter((r) => r.skuKind === 'COMPONENT') : [];
</script>

{#if error}<div class="card error">{error}</div>{/if}

<div class="card">
  <h2>整盒库存</h2>
  <table>
    <thead><tr><th>礼盒</th><th>SKU</th><th>状态</th><th>数量</th></tr></thead>
    <tbody>
      {#each boxes as r}
        <tr>
          <td>{r.name}</td><td>{r.sku}</td>
          <td><span class="tag {r.state === 'SELLABLE' ? 'gift' : 'accessory'}">{r.state === 'SELLABLE' ? '可售' : '破损'}</span></td>
          <td>{r.qty}</td>
        </tr>
      {/each}
      {#if boxes.length === 0}<tr><td colspan="4" class="muted">暂无整盒库存</td></tr>{/if}
    </tbody>
  </table>
</div>

<div class="card">
  <h2>散件库存（拆散组件，各按状态存放，不计入整盒）</h2>
  <table>
    <thead><tr><th>组件</th><th>SKU</th><th>状态</th><th>数量</th></tr></thead>
    <tbody>
      {#each components as r}
        <tr>
          <td>{r.name}</td><td>{r.sku}</td>
          <td><span class="tag {r.state === 'SELLABLE' ? 'gift' : 'accessory'}">{r.state === 'SELLABLE' ? '可售' : '破损'}</span></td>
          <td>{r.qty}</td>
        </tr>
      {/each}
      {#if components.length === 0}<tr><td colspan="4" class="muted">暂无散件库存</td></tr>{/if}
    </tbody>
  </table>
</div>

{#if view && view.recentTxns.length > 0}
  <div class="card">
    <h2>最近库存流水</h2>
    <table>
      <thead><tr><th>#</th><th>SKU</th><th>形态</th><th>状态</th><th>变动</th><th>原因</th><th>退货单</th><th>时间</th></tr></thead>
      <tbody>
        {#each view.recentTxns as t}
          <tr>
            <td>{t.id}</td><td>{t.sku}</td>
            <td>{t.skuKind === 'BOX' ? '整盒' : '散件'}</td>
            <td>{t.state === 'SELLABLE' ? '可售' : '破损'}</td>
            <td class:pos={t.delta > 0} class:neg={t.delta < 0}>{t.delta > 0 ? '+' : ''}{t.delta}</td>
            <td class="muted">{t.reason}</td>
            <td>{t.returnRequestId ? '#' + t.returnRequestId : '—'}</td>
            <td class="muted">{t.createdAt}</td>
          </tr>
        {/each}
      </tbody>
    </table>
  </div>
{/if}

<style>
  .pos { color: #1e7a38; }
  .neg { color: #c0392b; }
</style>
