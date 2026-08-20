const pako = require("pako");

function encode(text) {
  const data = JSON.stringify({ code: text });
  const compressed = pako.deflate(data);
  return Buffer.from(compressed)
    .toString("base64")
    .replace(/\+/g, "-").replace(/\//g, "_");
}

const text = "graph TD\n    A[Test]";
const enc = encode(text);

// Try different endpoints and methods
const urls = [
  `https://mermaid.ink/svg/${enc}`,
  `https://mermaid.ink/img/${enc}`,
];

Promise.all(urls.map(u =>
  fetch(u).then(async r => {
    const t = await r.text();
    console.log(`${r.status} ${u.slice(0, 60)}... body=${t.slice(0, 80)}`);
  }).catch(e => console.log(`ERR: ${u} - ${e.message}`))
));
