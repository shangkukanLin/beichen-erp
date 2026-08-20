const https = require("https");
const fs = require("fs");
const path = require("path");
const zlib = require("zlib");

// kroki.io 使用 raw deflate + base64url (Python zlib.compress 格式)
function krokiEncode(str) {
  const compressed = zlib.deflateRawSync(Buffer.from(str, "utf-8"));
  return compressed.toString("base64")
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}

async function download(url, filepath) {
  return new Promise((resolve, reject) => {
    https.get(url, (res) => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        const loc = res.headers.location;
        https.get(loc.startsWith("http") ? loc : `https://kroki.io${loc}`, (r) => handleResp(r)).on("error", reject);
        return;
      }
      handleResp(res);
      function handleResp(resp) {
        if (resp.statusCode !== 200) {
          let body = "";
          resp.on("data", d => body += d);
          resp.on("end", () => reject(new Error(`HTTP ${resp.statusCode}: ${body.slice(0, 300)}`)));
          return;
        }
        const file = fs.createWriteStream(filepath);
        resp.pipe(file);
        file.on("finish", () => { file.close(); resolve(); });
      }
    }).on("error", reject);
  });
}

async function main() {
  const diagramsDir = path.join(__dirname, "diagrams");
  const files = fs.readdirSync(diagramsDir).filter(f => f.endsWith(".mmd")).sort();

  for (const f of files) {
    const code = fs.readFileSync(path.join(diagramsDir, f), "utf-8");
    const outPath = path.join(diagramsDir, f.replace(".mmd", ".svg"));
    console.log(`渲染: ${f} -> ${path.basename(outPath)}`);

    try {
      const encoded = krokiEncode(code);
      const url = `https://kroki.io/mermaid/svg/${encoded}`;
      await download(url, outPath);
      console.log(`  ✅ 成功`);
    } catch (err) {
      console.error(`  ❌ ${f}: ${err.message}`);
    }
  }

  console.log("\n全部流程图生成完毕！");
}

main().catch(console.error);
