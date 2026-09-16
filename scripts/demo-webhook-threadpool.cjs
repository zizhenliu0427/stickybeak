// 故事线 9.6 演示：webhook 独立线程池水位观测
// 60 并发 mock webhook → 采样 /webhooks/pool-stats → 汇总线程池行为
// 用法: node scripts/demo-webhook-threadpool.cjs [concurrency=60]
const http = require('http');

const CONCURRENCY = Number(process.argv[2] || 60);
const GATEWAY = 'http://localhost:8080';

function postMock(i) {
  return new Promise((resolve, reject) => {
    const body = JSON.stringify({
      eventId: `evt_pool_${Date.now()}_${i}`,
      orderNo: `SO_POOL_DEMO_${i}`, // 不存在的订单号：走解析+落档路径，不发 MQ
      amount: 10.0,
      currency: 'AUD',
    });
    const start = process.hrtime.bigint();
    const req = http.request(
      `${GATEWAY}/api/webhooks/mock`,
      { method: 'POST', headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(body) } },
      (res) => {
        let data = '';
        res.on('data', (c) => (data += c));
        res.on('end', () => resolve({ status: res.statusCode, ms: Number(process.hrtime.bigint() - start) / 1e6, body: data }));
      },
    );
    req.on('error', reject);
    req.write(body);
    req.end();
  });
}

function getPoolStats() {
  return new Promise((resolve, reject) => {
    http.get(`${GATEWAY}/api/webhooks/pool-stats`, (res) => {
      let data = '';
      res.on('data', (c) => (data += c));
      res.on('end', () => resolve(JSON.parse(data)));
    }).on('error', reject);
  });
}

(async () => {
  console.log(`=== webhook 线程池压测：${CONCURRENCY} 并发 ===\n`);
  const before = await getPoolStats();
  console.log('启动前:', JSON.stringify(before));

  const samples = [];
  const sampler = setInterval(async () => {
    try {
      samples.push(await getPoolStats());
    } catch { /* ignore */ }
  }, 50);

  const t0 = Date.now();
  const results = await Promise.all(Array.from({ length: CONCURRENCY }, (_, i) => postMock(i)));
  clearInterval(sampler);
  const totalMs = Date.now() - t0;

  // 等池子排空
  await new Promise((r) => setTimeout(r, 1500));
  const after = await getPoolStats();

  const latencies = results.map((r) => r.ms).sort((a, b) => a - b);
  const accepted = results.filter((r) => r.body.includes('ACCEPTED')).length;
  const peakActive = Math.max(0, ...samples.map((s) => s.activeCount));
  const peakQueue = Math.max(0, ...samples.map((s) => s.queueSize));

  console.log('\n=== 结果 ===');
  console.log(`全部 ${CONCURRENCY} 个请求 HTTP 总耗时: ${totalMs}ms`);
  console.log(`受理状态 ACCEPTED: ${accepted}/${CONCURRENCY}`);
  console.log(`单请求延迟 avg=${(latencies.reduce((s, v) => s + v, 0) / latencies.length).toFixed(1)}ms p95=${latencies[Math.floor(latencies.length * 0.95)].toFixed(1)}ms`);
  console.log(`线程池峰值: active=${peakActive}（上限 8） queueDepth=${peakQueue}（容量 200）`);
  console.log(`完成后: completedTaskCount=${after.completedTaskCount} largestPoolSize=${after.largestPoolSize}`);
  console.log(`拒绝策略: ${after.rejectionPolicy}（队列满时回退调用方线程，事件不丢）`);
})().catch((e) => {
  console.error('demo failed:', e.message);
  process.exit(1);
});
