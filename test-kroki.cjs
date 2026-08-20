const https = require("https");
const zlib = require("zlib");

function krokiEncode(str) {
  const compressed = zlib.deflateRawSync(Buffer.from(str, "utf-8"));
  return compressed.toString("base64")
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}

// Test 1: simplest mermaid
const test1 = "graph TD\n    A[Test]";

function test(name, code) {
  return new Promise((resolve) => {
    const encoded = krokiEncode(code);
    const url = `https://kroki.io/mermaid/svg/${encoded}`;
    https.get(url, (res) => {
      let body = "";
      res.on("data", d => body += d);
      res.on("end", () => {
        console.log(`${name}: HTTP ${res.statusCode}, body: ${body.slice(0, 100)}`);
        resolve();
      });
    });
  });
}

// Also try POST API
function testPost(name, code) {
  return new Promise((resolve) => {
    const payload = JSON.stringify({
      diagram_source: code,
      diagram_type: "mermaid",
      output_format: "svg"
    });
    const req = https.request("https://kroki.io", {
      method: "POST",
      headers: { "Content-Type": "application/json", "Content-Length": Buffer.byteLength(payload) }
    }, (res) => {
      let body = "";
      res.on("data", d => body += d);
      res.on("end", () => {
        console.log(`${name} (POST): HTTP ${res.statusCode}, body: ${body.slice(0, 100)}`);
        resolve();
      });
    });
    req.write(payload);
    req.end();
  });
}

async function main() {
  await test("Simple", test1);

  // Test with HTML tags
  const test2 = 'graph TD\n    A["<b>Test</b>"]';
  await test("WithHTML", test2);

  // Test POST
  await testPost("Simple POST", test1);
}

main();
