import { mkdirSync, rmSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

// E2E 后端跑在独立端口和独立数据目录里，每次启动前清空，避免上一轮的分享链接和编辑结果漏进来。
const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..', '.tmp', 'e2e')
rmSync(root, { recursive: true, force: true })
mkdirSync(join(root, 'uploads'), { recursive: true })
