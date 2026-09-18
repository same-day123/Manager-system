-- 清理前的结构确认（判断记录表能否物理删）
select '=== 记录表结构 ===' as s;
show columns from lab_repair_record;

select '=== 本次合成单及其记录 ===' as s;
select repair_id, repair_code, status, rating, del_flag from lab_repair where repair_code = 'BX20260917222554243';
select record_id, repair_id, action_name from lab_repair_record where repair_id = (select repair_id from lab_repair where repair_code = 'BX20260917222554243');

select '=== 清理前计数 ===' as s;
select (select count(*) from lab_repair where del_flag='0') as repairs_active,
       (select count(*) from lab_repair) as repairs_all,
       (select count(*) from lab_repair_record) as records;
