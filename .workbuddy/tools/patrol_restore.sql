-- 巡检复原：只删本次巡检测试自己造出来的行，按明确 ID，不做范围删除
select '=== 复原前 ===' as s;
select (select count(*) from lab_repair where del_flag='0') as repairs_active,
       (select count(*) from lab_repair) as repairs_all,
       (select count(*) from lab_repair_record) as repair_records,
       (select count(*) from lab_asset_record) as asset_records,
       (select status from lab_asset where asset_id=2029) as asset2029_status;

-- 1) 合成单的处理记录（record_id 2..6，repair_id=3005）
delete from lab_repair_record where repair_id = 3005;
-- 2) 合成单本体（物理删，避免留下 del_flag='2' 的行让 count(*) 与界面不一致）
delete from lab_repair where repair_id = 3005;
-- 3) 合成单引起的资产履历（record_id 33 / 34，今天 09-17 由本次测试产生）
delete from lab_asset_record where record_id in (33, 34);

select '=== 复原后（应与巡检前基线一致：4 / 4 / 1 / 32 / 0） ===' as s;
select (select count(*) from lab_repair where del_flag='0') as repairs_active,
       (select count(*) from lab_repair) as repairs_all,
       (select count(*) from lab_repair_record) as repair_records,
       (select count(*) from lab_asset_record) as asset_records,
       (select status from lab_asset where asset_id=2029) as asset2029_status;

select '=== 残留检查（应为空） ===' as s;
select repair_id, repair_code from lab_repair where fault_description like '【巡检测试】%';
select record_id from lab_asset_record where date(create_time) = '2026-09-17';
select '=== 五台「维修中」资产仍在（基线状态，未被我改动） ===' as s;
select asset_id, asset_code, status from lab_asset where status='2' and del_flag='0' order by asset_id;
