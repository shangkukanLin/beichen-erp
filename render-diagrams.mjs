const { JSDOM } = require("jsdom");
const fs = require("fs");
const path = require("path");

async function main() {
  // 1. 创建 JSDOM 虚拟浏览器环境
  const dom = new JSDOM("<!DOCTYPE html><html><body><div id='container'></div></body></html>", {
    url: "http://localhost",
    runScripts: "dangerously",
    resources: "usable",
  });

  global.window = dom.window;
  global.document = dom.window.document;
  global.navigator = dom.window.navigator;

  // 2. 动态导入 ESM mermaid
  const mermaid = (await import("mermaid")).default;
  await mermaid.initialize({ startOnLoad: false, theme: "default", securityLevel: "loose" });

  // 3. 读取所有 mmd 文件并渲染
  const diagramsDir = path.join(__dirname, "diagrams");
  const files = fs.readdirSync(diagramsDir).filter(f => f.endsWith(".mmd")).sort();

  for (const f of files) {
    const code = fs.readFileSync(path.join(diagramsDir, f), "utf-8");
    const outPath = path.join(diagramsDir, f.replace(".mmd", ".svg"));
    console.log(`渲染: ${f} -> ${path.basename(outPath)}`);

    const id = "d" + f.replace(/[^a-z0-9]/gi, "_");
    const { svg } = await mermaid.render(id, code);
    fs.writeFileSync(outPath, svg);
  }

  console.log("\n全部流程图生成完毕！");
}

main().catch(console.error);
