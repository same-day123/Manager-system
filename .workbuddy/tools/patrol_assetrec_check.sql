select '=== 资产 2029 的履历（看有没有本次测试新增的行） ===' as s;
select record_id, asset_id, record_type, from_value, to_value, operator_name,
       date_format(create_time,'%m-%d %H:%i:%s') as ctime, left(record_content,30) as content
from lab_asset_record where asset_id = 2029 order by record_id;

select '=== 全库资历表：今天的行 ===' as s;
select record_id, asset_id, record_type, from_value, to_value,
       date_format(create_time,'%m-%d %H:%i:%s') as ctime
from lab_asset_record where date(create_time) = '2026-09-17' order by record_id;

select '=== 资历表总数 ===' as s;
select count(*) as asset_records_total from lab_asset_record;
