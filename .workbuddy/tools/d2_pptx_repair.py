# -*- coding: utf-8 -*-
"""
D2 pptx 文本层修复
背景：Ardot 的 pptx 导出会把每段文本「开头/结尾的非 ASCII 连续串」剥掉（疑似写坏的 trim），
     纯非 ASCII 文本剥完为空则整段保留。画布与 PNG/PDF 不受影响。
做法：以画布 batch_read 得到的文本为真值，对每个 <p:sp> 的 run 序列做预测对齐
     （预测 = 对画布每行应用同样的剥边规则），命中则把 run 内容回填为画布原文。
用法：python d2_pptx_repair.py [src.pptx] [dst.pptx]
"""
import re, sys, zipfile, html
from xml.sax.saxutils import escape

SRC = sys.argv[1] if len(sys.argv) > 1 else r"docs/大作业/答辩PPT/答辩PPT-v1.pptx"
DST = sys.argv[2] if len(sys.argv) > 2 else r"docs/大作业/答辩PPT/答辩PPT-v2.pptx"

# 画布真值：每页 = DFS 顺序的文本节点列表（\n 分行）
CANVAS = {
1: ["运维工作台","《软件新技术专题》大作业 · 终期答辩 · 2026-09-22","高校实验室资产与报修","管理平台",
    "面向高校二级学院实验室的「资产全生命周期 + 故障报修闭环」管理平台","第 6 组 · 软件工程 2023-2 · 授课教师 谭清萱",
    "基于若依 RuoYi-Vue 3.9.2 二次开发","组员：王旻辉 · 巴力江·托力肯别克 · 廖煌 · 迪丽热巴·阿布都西克尔"],
2: ["背景 · 要解决的三个真实痛点","台账、工单、统计各自为政，其实是同一个缺口",
    "三个场景的共同后果是没有履历 —— 查不出谁改的、催不到谁在修、算不出花了多少",
    "痛点 01","台账靠 Excel 手工维护","资产调拨、报废没有留痕，学期末账实不符，查不出是谁改的",
    "痛点 02","报修靠微信群喊人","谁报的、谁在修、修到第几步全靠记忆，超时无人催办",
    "痛点 03","没有统计数据","一学期修了多少次、哪台设备最常坏、维修费花了多少，完全说不清",
    "一句话定位","面向高校二级学院实验室的「资产全生命周期 + 故障报修闭环」管理平台",
    "三个痛点的共同点：都没有履历 —— 每次变动、每张工单、每个学期都留不下证据","02 / 14"],
3: ["系统全景 · 四层架构","自定义代码只住一个包，不污染若依脚手架",
    "全部业务代码位于 com.ruoyi.project.laboratory，与若依原生模块物理隔离",
    "展示层","Vue 3.5 · Element Plus 2.13","三个业务页 + 首页运维看板","views/laboratory/**　views/index.vue",
    "业务层","Spring Boot 2.5.15 · Security + JWT","四个业务领域各一套 Controller + Service","com.ruoyi.project.laboratory",
    "持久化层","MyBatis · Mapper XML","自定义 Mapper 只读写自己的业务表","resources/mybatis/laboratory/*.xml",
    "数据库层","MySQL 8 · education_system","5 张业务表 + 4 组字典，删除一律逻辑删除","lab_room · lab_asset · lab_repair · *_record",
    "SOLID 落点：SRP LabRepairServiceImpl 不碰资产表 · OCP LabRoleUtils＋LabConstants · ISP 四个接口各自成域 · DIP 控制器依赖接口",
    "03 / 14","四个业务模块","实验室房间","资产台账","设备报修","运维工作台",
    "接口前缀 /laboratory/{room|asset|repair|dashboard}"],
4: ["核心契约 · 报修状态机","资产状态由报修工单驱动，不允许手工改",
    "流转规则只写在 validateStatusChange 一处，非法流转直接抛异常",
    "状态 0","待审核","审核通过","状态 1","待维修","开始维修","维修完成","状态 2","维修中",
    "状态 3","已完成","拒绝","状态 4","已拒绝","资产状态联动","提交报修即锁定资产，闭环结束自动释放",
    "提交报修成功","资产置为「维修中」","完成 / 拒绝 / 删除待审核单","资产回到「正常」",
    "非法流转一律抛异常且数据库不变；传 null 或同状态视为普通更新","04 / 14"],
5: ["设计取舍 · 我们主动做减法","主动不做两件事：预约模块、状态模式重构",
    "范围不是越大越好 —— 每一条「不做」都有明确理由，答辩时主动说明",
    "房间 / 设备预约与时段冲突","本期范围外，列为「后续迭代」","评分表五个维度，没有一个与功能数量相关",
    "补一个模块要动表 + 后端 + 前端 + 菜单权限 + SQL —— 一整轮迭代的成本",
    "用状态模式重构报修流转","主动判断：投入产出不划算","现有 validateStatusChange 只有 3 个分支的 if-else",
    "引入状态模式属过度设计；讲「评估过、判断不划算」比堆模式更能得分",
    "同样明确不做的 5 项","第三方支付与短信网关 · 小程序端 · 原生移动 App · 多校区 / 多租户隔离 · Selenium 端到端",
    "取舍的判断标准只有一个：能不能在任务书的评分维度上换到分","05 / 14"],
6: ["AI 协作 · 一次双向纠错","AI 关掉了一个我们一直没发现的缺陷",
    "一次「查缺补漏」巡检 —— 只给了一句提示词，AI 交回两个真实缺陷；而它自己算错的一处数字，被测试当场兜住",
    "AI 帮我们发现的","首页看板对非超管角色返回 403",
    "同一条权限散在四个技术层的五个位置，白名单不一致 —— 维修工程师、资产管理员这类角色看不到本该可见的看板",
    "处置 · 后端 LabRoleUtils 与前端常量对齐，SQL 末尾兜底补齐授权，登记为偏移 D-10",
    "我们帮 AI 纠正的","非法流转断言：AI 写 11，实际 16",
    "状态机共 5 个状态、有序对 25 组；AI 凭印象推定非法 11 组，正确推导是 25 − 5 组同状态幂等 − 4 组合法 = 16 组",
    "处置 · 断言改为 16，测试立刻全绿 —— 若当初写成 assert checked > 0，这个错误会一直躺在代码里",
    "这条权限要在 5 个位置说同一句话 —— 五处分布在四个技术层",
    "① 接口注解","② 菜单权限","③ 角色授予","④ 前端指令","⑤ 行级过滤",
    "AI 的自检结论同样需要被怀疑 —— 那次「前端构建 12ms 失败」最终查明是调用姿势错误，不是代码缺陷","06 / 14"],
7: ["质量保障 · 测试分层","把业务规则写成可断言的测试","49 个用例全部通过 —— 六条业务规则每一条都能指到一个具体的断言对象",
    "端到端实测 · 不写脚本","73 条接口 0 失败","集成测试 16 个","H2 内存库 · 验落库结果",
    "单元测试 33 个","Mockito 隔离 · 验调用契约","顶 · 为什么不写自动化脚本","现场跑真实环境，权限注解由 UI 巡检实测",
    "中 · 为什么集成要 16 条","跨表事务、SQL 与表结构是否对得上 —— 单测发现不了",
    "底 · 为什么单元最多","业务规则集中在 Service 层，分支与边界在这里穷举","如实说明",
    "覆盖率百分比出不来 —— 本机离线仓库缺 JaCoCo 插件，改用「用例与业务规则」追溯矩阵作证据",
    "177 条断言 + 27 次交互校验，含「非法流转时一次写库都没发生」这类否定式契约","07 / 14"],
8: ["交付链路 · CI 与容器化","从提交到上线，本地和 CI 跑同一套脚本","六段等价流水线，与 GitHub Actions 的 job 一一对应 —— 换环境不换命令",
    "[1/6] 版本信息","PASS 3.5s","[2/6] 后端测试","PASS 111.6s","[3/6] 前端构建","PASS 49.5s",
    "[4/6] 打包产物","PASS 49s","[5/6] 镜像构建","SKIP 0.1s","[6/6] 冒烟测试","SKIP 2.5s",
    "本地开发","直连本机 MySQL 与 Redis","改端口或库名即可，不动代码",
    "CI 流水线","跑 H2 内存库，零中间件依赖","与本地跑同一份测试命令",
    "容器编排","四个服务同起，配置走环境变量","application.yml 一字未改",
    "本机限制 · 如实说明","本机未安装 Docker，第 5、6 段恒为 SKIPPED —— 脚本保证 SKIPPED 不伪装成 PASS",
    "替代证据：94 条配置静态断言 + 23 条环境变量绑定实证，两组全绿",
    "提交 1668e4a 实测 PASS 4 / SKIPPED 2 / FAIL 0，总耗时 216.2 秒；流水线不含任何硬编码凭据","08 / 14"],
9: ["现场演示 · 三步闭环","三步走完一次报修闭环","从提交到评价，资产状态全程由工单驱动 —— 演示动作与截图一一对应",
    "第一步 · 首页就是结论","① 登录 → 首页看板","4 个指标卡 + 4 张图；维修中资产 2 台",
    "第二步 · 提报即联动资产","② 资产台账 → 报修","提交报修后，资产自动置「维修中」",
    "第三步 · 闭环走完","③ 审核 → 完成 → 评价","状态机 0→1→2→3，完成后资产回到「正常」",
    "演示账号 labadmin ／ 口令 admin123 —— 与 README、种子脚本三处口径已统一，现场登录不会翻车",
    "截图位待 T6-01 重拍完成后替换 —— 现场演示的画面必须与 PPT 里的截图一致","09 / 14"],
10: ["提问备份 · 权限与可见性","同一条权限，只能有一处口径",
     "角色白名单要在三处保持一致 —— 漏掉任意一处，就会出现「按钮看得到、接口却 403」",
     "① 后端 · LabRoleUtils","两套白名单的唯一真源","canHandleRepair 与 canViewAll 两个方法；控制器与 Service 全部调它，不在别处复制判断",
     "② 前端 · labPermission.js","同名常量照抄后端口径","两个常量与后端同名同义，文件注释里写明「必须与后端保持一致」，改一处要同步改另一处",
     "③ SQL · permission_fix","末尾按角色清单兜底授权","菜单授权脚本里少了看板权限，非超管角色首页空白 —— 偏移 D-10 漏掉的正是这一处",
     "可处理报修 · 4 / 8","admin","teacher","lab_manager","repair_engineer",
     "可查看全部 · 6 / 8","admin","teacher","lab_manager","repair_engineer","asset_keeper","lab_viewer",
     "蓝＝与上行同一批角色；绿＝只读观察角色。room_keeper / student_assistant 不在任一白名单 —— 只能看到自己提交的报修单",
     "把口径收在一处，是为了让「改权限」只需要改对一个地方 —— 漏改时也不会静默越权","10 / 14"],
11: ["提问备份 · 数据模型","五张业务表，删除一律是逻辑删除","单据不消失、履历能回溯 —— 代价是不能给业务编号建唯一索引",
     "lab_room","实验室房间","编号 / 名称 / 学院 / 管理员","lab_asset","资产台账","编号 / 名称 / 归属房间 / 状态 / 价格",
     "lab_repair","报修工单","编号 / 资产 / 状态 / 成本 / 评分","1 ： N","1 ： N",
     "lab_asset_record","资产履历 · 每次状态变更留一行","lab_repair_record","报修履历 · 处理时间线由此拼出",
     "房间为什么不建履历","房间不参与闭环，只有资产与工单留痕",
     "删得了单据，删不掉行","列表查询一律带 del_flag = '0'；编号唯一性由 Service 校验承担，不建唯一索引",
     "唯一索引会与「删掉后重建同编号」冲突 —— 实测返回 500，已用幂等迁移删掉这两个键",
     "五张业务表另有 4 张字典表配合 —— 状态与等级的中文名不写死在代码里","11 / 14"],
12: ["提问备份 · 已知不足与处置","五条我们选择容忍的偏差",
     "容忍不等于不知道 —— 每一条都写清了现象、影响与处置路径，答辩时主动说明",
     "五条都在 AGENTS.md 第 7.3 节留有编号 —— 没编号的修复等于没发生","12 / 14",
     "D-01","二维码是相对路径","手机相机扫不开，只能登录后站内跳转","配一个站点基址即可，属小改动",
     "D-06","前后端状态机是两张手写表","跨语言无法真正共享，后端有矩阵测试兜底","改状态机时人工比对前端",
     "D-08","签名密钥有可猜的默认值","默认值一旦上线就等于公开，能伪造登录凭证","用环境变量覆盖，写进部署文档",
     "D-26","沙箱拦截 WMIC 会拆命令树","长流水线有概率被中途掐断，易误判成构建失败","脚本内置重试；CI 在 Linux 上无此问题",
     "D-28","本机没装 Docker","容器未真跑，镜像与冒烟两段恒为 SKIPPED","改用静态断言与环境变量绑定实证",
     "处置原则","容忍的标准是影响可控、且修复成本高于收益 —— 而像 D-10 那种用户可感知的缺陷，一律当场修掉"],
13: ["提问备份 · 工作量","这套系统到底做了多少东西","每一个数字都能在仓库里用一条命令复算 —— 不给估算值",
     "28","个 REST 接口，分在 4 个控制器","9","张数据表 —— 5 张业务 + 4 张字典",
     "29","个生产类，全部收在一个自定义包内","49","个测试用例 —— 33 单元 + 16 集成",
     "9","个 SQL 脚本，全部可重复执行","4","个业务模块 —— 房间 / 资产 / 报修 / 看板",
     "口径说明","以上数字都能用一条命令复算 —— 测试数取自实跑日志，接口数取自注解计数",
     "本次实跑记录：Tests run: 49, Failures: 0, Errors: 0, Skipped: 0（提交 1668e4a）",
     "规模不按代码行数论 —— 论的是每条业务规则有没有落点、每个数字能不能复算","13 / 14"],
14: ["收尾 · 分工与 AI 使用声明","谁做了什么，AI 参与了哪一段",
     "AI 的输出一律经人工复核后才进入交付，每一次改动都留有编号与留痕",
     "组员分工 · 第 6 组 · 软件工程 2023-2","01　王旻辉 · 待认领","02　巴力江·托力肯别克 · 待认领",
     "03　廖煌 · 待认领","04　迪丽热巴·阿布都西克尔 · 待认领",
     "分工认领尚待确认（问题 Q-08）；确认后只替换本栏文字，页面结构不动",
     "AI 使用声明 · 六个角色各领一块",
     "产品经理 · 需求基线、任务卡与偏移台账\n代码负责 · 前后端实现、CI/CD 与容器化\n测试负责 · T1/T2，49 个用例全绿\n文档负责 · 五份交付文档\nUI 设计 · T6，界面统一与答辩截图\nPPT 和设计 · D2，这份答辩稿",
     "人工把关的环节","缺陷判定与流程纠偏、状态机与权限口径验收、容忍偏差的取舍决策",
     "三条红线","查重率 ≤ 30%（含代码注释）","每份文档开头写明 AI 使用段落","答辩全员参与，教师随机点人",
     "每一份 AI 留痕都保留完整提示词与人工修改点 —— 可追溯到具体某一次对话","14 / 14"],
}

