import { existsSync, readdirSync } from 'node:fs'
import { homedir } from 'node:os'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { defineConfig } from '@playwright/test'

const frontendDir = dirname(fileURLToPath(import.meta.url))
const repoDir = resolve(frontendDir, '..')
const e2eDir = join(repoDir, '.tmp', 'e2e')
const jdbcPath = (value) => value.replaceAll('\\', '/')

/** 本机通常只有 maven wrapper；CI 和装了 Maven 的机器直接用 PATH 里的 mvn。 */
function mavenCommand() {
  if (process.env.MINIWHO_E2E_MVN) return process.env.MINIWHO_E2E_MVN
  const exe = process.platform === 'win32' ? 'mvn.cmd' : 'mvn'
  const dists = join(homedir(), '.m2', 'wrapper', 'dists')
  if (existsSync(dists)) {
    for (const dist of readdirSync(dists)) {
      if (!dist.startsWith('apache-maven-')) continue
      for (const hash of readdirSync(join(dists, dist))) {
        const bin = join(dists, dist, hash, dist.replace(/-bin$/, ''), 'bin', exe)
        if (existsSync(bin)) return bin
      }
    }
  }
  return exe
}

// fork=false：应用跑在 Maven 进程里，测试结束时 Playwright 杀掉进程树就能一起带走，
// 不会在 Windows 上留下占用 8081 的 Java 子进程。
const backendCommand = `node e2e/reset.mjs && "${mavenCommand()}" -q -B -f "${join(repoDir, 'backend', 'pom.xml')}" spring-boot:run -Dspring-boot.run.fork=false`

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  reporter: 'list',
  timeout: 60_000,
  expect: { timeout: 15_000 },
  use: {
    baseURL: 'http://127.0.0.1:5174',
    viewport: { width: 1440, height: 900 },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    // 默认用 `playwright install chromium` 装好的浏览器；本机已有 Chrome 时
    // 设 MINIWHO_E2E_CHANNEL=chrome 可以省掉这次下载。
    channel: process.env.MINIWHO_E2E_CHANNEL || undefined
  },
  webServer: [
    {
      command: 'npm run dev -- --port 5174 --strictPort',
      url: 'http://127.0.0.1:5174',
      cwd: frontendDir,
      env: { VITE_PROXY_TARGET: 'http://127.0.0.1:8081' },
      reuseExistingServer: false,
      timeout: 90_000
    },
    {
      command: backendCommand,
      url: 'http://127.0.0.1:8081/api/pdf/status',
      cwd: frontendDir,
      env: {
        SERVER_PORT: '8081',
        MINIWHO_DB: jdbcPath(join(e2eDir, 'e2e.db')),
        MINIWHO_UPLOAD_DIR: jdbcPath(join(e2eDir, 'uploads')),
        MINIWHO_ADMIN_PASSWORD: 'e2e-admin-123',
        MINIWHO_MEDIA_SECRET: 'e2e-media-secret',
        MINIWHO_PUBLIC_BASE_URL: 'http://127.0.0.1:5174',
        MINIWHO_PDF_RENDER_BASE_URL: 'http://127.0.0.1:5174'
      },
      reuseExistingServer: false,
      timeout: 240_000
    }
  ]
})
