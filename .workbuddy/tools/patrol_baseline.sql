-- UI设计 第二轮 · 演示路径巡检 · 只读核对（不改任何数据）
select '=== A. 演示账号口令哈希 ===' as section;
select user_name, left(password, 30) as pw_prefix, status, del_flag,
       date_format(pwd_update_date, '%Y-%m-%d %H:%i') as pwd_upd
from sys_user
where user_name in ('admin','labadmin','asset01','repair01','room01','student1','viewer01')
order by user_name;

select '=== B. 登录重试限制（决定我敢不敢输错口令） ===' as section;
select config_key, config_value from sys_config
where config_key in ('sys.account.captchaEnabled','sys.account.maxRetryCount','sys.account.lockTime');

select '=== C. D-21：看板权限菜单是否存在 ===' as section;
select menu_id, menu_name, perms from sys_menu where perms like '%dashboard%';

select '=== D. 基线：报修单（巡检要能复原到这个状态） ===' as section;
select repair_id, repair_code, asset_id, status, rating, del_flag from lab_repair order by repair_id;

select '=== E. 基线：资产状态（巡检会动到 1 条，须复原） ===' as section;
select asset_id, asset_code, asset_name, status, del_flag from lab_asset order by asset_id;

select '=== F. 基线：处理记录条数 ===' as section;
select count(*) as repair_records_total from lab_repair_record;
