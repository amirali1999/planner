import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

const [projectRoot, javaBin, nodeBin, home] = process.argv.slice(2);
if (![projectRoot, javaBin, nodeBin, home].every(Boolean)) {
  throw new Error('usage: node generate-plists.mjs PROJECT_ROOT JAVA_BIN NODE_BIN HOME');
}

const launchAgents = join(home, 'Library', 'LaunchAgents');
const logs = join(home, 'Library', 'Logs', 'Planner');
const runtime = join(projectRoot, '.runtime');
const frontend = join(projectRoot, 'frontend');
const nodePath = dirname(nodeBin);

const common = {
  RunAtLoad: true,
  KeepAlive: true,
  ThrottleInterval: 10,
};

const backend = {
  ...common,
  Label: 'com.lifeplanner.backend',
  ProgramArguments: [javaBin, '-jar', join(runtime, 'planner.jar')],
  WorkingDirectory: join(projectRoot, 'backend'),
  EnvironmentVariables: {
    PLANNER_SERVER_ADDRESS: '127.0.0.1',
    PLANNER_SERVER_PORT: '18080',
    PLANNER_DB_PATH: join(projectRoot, 'data', 'planner.db'),
  },
  StandardOutPath: join(logs, 'backend.log'),
  StandardErrorPath: join(logs, 'backend-error.log'),
};

const web = {
  ...common,
  Label: 'com.lifeplanner.frontend',
  ProgramArguments: [
    nodeBin,
    join(frontend, 'node_modules', 'vite', 'bin', 'vite.js'),
    'preview',
    '--host', '127.0.0.1',
    '--port', '4173',
    '--strictPort',
  ],
  WorkingDirectory: frontend,
  EnvironmentVariables: {
    PATH: `${nodePath}:/usr/local/bin:/opt/homebrew/bin:/usr/bin:/bin`,
    PLANNER_API_TARGET: 'http://127.0.0.1:18080',
  },
  StandardOutPath: join(logs, 'frontend.log'),
  StandardErrorPath: join(logs, 'frontend-error.log'),
};

writeFileSync(join(launchAgents, 'com.lifeplanner.backend.plist'), `${JSON.stringify(backend, null, 2)}\n`);
writeFileSync(join(launchAgents, 'com.lifeplanner.frontend.plist'), `${JSON.stringify(web, null, 2)}\n`);
