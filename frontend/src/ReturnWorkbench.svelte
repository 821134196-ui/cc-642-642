<script>
  import { createEventDispatcher } from 'svelte';
  import { api } from './api.js';

  export let order;
  const dispatch = createEventDispatcher();

  let activeId = null;
  let rc = null;
  let counter = 'A柜';
  let busy = false;
  let flash = null; // {type, text}
  let noteSupervisor = '周主管';
  let noteApproved = true;
  let noteOpinion = '';
  let photoFiles = [];

  $: cases = order.returnCases || [];

  async function reload() {
    order = await api.getOrder(order.id);
    if (activeId) {
      rc = await api.getReturn(activeId);
    }
  }

  async function notifyChanged() {
    await reload();
    dispatch('changed');
  }

  async function createCase() {
    busy = true; flash = null;
    try {
      rc = await api.createReturn(order.id, counter || '未命名柜台');
      activeId = rc.id;
      await notifyChanged();
    } catch (e) {
      flash = { type: 'err', text: e.message };
    } finally {
      busy = false;
    }
  }

  async function openCase(c) {
    activeId = c.id;
    rc = await api.getReturn(c.id);
    flash = null;
  }

  function closeCase() {
    activeId = null; rc = null; flash = null;
  }

  async function setQty(line, field, delta) {
    const payload = {
      intactQty: line.intactQty,
      damagedQty: line.damagedQty,
      missingQty: line.missingQty
    };
    payload[field] = Math.max(0, payload[field] + delta);
    // 保持三者之和 = 应有：从完好件里转移
    if (field !== 'intactQty') {
      const used = payload.damagedQty + payload.missingQty;
      payload.intactQty = line.expectedQty - used;
      if (payload.intactQty < 0) {
        flash = { type: 'err', text: '破损 + 缺失不能超过应有数量' };
        return;
      }
    }
    await saveLine(line, payload);
  }

  async function setExact(line, field, value) {
    const v = Math.max(0, Number(value) || 0);
    const payload = {
      intactQty: line.intactQty,
      damagedQty: line.damagedQty,
      missingQty: line.missingQty,
      [field]: v
    };
    const others = line.expectedQty - v;
    if (field === 'intactQty') {
      // 改动完好时，把余量优先留给缺失再破损（简单处理：按现有比例，缺省给缺失）
      payload.damagedQty = line.damagedQty;
      payload.missingQty = Math.max(0, others - payload.damagedQty);
      if (payload.damagedQty + payload.missingQty > others) {
        payload.damagedQty = Math.max(0, others);
        payload.missingQty = 0;
      }
    } else {
      payload.intactQty = others;
      if (payload.intactQty < 0) {
        flash = { type: 'err', text: '数量之和必须等于应有数量' };
        return;
      }
    }
    await saveLine(line, payload);
  }

  let saveTimer = {};
  function saveLine(line, payload) {
    clearTimeout(saveTimer[line.id]);
    return new Promise((resolve) => {
      saveTimer[line.id] = setTimeout(async () => {
        try {
          rc = await api.updateLine(rc.id, line.id, payload);
          flash = null;
        } catch (e) {
          flash = { type: 'err', text: e.message };
        }
        resolve();
      }, 120);
    });
  }

  async function addNote() {
    if (!noteOpinion.trim()) {
      flash = { type: 'err', text: '请填写主管意见' };
      return;
    }
    busy = true;
    try {
      rc = await api.addNote(rc.id, {
        supervisor: noteSupervisor, approved: noteApproved, opinion: noteOpinion
      });
      noteOpinion = '';
      await notifyChanged();
    } catch (e) {
      flash = { type: 'err', text: e.message };
    } finally {
      busy = false;
    }
  }

  async function uploadPhotos() {
    if (!photoFiles.length) return;
    busy = true; flash = null;
    try {
      for (const f of photoFiles) {
        await api.uploadPhoto(rc.id, f, counter);
      }
      photoFiles = [];
      await notifyChanged();
      flash = { type: 'ok', text: '照片已保存到本地并挂在本订单下' };
    } catch (e) {
      flash = { type: 'err', text: e.message };
    } finally {
      busy = false;
    }
  }

  async function confirm() {
    busy = true;
    try {
      rc = await api.confirm(rc.id);
      await notifyChanged();
      flash = { type: 'ok', text: '顾客已确认退款明细，等待办理退款入库' };
    } catch (e) {
      flash = { type: 'err', text: e.message };
    } finally {
      busy = false;
    }
  }

  async function complete() {
    if (!confirm1) {
      showConfirm = true;
      return;
    }
    busy = true; showConfirm = false; confirm1 = false;
    try {
      const result = await api.complete(rc.id);
      rc = result;
      await notifyChanged();
      if (result.rejected) {
        flash = { type: 'err', text: '该订单已被另一柜台办理，本笔请求被幂等拒绝：未退款、未入库。' };
      } else if (result.alreadyDone) {
        flash = { type: 'info', text: '该退货单已完成，此为重放：仍只有原退款回执，未重复退款、未重复入库。' };
      } else {
        flash = { type: 'ok', text: `办理完成：退款 ¥${result.quote.refundYuan}，回执 ${result.resultReceipt}。` };
      }
    } catch (e) {
      flash = { type: 'err', text: e.message };
    } finally {
      busy = false;
    }
  }

  let showConfirm = false;
  let confirm1 = false;

  async function replayReceipt(receipt) {
    busy = true;
    try {
      const r = await api.replay(receipt);
      flash = { type: 'info', text: `回执 ${receipt} 重放结果：${r.result}，金额 ¥${r.amountYuan}（仅回显，未再次退款，库存不变）` };
    } catch (e) {
      flash = { type: 'err', text: e.message };
    } finally {
      busy = false;
    }
  }

  const statusBadge = {
    DRAFT: ['gray', '登记中'],
    CONFIRMED: ['info', '顾客已确认'],
    COMPLETED: ['ok', '已完成'],
    REJECTED: ['bad', '并发被拒']
  };
