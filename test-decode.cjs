const pako = require("pako");

// 1. 编码
const text = "graph TD\n    A[Test]";
const data = JSON.stringify({ code: text });
const compressed = pako.deflate(data);
const encoded = Buffer.from(compressed).toString("base64")
  .replace(/\+/g, "-").replace(/\//g, "_");
console.log("Encoded:", encoded);

// 2. 解码验证
const b64 = encoded.replace(/-/g, "+").replace(/_/g, "/");
const decompressed = Buffer.from(pako.inflate(Buffer.from(b64, "base64"))).toString("utf-8");
console.log("Decoded:", decompressed);

// 3. 试试不带 JSON 包装
const compressed2 = pako.deflate(text);
const encoded2 = Buffer.from(compressed2).toString("base64")
  .replace(/\+/g, "-").replace(/\//g, "_");
console.log("\nRaw encoded:", encoded2);

const b642 = encoded2.replace(/-/g, "+").replace(/_/g, "/");
const decompressed2 = Buffer.from(pako.inflate(Buffer.from(b642, "base64"))).toString("utf-8");
console.log("Raw decoded:", decompressed2);
