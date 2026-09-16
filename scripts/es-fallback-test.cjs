// ES 降级验证：搜索指定关键词（默认「火车」），打印命中数与前几名
const { execSync } = require('child_process');
const kw = process.argv[2] || '火车';
const url = 'http://localhost:8080/api/products?q=' + encodeURIComponent(kw);
const out = execSync(`curl.exe -s "${url}"`).toString('utf8');
const j = JSON.parse(out);
console.log('total:', j.data.total);
j.data.items.slice(0, 3).forEach((i) => console.log(' -', i.name));
