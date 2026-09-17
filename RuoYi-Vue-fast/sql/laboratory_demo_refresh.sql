-- ============================================================================
-- 演示数据校准（可重复执行；**答辩前执行一次**，不属于 [N/7] 建库顺序）
-- 用途：把"已经存在"的演示库校准到与 [5/7] laboratory_user_seed.sql 一致，
--       并让首页看板的「近七日报修趋势 / 近七日维修费用」两张图有数据。
-- 依赖：[1/7] ~ [7/7] 已执行；**建议先重跑一次 [5/7] laboratory_user_seed.sql**
--       （它会补上 2 张在途工单，且自身幂等）。
-- 幂等：是（全部为 UPDATE + 带 not exists 判据的 INSERT，可重复执行）
-- 目标库：education_system
-- 边界：只动实验室演示数据（lab_asset / lab_repair / lab_repair_record /
--       lab_asset_record），**不碰任何 sys_* 基础数据，不改任何业务代码**。
--       执行前建议先 mysqldump 备份。
-- ============================================================================
--
-- 为什么需要这个脚本（两条都是 2026-09-17 演示路径巡检查实的缺陷，
-- 见 AGENTS.md 第 7.3 节 D-24 / D-25）：
--
-- 【缺陷 1｜D-24】「维修中」的资产可能没有任何工单
--   业务规则是**单向**的：提交报修 → 资产置 2 维修中。反向没有约束，于是
--   ①早期演示库手工改过状态、②运维人员直接"资产状态变更"，
--   都会留下「资产在修、却一张工单都点不开」的孤儿行。
--   实测真实库 5 台维修中资产**全部**没有工单，首页就出现
--   「维修中资产 5 / 待处理工单 0」这种看着像坏了的画面。
--
-- 【缺陷 2｜D-25】两张趋势图会随时间自然变空
--   首页两张图只统计**近 7 天**（LabDashboardMapper 写死
--   `create_time >= date_sub(curdate(), interval 6 day)`）。
--   [5/7] 里的报修日期是**相对脚本执行时刻**算的，而报修单有 repair_code 幂等保护
--   （`where not exists`）—— 也就是说**脚本只在第一次插入那天是新鲜的**，
--   之后日期一天天滑出 7 天窗口，图就变成 0 线。这不是代码 bug，是演示数据的
--   自然老化。本脚本把演示单日期重新平移回近 7 天，所以**答辩前一两天跑一次即可**。
--
-- 取舍说明：另一条路是把看板 SQL 改成"全部时间"聚合（PM 在待确认表 Q-18 的 A 方案），
--   但那样与需求基线「近七日趋势」的表述冲突，还会打破 T2 看板集成测试 DB-01~DB-07
--   的窗口边界断言（那属于测试负责的模块），故本脚本走数据侧校准。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 0. 校准前快照（打印出来留证）
-- ----------------------------------------------------------------------------
select '=== 校准前 ===' as step;
select '资产状态分布' as item, status, count(1) as n
from lab_asset where del_flag = '0' group by status order by status;
select '工单状态分布' as item, status, count(1) as n, ifnull(sum(repair_cost), 0) as cost
from lab_repair where del_flag = '0' group by status order by status;
select '近七日窗口内工单数' as item, date_sub(curdate(), interval 6 day) as win_start,
       curdate() as win_end,
       (select count(1) from lab_repair
         where del_flag = '0' and create_time >= date_sub(curdate(), interval 6 day)) as in_window;

-- ----------------------------------------------------------------------------
-- 1. 维修中资产纠偏：把"没有任何在途工单"的资产置回「正常」
--    顺序很重要 —— **先补履历，再改状态**，否则改完就查不出该补哪几行。
--    履历口径与生产代码一致：LabAssetEvent.ENABLE
--      → record_type='启用'、record_content='资产状态变更'、
--        from_value='维修中'、to_value='正常'（from/to 存的是中文标签，与真实库一致）。
-- ----------------------------------------------------------------------------
insert into lab_asset_record (
    asset_id, record_type, from_value, to_value, operator_name,
    record_content, create_by, create_time, remark
)
select a.asset_id, '启用', '维修中', '正常', 'admin',
       '资产状态变更', 'admin', sysdate(), '演示数据校准：[演示数据校准] 无在途工单的维修中资产归位'
