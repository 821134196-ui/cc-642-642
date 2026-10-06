<script>
  import { onMount } from 'svelte';
  import OrderLookup from './lib/OrderLookup.svelte';
  import OrderDetail from './lib/OrderDetail.svelte';
  import ReturnWorkspace from './lib/ReturnWorkspace.svelte';
  import InventoryView from './lib/InventoryView.svelte';

  let route = { name: 'home', param: null };
  let counterId = localStorage.getItem('counterId') || 'COUNTER-A';

  function parseHash() {
    const h = window.location.hash.replace(/^#/, '');
    const mOrder = h.match(/^\/order\/(.+)$/);
    if (mOrder) return { name: 'order', param: decodeURIComponent(mOrder[1]) };
    const mReturn = h.match(/^\/return\/(\d+)$/);
    if (mReturn) return { name: 'return', param: mReturn[1] };
    if (h === '/inventory') return { name: 'inventory', param: null };
    return { name: 'home', param: null };
  }

  onMount(() => {
    const onChange = () => (route = parseHash());
    onChange();
    window.addEventListener('hashchange', onChange);
    return () => window.removeEventListener('hashchange', onChange);
  });

  $: localStorage.setItem('counterId', counterId);
</script>

<div class="page">
  <header>
    <a class="brand" href="#/">🎁 百货礼盒退货柜台</a>
    <nav>
      <a href="#/" class:active={route.name === 'home'}>订单查询</a>
      <a href="#/inventory" class:active={route.name === 'inventory'}>库存</a>
    </nav>
    <label class="counter">
      柜台
      <input bind:value={counterId} size="10" />
    </label>
  </header>

  <main>
    {#if route.name === 'home'}
      <OrderLookup />
    {:else if route.name === 'order'}
      <OrderDetail orderNo={route.param} {counterId} />
    {:else if route.name === 'return'}
      <ReturnWorkspace returnId={Number(route.param)} {counterId} />
    {:else if route.name === 'inventory'}
      <InventoryView />
    {/if}
  </main>
</div>

<style>
  :global(body) {
    margin: 0;
    font-family: 'PingFang SC', 'Microsoft YaHei', 'Noto Sans CJK SC', system-ui, sans-serif;
    background: #f4f5f7;
    color: #23272e;
  }
  :global(*) { box-sizing: border-box; }
  .page { max-width: 1080px; margin: 0 auto; padding: 0 16px 48px; }
  header {
    display: flex; align-items: center; gap: 24px;
    padding: 14px 0; border-bottom: 1px solid #e3e6ea; margin-bottom: 20px;
  }
  .brand { font-size: 18px; font-weight: 700; color: #8a5a00; text-decoration: none; }
  nav { display: flex; gap: 14px; flex: 1; }
  nav a { color: #555; text-decoration: none; font-size: 14px; }
  nav a.active { color: #b07400; font-weight: 600; }
  .counter { font-size: 13px; color: #666; display: flex; align-items: center; gap: 6px; }
  .counter input { padding: 4px 8px; border: 1px solid #ccc; border-radius: 6px; }

  :global(.card) {
    background: #fff; border: 1px solid #e6e8eb; border-radius: 10px;
    padding: 18px 20px; margin-bottom: 16px;
  }
  :global(h2) { font-size: 17px; margin: 0 0 12px; }
  :global(h3) { font-size: 15px; margin: 16px 0 8px; }
  :global(table) { width: 100%; border-collapse: collapse; font-size: 14px; }
  :global(th, td) { padding: 8px 10px; border-bottom: 1px solid #eef0f2; text-align: left; }
  :global(th) { color: #777; font-weight: 500; font-size: 13px; background: #fafbfc; }
  :global(button) {
    font: inherit; cursor: pointer; border: none; border-radius: 8px;
    padding: 8px 16px; background: #b07400; color: #fff;
  }
  :global(button:hover) { background: #96620a; }
  :global(button:disabled) { background: #c9cdd3; cursor: not-allowed; }
  :global(button.ghost) { background: #eef0f3; color: #333; }
  :global(button.ghost:hover) { background: #e0e4e9; }
  :global(input, select) { font: inherit; padding: 6px 8px; border: 1px solid #ccd1d6; border-radius: 6px; }
  :global(.muted) { color: #888; font-size: 13px; }
  :global(.error) { color: #c0392b; font-size: 13px; margin-top: 8px; }
  :global(.tag) {
    display: inline-block; padding: 1px 8px; border-radius: 10px;
    font-size: 12px; background: #eef0f3; color: #555;
  }
  :global(.tag.main) { background: #e8f0fe; color: #1a56c4; }
  :global(.tag.accessory) { background: #fef3e0; color: #a05e00; }
  :global(.tag.gift) { background: #e9f7ec; color: #1e7a38; }
  :global(.money) { font-variant-numeric: tabular-nums; }
</style>
