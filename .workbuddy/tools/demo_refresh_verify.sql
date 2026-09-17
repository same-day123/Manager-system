-- ============================================================================
-- 演示数据校准的验收断言（**只读**，不改任何数据）
-- 用法： mysql -uroot -p<密码> --default-character-set=utf8mb4 --batch <DB> < demo_refresh_verify.sql
-- 输出： 12 条 V01~V12 的 PASS/FAIL + 一行带分母的 SUMMARY
-- 背景：对应 AGENTS.md 7.3 的 D-24 / D-25
-- ============================================================================
with checks as (
    select 'V01 维修中资产全部有在途工单（孤儿数 = 0）' as chk,
           if((select count(1) from lab_asset a where a.del_flag = '0' and a.status = '2'
                 and not exists (select 1 from lab_repair r where r.asset_id = a.asset_id
                                   and r.del_flag = '0' and r.status in ('0', '1', '2'))) = 0,
              'PASS', 'FAIL') as result
    union all
    select 'V02 维修中资产数 = 有在途工单的资产数',
           if((select count(1) from lab_asset where del_flag = '0' and status = '2')
              = (select count(distinct asset_id) from lab_repair
                   where del_flag = '0' and status in ('0', '1', '2') and asset_id is not null),
              'PASS', 'FAIL')
    union all
    select 'V03 维修中资产恰为 2 台',
           if((select count(1) from lab_asset where del_flag = '0' and status = '2') = 2, 'PASS', 'FAIL')
    union all
    select 'V04 演示工单恰为 6 张',
           if((select count(1) from lab_repair where del_flag = '0') = 6, 'PASS', 'FAIL')
    union all
    select 'V05 存在 1 张待审核工单（现场可演示审核）',
           if((select count(1) from lab_repair where del_flag = '0' and status = '0') = 1, 'PASS', 'FAIL')
    union all
    select 'V06 在途工单 2 张（1 待审核 + 1 维修中）',
           if((select count(1) from lab_repair where del_flag = '0' and status in ('0', '1', '2')) = 2,
              'PASS', 'FAIL')
    union all
    select 'V07 近七日窗口内工单数 >= 6（趋势图非空）',
           if((select count(1) from lab_repair
                 where del_flag = '0' and create_time >= date_sub(curdate(), interval 6 day)) >= 6,
              'PASS', 'FAIL')
    union all
    select 'V08 近七日维修费用合计 > 0',
           if((select ifnull(sum(repair_cost), 0) from lab_repair
                 where del_flag = '0' and create_time >= date_sub(curdate(), interval 6 day)) > 0,
              'PASS', 'FAIL')
    union all
    select 'V09 完成时间不早于创建时间（终态单）',
           if((select count(1) from lab_repair
                 where del_flag = '0' and finish_time is not null and finish_time < create_time) = 0,
              'PASS', 'FAIL')
    union all
    select 'V10 履历文案无直角引号「」残留（与生产文案一致）',
           if((select count(1) from lab_repair_record
                 where record_content like '%「%' or record_content like '%」%') = 0,
              'PASS', 'FAIL')
    union all
    select 'V11 在途 2 张单子各有履历（时间线非空）',
           if((select count(1) from lab_repair r where r.del_flag = '0' and r.status in ('0', '1', '2')
                 and exists (select 1 from lab_repair_record rec where rec.repair_id = r.repair_id)) = 2,
              'PASS', 'FAIL')
    union all
    select 'V12 每张工单都挂着存在的资产（无悬空 asset_id）',
           if((select count(1) from lab_repair r where r.del_flag = '0' and r.asset_id is not null
                 and not exists (select 1 from lab_asset a where a.asset_id = r.asset_id)) = 0,
              'PASS', 'FAIL')
)
select chk, result from checks
union all
select 'SUMMARY',
       concat('PASS=', sum(result = 'PASS'), ' FAIL=', sum(result = 'FAIL'), ' TOTAL=', count(1))
from checks;
