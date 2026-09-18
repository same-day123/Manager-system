-- 复核 D-21 是否真的解除 + 排查「维修中」资产无对应工单
select '=== G. labadmin 的角色 ===' as s;
select u.user_name, r.role_id, r.role_key, r.role_name, r.status, r.del_flag
from sys_user u
join sys_user_role ur on ur.user_id = u.user_id
join sys_role r on r.role_id = ur.role_id
where u.user_name in ('admin','labadmin','student1','asset01','repair01','room01','viewer01')
order by u.user_name;

select '=== H. 看板菜单 2022 挂给了哪些角色（D-21 关键） ===' as s;
select rm.role_id, r.role_key, rm.menu_id
from sys_role_menu rm join sys_role r on r.role_id = rm.role_id
where rm.menu_id = 2022;

select '=== I. 各演示账号实持的 laboratory 权限条数 ===' as s;
select r.role_key, count(distinct m.perms) as lab_perms
from sys_role r
join sys_role_menu rm on rm.role_id = r.role_id
join sys_menu m on m.menu_id = rm.menu_id
where m.perms like 'laboratory:%'
group by r.role_key
order by lab_perms desc;

select '=== J. lab_manager 是否含 dashboard:view ===' as s;
select r.role_key, m.perms
from sys_role r
join sys_role_menu rm on rm.role_id = r.role_id
join sys_menu m on m.menu_id = rm.menu_id
where m.perms = 'laboratory:dashboard:view';

select '=== K. 「维修中」资产 vs 现有工单（排查数据不一致） ===' as s;
select a.asset_id, a.asset_code, a.asset_name, a.status as asset_status,
       (select count(*) from lab_repair rp where rp.asset_id = a.asset_id and rp.del_flag='0') as repair_cnt,
       (select group_concat(concat(rp2.repair_code,':',rp2.status) order by rp2.repair_id)
          from lab_repair rp2 where rp2.asset_id = a.asset_id) as repairs
from lab_asset a
where a.del_flag = '0' and a.status = '2'
order by a.asset_id;

select '=== L. 全库工单与资产联动总览 ===' as s;
select (select count(*) from lab_repair where del_flag='0') as repairs,
       (select count(*) from lab_repair where del_flag='0' and status='0') as st0,
       (select count(*) from lab_repair where del_flag='0' and status='3') as st3,
       (select count(*) from lab_asset where del_flag='0' and status='2') as assets_repairing,
       (select count(*) from lab_repair_record) as records;
