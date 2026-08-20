const https = require("https");
const pako = require("pako");

function mermaidInkEncode(text) {
  const data = JSON.stringify({ code: text });
  const compressed = pako.deflate(data);
  return Buffer.from(compressed)
    .toString("base64")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
}

// Test simplest
const test1 = "graph TD\n    A[Test]";
const encoded = mermaidInkEncode(test1);
const url = `https://mermaid.ink/svg/${encoded}`;
console.log("URL:", url);

https.get(url, (res) => {
  let body = "";
  res.on("data", d => body += d);
  res.on("end", () => {
    console.log("Status:", res.statusCode);
    console.log("Body:", body.slice(0, 200));
  });
}).on("error", e => console.error("Error:", e.message));