CJK_STRIP = re.compile(r'^[\u3000-\u303f\u4e00-\u9fff\uf900-\ufaff\uff00-\uffef]+')

def fix(s):
    """复现导出器的剥边行为：剥掉首尾连续的 CJK 字符/全角标点（·U+00B7、①U+2460 等不在类内，不剥）；
    剥空则视为整段保留。类范围由 v1 全量 run 对照确证。"""
    t = CJK_STRIP.sub('', s)
    t = re.sub(r'[\u3000-\u303f\u4e00-\u9fff\uf900-\ufaff\uff00-\uffef]+$', '', t)
    return t if t else s

# 导出器按脚本分段剥边、run 切分与整行模型不一致的个例：显式补丁
# slide号 -> [(实际run元组, [回填行...])]
OVERRIDE = {
    10: [(('canHandleRepair 与 canViewAll', 'Service '),
          ['canHandleRepair 与 canViewAll 两个方法；控制器与 Service 全部调它，不在别处复制判断', ''])],
}

zin = zipfile.ZipFile(SRC)
slides = sorted([n for n in zin.namelist() if re.match(r'ppt/slides/slide\d+\.xml$', n)],
                key=lambda n: int(re.search(r'\d+', n).group()))
print('源文件页数:', len(slides), '| 目标:', DST)