from lab_asset a
where a.del_flag = '0'
  and a.status = '2'
  and not exists (
      select 1 from lab_repair r
      where r.asset_id = a.asset_id and r.del_flag = '0' and r.status in ('0', '1', '2')
  )
  and not exists (
      select 1 from lab_asset_record rec
      where rec.asset_id = a.asset_id
        and rec.remark = '演示数据校准：[演示数据校准] 无在途工单的维修中资产归位'
  );

update lab_asset a
set a.status = '0'
where a.del_flag = '0'
  and a.status = '2'
  and not exists (
      select 1 from lab_repair r
      where r.asset_id = a.asset_id and r.del_flag = '0' and r.status in ('0', '1', '2')
  );

-- ----------------------------------------------------------------------------
-- 2. 把演示单日期平移回近 7 天（让两张趋势图有柱子）
--    只动 6 张演示单（BX202607020001 ~ 0006），按 [5/7] 里同样的相对偏移量重算：
--      0001 -5/-4、0002 -4/-3、0003 -2/-1、0004 -1、0005 -1、0006 -3
--    每次执行都会重新按"当天"计算，所以反复跑不会越推越远。
-- ----------------------------------------------------------------------------
update lab_repair r
set r.create_time = case r.repair_code
        when 'BX202607020001' then date_sub(sysdate(), interval 5 day)
        when 'BX202607020002' then date_sub(sysdate(), interval 4 day)
        when 'BX202607020003' then date_sub(sysdate(), interval 2 day)
        when 'BX202607020004' then date_sub(sysdate(), interval 1 day)
        when 'BX202607020005' then date_sub(sysdate(), interval 1 day)
        when 'BX202607020006' then date_sub(sysdate(), interval 3 day)
        else r.create_time
    end,
    r.finish_time = case r.repair_code
        when 'BX202607020001' then date_sub(sysdate(), interval 4 day)
        when 'BX202607020002' then date_sub(sysdate(), interval 3 day)
        when 'BX202607020003' then date_sub(sysdate(), interval 1 day)
        else r.finish_time
    end
where r.repair_code in ('BX202607020001', 'BX202607020002', 'BX202607020003',
                        'BX202607020004', 'BX202607020005', 'BX202607020006');

-- 履历时间线跟着单子走：提交报修 = 单子创建时间；状态流转 = 完成时间
-- （未完结的单子没有 finish_time，取创建时间 +1 天，正好落在两条流转记录上）。
update lab_repair_record rec
join lab_repair r on r.repair_id = rec.repair_id
set rec.create_time = case rec.action_name
        when '提交报修' then r.create_time
        else ifnull(r.finish_time, date_add(r.create_time, interval 1 day))
    end
where r.repair_code in ('BX202607020001', 'BX202607020002', 'BX202607020003',
                        'BX202607020004', 'BX202607020005', 'BX202607020006');

-- ----------------------------------------------------------------------------
-- 3. 校准后自检
--    ★ 关键不变式：**维修中资产数 必须等于 有在途工单的资产数**
--      （业务规则「提交报修 → 资产置维修中」的单向闭环）。
--      两个数不相等就说明还有孤儿行或还有漏补的单子。
-- ----------------------------------------------------------------------------
select '=== 校准后 ===' as step;
select '资产状态分布' as item, status, count(1) as n
from lab_asset where del_flag = '0' group by status order by status;
select '工单状态分布' as item, status, count(1) as n, ifnull(sum(repair_cost), 0) as cost
from lab_repair where del_flag = '0' group by status order by status;
select '不变式 A：维修中资产数' as item,
       (select count(1) from lab_asset where del_flag = '0' and status = '2') as value,
       '应等于下一行的值' as expect
union all
select '不变式 B：有在途工单的资产数', 
       (select count(distinct asset_id) from lab_repair
         where del_flag = '0' and status in ('0', '1', '2') and asset_id is not null),
       '应等于上一行的值';
select '近七日趋势（首页图数据源）' as item,
       date_format(create_time, '%m-%d') as d, count(1) as orders,
       ifnull(sum(repair_cost), 0) as cost
from lab_repair
where del_flag = '0' and create_time >= date_sub(curdate(), interval 6 day)
group by date_format(create_time, '%m-%d')
order by min(create_time);
