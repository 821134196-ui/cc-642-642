<script>
  import { onMount } from 'svelte';
  import { api } from './api.js';

  let summary = null;
  let error = '';

  async function load() {
    error = '';
    try {
      summary = await api.stock();
    } catch (e) {
      error = e.message;
    }
  }

  $: boxEntries = summary ? summary.entries.filter((e) => e.kind === 'BOX') : [];
  $: looseEntries = summary ? summary.entries.filter((e) => e.kind === 'LOOSE') : [];

  onMount(load);
</script>

<div class="panel">
  <div class="row">
    <h2>库存核对</h2>
    <span class="spacer"></span>
    <button class="btn ghost sm" on:click={load}>刷新</button>
  </div>
  {#if error}<div class="flash err">{error}</div>{/if}
  {#if summary}
    <div class="summary-grid">
      <div class="stat">
        <div class="k">整盒种类 / 整盒总数</div>
        <div class="v">{summary.boxKinds} 种 / {summary.boxTotal} 盒</div>
      </div>
      <div class="stat">
        <div class="k">散件批次 / 散件总数</div>
        <div class="v">{summary.looseBatches} 批 / {summary.looseTotal} 件</div>
      </div>
      <div class="stat" style="grid-column: span 2">
        <div class="k">复原规则提示</div>
        <div style="font-size:12.5px;color:#4b5563;margin-top:4px;line-height:1.6">
          只有退货礼盒<b style="color:var(--ok)">全部组件完好</b>才会出现在“整盒库存”；
          存在任何破损/缺失时只在“散件库存”按完好组件登记，<b>不会</b>产生整盒。
        </div>
      </div>
    </div>
  {/if}
</div>

<div class="panel">
  <h2>整盒库存（BOX）</h2>
  {#if boxEntries.length === 0}
    <p class="muted">无整盒库存。</p>
  {:else}
    <table>
      <thead>
        <tr><th>礼盒编码</th><th>礼盒名称</th><th class="num">数量(盒)</th>
        <th>来源订单</th><th>来源退货单</th><th>备注</th><th>入库时间</th></tr>
      </thead>
      <tbody>
        {#each boxEntries as e}
          <tr>
            <td><b>{e.boxCode}</b></td>
            <td>{e.name}</td>
            <td class="num">{e.quantity}</td>
            <td>{e.sourceOrderCode}</td>
            <td>{e.sourceReturnId ? '#' + e.sourceReturnId : '—'}</td>
            <td class="muted">{e.note}</td>
            <td class="muted">{e.createdAt?.slice(0,16).replace('T',' ')}</td>
          </tr>
        {/each}
      </tbody>
    </table>
  {/if}
</div>

<div class="panel">
  <h2>散件库存（LOOSE）</h2>
  {#if looseEntries.length === 0}
    <p class="muted">无散件库存。</p>
  {:else}
    <table>
      <thead>
        <tr><th>组件</th><th>类型</th><th class="num">数量(件)</th>
        <th>来源订单</th><th>来源退货单</th><th>备注</th><th>入库时间</th></tr>
      </thead>
      <tbody>
        {#each looseEntries as e}
          <tr>
            <td><b>{e.name}</b></td>
            <td><span class="tag {e.type}">{e.typeLabel}</span></td>
            <td class="num">{e.quantity}</td>
            <td>{e.sourceOrderCode}</td>
            <td>{e.sourceReturnId ? '#' + e.sourceReturnId : '—'}</td>
            <td class="muted">{e.note}</td>
            <td class="muted">{e.createdAt?.slice(0,16).replace('T',' ')}</td>
          </tr>
        {/each}
      </tbody>
    </table>
  {/if}
</div>
