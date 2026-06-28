// Minimal static server for the designed careers front door (bofa-careers-home.html).
// Serves it at http://localhost:5173/ ; the page opens the screening runtime on :5174.
import { createServer } from 'node:http'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const here = dirname(fileURLToPath(import.meta.url))
const HOME = join(here, 'bofa-careers-home.html')
const PORT = 5173

createServer(async (_req, res) => {
  try {
    const html = await readFile(HOME)
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' })
    res.end(html)
  } catch (e) {
    res.writeHead(500); res.end('Could not load careers home: ' + e.message)
  }
}).listen(PORT, () => console.log(`Careers home → http://localhost:${PORT}`))