</script>

<div class="panel">
  <div class="row">
    <button class="btn ghost sm" on:click={() => dispatch('back')}>← 返回订单列表</button>
    <span class="spacer"></span>
  </div>
  <h2 style="margin-top:10px">原订单 {order.code} · {order.boxName}</h2>
  <p class="muted">
    顾客 {order.customerName} ｜ 售出柜台 {order.counter} ｜
    售出组合价 <b style="color:#1f2430">¥{order.comboPriceYuan}</b> ｜
    {#if order.returnState === 'REFUNDED'}
      <span class="badge bad">已退款（退货权已占用）</span>
    {:else}
      <span class="badge ok">未退货</span>
    {/if}
  </p>
</div>

{#if flash}
  <div class="flash {flash.type}">{flash.text}</div>
{/if}

<!-- 处理过程回看：该订单下所有退货单 -->
<div class="panel">
  <h2>本订单的退货办理记录（{cases.length}）</h2>
  {#if cases.length === 0}
    <p class="muted">尚未办理退货。两个柜台可各自建单登记，但最终只有一笔能退款入库。</p>
  {:else}
    <table>
      <thead>
        <tr><th>#</th><th>柜台</th><th>状态</th><th class="num">退款</th><th class="num">扣减</th>
          <th>整盒复原</th><th>照片/主管意见</th><th></th></tr>
      </thead>
      <tbody>
        {#each cases as c}
          <tr class:complete={c.status === 'COMPLETED'}>
            <td>#{c.id}</td>
            <td>{c.counter}</td>
            <td><span class="badge {statusBadge[c.status][0]}">{statusBadge[c.status][1]}</span></td>
            <td class="num">{c.refundYuan ? '¥' + c.refundYuan : '—'}</td>
            <td class="num">{c.deductionYuan ? '¥' + c.deductionYuan : '—'}</td>
            <td>
              {#if c.status === 'COMPLETED'}
                {#if c.boxRestored}<span class="badge ok">恢复整盒</span>{:else}<span class="badge warn">仅散件入库</span>{/if}
              {:else}—{/if}
            </td>
            <td class="muted">{c.photos.length} 张 / {c.supervisorNotes.length} 条</td>
            <td><button class="btn ghost sm" on:click={() => openCase(c)}>查看/办理</button></td>
          </tr>
        {/each}
      </tbody>
    </table>
  {/if}

  {#if !rc}
    <div class="row" style="margin-top:14px">
      <label class="muted">本柜台：</label>
      <select bind:value={counter}>
        <option>A柜</option><option>B柜</option>
      </select>
      <button class="btn primary" on:click={createCase} disabled={busy || order.returnState === 'REFUNDED'}>
        新建退货检查单
      </button>
      {#if order.returnState === 'REFUNDED'}
        <span class="muted">订单已退款，不能再新建办理；可打开已有记录回看或重放回执。</span>
      {/if}
    </div>
  {/if}
</div>

{#if rc}
  <!-- 应有组件 vs 实际清点 并列登记表 -->
  <div class="panel">
    <div class="row">
      <h2>退货单 #{rc.id}（{rc.counter}）
        <span class="badge {statusBadge[rc.status][0]}">{statusBadge[rc.status][1]}</span>
      </h2>
      <span class="spacer"></span>
      <button class="btn ghost sm" on:click={closeCase}>收起</button>
    </div>
    <p class="compare-note">
      左列为售出时<b>应有组件</b>，右侧逐件登记<b>实际收到</b>情况；数量须满足 完好＋破损＋缺失＝应有。
      完好件可入库；破损件不可入库；缺失件无实物。
    </p>

    <table>
      <thead>
        <tr>
          <th style="width:26%">应有组件（售出组合）</th>
          <th>类型</th>
          <th class="num">应有</th>
          <th class="num">单件参考报价</th>
          <th class="num" style="color:var(--ok)">完好</th>
          <th class="num" style="color:var(--warn)">破损</th>
          <th class="num" style="color:var(--bad)">缺失</th>
          <th class="num">分摊额(行)</th>
          <th class="num">本行退款</th>
          <th class="num">本行扣减</th>
        </tr>
      </thead>
      <tbody>
        {#each rc.lines as line}
          <tr>
            <td><b>{line.component.name}</b></td>
            <td><span class="tag {line.component.type}">{line.component.typeLabel}</span></td>
            <td class="num">{line.expectedQty}</td>
            <td class="num">¥{line.component.unitQuoteYuan}</td>
            <td class="num">
              <div class="qty-ctrl">
                <button on:click={() => setQty(line, 'intactQty', -1)} disabled={rc.status !== 'DRAFT'}>−</button>
                <input type="number" value={line.intactQty}
                       on:change={(e) => setExact(line, 'intactQty', e.target.value)}
                       disabled={rc.status !== 'DRAFT'} />
                <button on:click={() => setQty(line, 'intactQty', 1)} disabled={rc.status !== 'DRAFT'}>＋</button>
              </div>
            </td>
            <td class="num">
              <div class="qty-ctrl">
                <button on:click={() => setQty(line, 'damagedQty', -1)} disabled={rc.status !== 'DRAFT'}>−</button>
                <input type="number" value={line.damagedQty}
                       on:change={(e) => setExact(line, 'damagedQty', e.target.value)}
                       disabled={rc.status !== 'DRAFT'} />
                <button on:click={() => setQty(line, 'damagedQty', 1)} disabled={rc.status !== 'DRAFT'}>＋</button>
              </div>
            </td>
            <td class="num">
              <div class="qty-ctrl">
                <button on:click={() => setQty(line, 'missingQty', -1)} disabled={rc.status !== 'DRAFT'}>−</button>
                <input type="number" value={line.missingQty}
                       on:change={(e) => setExact(line, 'missingQty', e.target.value)}
                       disabled={rc.status !== 'DRAFT'} />
                <button on:click={() => setQty(line, 'missingQty', 1)} disabled={rc.status !== 'DRAFT'}>＋</button>
              </div>
            </td>
            <td class="num">¥{line.allocatedYuan}</td>
            <td class="num" style="color:var(--ok)">¥{line.refundYuan}</td>
            <td class="num" style="color:{line.deductionYuan === '0.00' ? '#6b7280' : 'var(--bad)'}">
              ¥{line.deductionYuan}
            </td>
          </tr>
          {#if line.charges && line.charges.length}
            <tr>
              <td colspan="10" style="background:#fcfbff">
                {#each line.charges as ch}
                  <div class="charge-box">
                    <div class="row">
                      <span class="badge {ch.status === 'MISSING' ? 'bad' : 'warn'}">{ch.title} ×{ch.quantity}</span>
                      <span class="spacer"></span>
                      <b>扣减 ¥{ch.amountYuan}</b>
                      <span class="muted">（单件参考报价 ¥{ch.unitQuoteYuan}）</span>
                    </div>
                    <div class="basis">依据：{ch.basis}</div>
                  </div>
                {/each}
              </td>
            </tr>
          {/if}
        {/each}
      </tbody>
    </table>
  </div>

  <!-- 退款明细：组合价分摊 -->
  <div class="panel">
    <h2>退款明细（按售出组合价 ¥{rc.quote.comboPriceYuan} 分摊）</h2>
    <div class="summary-grid">
      <div class="stat"><div class="k">售出组合价</div><div class="v">¥{rc.quote.comboPriceYuan}</div></div>
      <div class="stat"><div class="k">应退款</div><div class="v" style="color:var(--ok)">¥{rc.quote.refundYuan}</div></div>
      <div class="stat"><div class="k">扣减合计</div><div class="v" style="color:var(--bad)">¥{rc.quote.deductionYuan}</div></div>
      <div class="stat">
        <div class="k">勾稽校验 退款＋扣减</div>
        <div class="v" style="font-size:15px;padding-top:6px">
          {#if rc.quote.balanced}<span class="badge ok">✓ 等于组合价</span>
          {:else}<span class="badge bad">✗ 不平</span>{/if}
        </div>
      </div>
    </div>
    <p class="muted">
      赠品缺失与商品破损分别在上方列出报价与依据；扣减采用最大余数法把组合价精确分摊到每件，
      因此各行分摊额之和恒等于售出组合价，退款金额与组合价、扣减明细严格一致。
    </p>

    {#if rc.status === 'COMPLETED' || rc.status === 'CONFIRMED'}
      <div class="quote-card" style="margin-top:6px">
        {#if rc.status === 'COMPLETED'}
          <b>办理结果：</b>
          {#if rc.boxRestored}
            <span class="badge ok">全部组件完好，整盒恢复入库 1 盒</span>
          {:else}
            <span class="badge warn">未恢复整盒，仅完好散件各自入库</span>
          {/if}
          {#each rc.refunds as r}
            <div class="muted" style="margin-top:6px">
              支付回执 {r.receiptNo || '—'} ｜ {r.result} ｜ 金额 {r.amountYuan ? '¥' + r.amountYuan : '—'}
              ｜ {r.detail}
              {#if r.receiptNo}
                <button class="btn ghost sm" style="margin-left:8px" on:click={() => replayReceipt(r.receiptNo)}>
                  用该回执再退一次（模拟重放）
                </button>
              {/if}
            </div>
          {/each}
        {:else}
          <span class="badge info">顾客已确认明细，柜台可办理退款与入库</span>
        {/if}
      </div>
    {/if}

    {#if rc.status === 'DRAFT'}
      <div class="row" style="margin-top:12px">
        <button class="btn success" on:click={confirm} disabled={busy}>顾客确认退款明细</button>
        <button class="btn primary" on:click={complete} disabled={busy}>确认后办理退款 + 入库</button>
        <span class="muted">可先确认再办理，也可由柜台现场直接办理。</span>
      </div>
    {:else if rc.status === 'CONFIRMED'}
      <div class="row" style="margin-top:12px">
        <button class="btn primary" on:click={complete} disabled={busy}>办理退款 + 入库</button>
      </div>
    {:else if rc.status === 'COMPLETED'}
      <div class="row" style="margin-top:12px">
        <button class="btn ghost" on:click={complete} disabled={busy}>重复点击办理（幂等测试）</button>
      </div>
    {/if}

    {#if showConfirm}
      <div class="quote-card" style="margin-top:12px">
        <b>请向顾客复述：</b>组合价 ¥{rc.quote.comboPriceYuan}，扣减 ¥{rc.quote.deductionYuan}，
        实退 ¥{rc.quote.refundYuan}。
        {#if rc.lines.every(l => l.missingQty === 0 && l.damagedQty === 0)}
          全部完好将恢复整盒库存。
        {:else}
          存在破损/缺失，<b>不会</b>恢复整盒，仅完好散件入库。
        {/if}
        <div class="row" style="margin-top:10px">
          <label><input type="checkbox" bind:checked={confirm1} /> 顾客已确认</label>
          <span class="spacer"></span>
          <button class="btn primary" on:click={complete} disabled={!confirm1 || busy}>确认办理</button>
          <button class="btn ghost" on:click={() => (showConfirm = false)}>取消</button>
        </div>
      </div>
    {/if}
  </div>

  <!-- 检查照片 + 主管意见 -->
  <div class="panel">
    <h2>检查照片与主管意见（保留在原订单下）</h2>
    <div class="row" style="align-items:flex-start">
      <div style="flex:1;min-width:280px">
        <h3>检查照片（本地保存）</h3>
        {#if rc.photos.length}
          <div class="photo-grid">
            {#each rc.photos as p}
              <div class="photo-cell">
                <a href={p.url} target="_blank"><img src={p.url} alt={p.originalName} /></a>
                <div>{p.originalName || p.fileName}</div>
                <div>{p.uploadedBy} · {p.uploadedAt?.slice(0,16).replace('T',' ')}</div>
              </div>
            {/each}
          </div>
        {:else}
          <p class="muted">暂无照片。</p>
        {/if}
        {#if rc.status === 'DRAFT' || rc.status === 'CONFIRMED'}
          <div class="row" style="margin-top:8px">
            <input type="file" accept="image/*" multiple bind:files={photoFiles} />
            <button class="btn sm" on:click={uploadPhotos} disabled={busy || !photoFiles.length}>上传照片</button>
          </div>
        {/if}
      </div>
      <div style="flex:1;min-width:280px">
        <h3>主管意见</h3>
        {#if rc.supervisorNotes.length}
          {#each rc.supervisorNotes as n}
            <div class="charge-box">
              <div class="row">
                <b>{n.supervisor}</b>
                {#if n.approved}<span class="badge ok">同意</span>{:else}<span class="badge bad">不同意</span>{/if}
                <span class="spacer"></span>
                <span class="muted">{n.createdAt?.slice(0,16).replace('T',' ')}</span>
              </div>
              <div class="basis">{n.opinion}</div>
            </div>
          {/each}
        {:else}
          <p class="muted">暂无主管意见。</p>
        {/if}
        {#if rc.status === 'DRAFT'}
          <div style="margin-top:8px">
            <div class="row">
              <input type="text" bind:value={noteSupervisor} placeholder="主管" style="width:120px" />
              <label><input type="checkbox" bind:checked={noteApproved} /> 同意办理</label>
            </div>
            <textarea bind:value={noteOpinion} placeholder="主管意见，如：赠品按分摊扣减、破损商品不可入库…"
                      style="margin-top:6px"></textarea>
            <button class="btn sm" on:click={addNote} disabled={busy} style="margin-top:6px">提交主管意见</button>
          </div>
        {/if}
      </div>
    </div>
  </div>
{/if}
