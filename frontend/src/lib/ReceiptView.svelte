<script>
  import { yuan } from '../api.js';

  /** 退款回执 */
  export let ret;
</script>

<div class="card receipt">
  <h2>✅ 退款已办理</h2>
  {#if ret.refund}
    <table class="kv">
      <tbody>
        <tr><th>退款回执号</th><td><b>{ret.refund.receiptNo}</b></td></tr>
        <tr><th>退款金额</th><td class="money big">{yuan(ret.refund.amountCents)}</td></tr>
        <tr><th>支付流水号</th><td>{ret.refund.paymentTxnId}</td></tr>
        <tr><th>办理柜台</th><td>{ret.refund.counterId}</td></tr>
        <tr><th>办理时间</th><td>{ret.refund.createdAt}</td></tr>
        <tr><th>原路退回</th><td>订单 {ret.orderNo} 的原始支付方式（模拟）</td></tr>
      </tbody>
    </table>
    <div class="muted" style="margin-top:10px">
      库存已按核对结果复原：{ret.inspectionPassed && ret.lines.every((l) => l.receivedQty === l.expectedQty && l.damagedQty === 0)
        ? '组件齐全且检查合格，已按整盒恢复可售库存。'
        : '未达整盒复原条件，散件已按各自状态（完好可售 / 破损）分别入库，未计入整盒库存。'}
    </div>
    <div class="muted">本回执重复提交不会再次退款，也不会重复增加库存。</div>
  {/if}
  <div style="margin-top:14px">
    <a href={'#/order/' + ret.orderNo}>返回订单查看处理记录 →</a>
  </div>
</div>

<style>
  .receipt { border-left: 4px solid #1e7a38; }
  .kv th { width: 120px; color: #777; font-weight: 500; }
  .kv td { font-size: 15px; }
  .big { font-size: 22px; color: #1e7a38; }
</style>
