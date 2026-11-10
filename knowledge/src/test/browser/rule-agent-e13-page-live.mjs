// 仅测试专用入口，显式调用才执行；只替换profile，复用E02已有的所有权、限次与清理守卫。
import {runPageLive} from './rule-agent-e02-page-live.mjs';
await runPageLive('E13');