repaired_runs = 0
report = []
zout = zipfile.ZipFile(DST, 'w', zipfile.ZIP_DEFLATED)
for item in zin.infolist():
    data = zin.read(item.filename)
    m = re.match(r'ppt/slides/slide(\d+)\.xml$', item.filename)
    if m:
        n = int(m.group(1))
        xml = data.decode('utf-8')
        nodes = [e.split('\n') for e in CANVAS.get(n, [])]
        consumed = [False] * len(nodes)
        out, last, nfix = [], 0, 0
        for sm in re.finditer(r'<p:sp>.*?</p:sp>', xml, re.S):
            sp = sm.group(0)
            ts = list(re.finditer(r'<a:t>(.*?)</a:t>', sp, re.S))
            if ts:
                actual = [html.unescape(t.group(1)) for t in ts]
                lines, hit = None, None
                ov = OVERRIDE.get(n, [])
                for oi, (pat, rep) in enumerate(ov):
                    if tuple(actual) == pat:
                        lines, hit = rep, 'OVR'
                        ov[oi] = ((), [])  # 消耗掉，避免重复命中
                        break
                if hit is None:
                    for i, cand in enumerate(nodes):
                        if not consumed[i] and [fix(l) for l in cand] == actual:
                            lines, hit = cand, i
                            break
                if lines is not None:
                    if hit != 'OVR':
                        consumed[hit] = True
                    buf, pos = [], 0
                    for t, line in zip(ts, lines):
                        buf.append(sp[pos:t.start(1)])
                        buf.append(escape(line))
                        pos = t.end(1)
                    buf.append(sp[pos:])
                    sp = ''.join(buf)
                    nfix += len(ts)
            out.append(xml[last:sm.start()]); out.append(sp); last = sm.end()
        out.append(xml[last:])
        new_xml = ''.join(out)
        repaired_runs += nfix
        # 校验：画布每行（转义后）必须出现在修复后的 XML 里
        missing = []
        for lines in nodes:
            for l in lines:
                if escape(l) not in new_xml:
                    missing.append(l)
        report.append((n, nfix, len([c for c in consumed if c]), len(nodes), missing))
        data = new_xml.encode('utf-8')
    zout.writestr(item, data)
zout.close()

print('\n页 | 回填run数 | 命中节点/画布节点 | 校验缺失')
bad = 0
for n, nfix, hit, total, missing in report:
    flag = '' if not missing else '  ←←← 需人工检查'
    if missing: bad += 1
    print('S%02d | %3d | %d/%d | %s%s' % (n, nfix, hit, total, missing, flag))
print('\n回填 run 总数:', repaired_runs, '| 有缺失的页数:', bad)
