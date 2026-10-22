-- ============================================================
-- 生产环境清库 + 机构重置脚本
--
-- 用途：首个试用机构（力达托管）试用期结束，机构即将提供最准确、完整的真实
-- 数据重新导入。这个脚本把线上数据库清空到只剩一条记录——平台超级管理员
-- （手机号 13842805660），然后在同一个事务里重新建好"力达托管"这个机构、
-- 以及于老师（13942605943，机构运营老师，ADMIN角色）作为这个机构的第一个
-- 管理员账号——后续的批量导入脚本（import_to_sql.py 生成的 SQL）需要靠这个
-- 已存在的管理员手机号去定位机构，才能把其余 9 位老师、9 个托管班、19 门
-- 课外课、111 名学生批量导入进去。
--
-- ⚠️ 这是一次性、不可逆的生产数据清空操作。执行前必须：
--   1. 确认这确实是你想要的操作范围——清空的是【整个数据库】里除了这一条
--      超级管理员记录以外的所有数据，不是只清"力达托管"这一个机构。
--   2. 先跑一次完整备份：
--        cd <仓库根目录>
--        ./deploy/backup.sh
--      （不要用 --mysql-only，这次操作连 MinIO 里的头像等文件也建议一并备份，
--      虽然这个脚本本身不碰 MinIO，但数据库里引用的 avatar_object_key 清空后
--      那些文件会变成孤儿，保留一份文件快照方便万一需要人工核对。）
--   3. 确认没有老师/家长正在使用系统（建议挑一个没人在用的时间段执行，避免
--      执行过程中有人还在写入旧数据）。
--
-- 安全设计：
--   - 执行最开始先检查目标超级管理员账号是否存在、角色是否正确，不满足条件
--     直接报错中止，不会删除任何一行数据——这是防止手滑/环境搞错导致清空
--     了错误的数据库的最后一道保险。
--   - 全程包在一个事务里，和仓库里已有的批量导入脚本用同一套
--     「DECLARE EXIT HANDLER FOR SQLEXCEPTION」模式：任何一步出错都会完整
--     回滚，不会出现"删了一半"或"建了一半"的中间状态。
--   - 用 DELETE 而不是 TRUNCATE：TRUNCATE 在 MySQL 里是 DDL，会隐式 COMMIT，
--     没法被事务回滚保护（这是本仓库之前的批量导入脚本已经踩过、注释里写明
--     的真实坑），这里统一用事务保护下的 DELETE。
--   - 覆盖当前 schema 里的全部 23 张业务表（按 Flyway 迁移历史从 V1 到 V31
--     逐条核对过，排除了已经被后续迁移 RENAME/DROP 掉的旧表名），不包括
--     Flyway 自己的 flyway_schema_history 表（那张表不归业务逻辑管，绝对
--     不能动，否则会破坏迁移版本记录）。
--
-- 执行方式（必须用支持 DELIMITER 的 mysql 命令行客户端，不要用 --force 参数，
-- 不能漏掉 --default-character-set=utf8mb4——2026-10-22 实测过：漏了这个参数，
-- 客户端会用错误的连接字符集解释这份 UTF-8 文件，把中文名双重编码成乱码真实
-- 写进数据库，而且不会有任何报错提示）：
--   docker compose -f docker-compose.yml -f docker-compose.prod.yml exec -T mysql \
--     mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" <数据库名> \
--     < reset_production_except_platform_admin.sql
--
-- 执行结果只有两种：
--   - 没有任何 ERROR 输出——清库成功，脚本末尾的校验查询会显示 teacher 表
--     只剩 1 行、其余表全部是 0 行、新建的机构和于老师账号已经就绪。
--   - 报错——整体回滚，数据库状态和执行前完全一样，不需要手工清理。
-- ============================================================

DROP PROCEDURE IF EXISTS tuoguan_reset_except_platform_admin;

DELIMITER $$
CREATE PROCEDURE tuoguan_reset_except_platform_admin()
BEGIN
    DECLARE v_count INT;
    DECLARE v_institution_id BIGINT;

    -- 见批量导入脚本（import_to_sql.py）里同样写法的注释：不声明这个
    -- HANDLER，"出错整体回滚"这个安全保证只在用一次性管道
    -- （mysql ... < file.sql）执行时成立；如果改用交互式 source 执行、出错
    -- 后又在同一个未断开的会话里继续操作，后续语句里的 DDL（比如下一次
    -- 重跑这个脚本时开头的 DROP PROCEDURE）会隐式 COMMIT 当前还开着的事务。
    -- 显式声明这个 HANDLER 让任何未预期的错误都先 ROLLBACK 再重新抛出，
    -- 不依赖"连接是否断开"这个外部条件。
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    -- ---------- 安全阀：目标超级管理员必须存在且角色正确，否则一行都不删 ----------
    SELECT COUNT(*) INTO v_count
    FROM teacher
    WHERE phone = '13842805660' AND role = 'PLATFORM_ADMIN';

    IF v_count <> 1 THEN
        -- MySQL 的 SIGNAL MESSAGE_TEXT 最长 128 字符，超长会在 SIGNAL 这一步
        -- 本身就报错（已实测复现：ERROR 1648 Data too long for condition item
        -- 'MESSAGE_TEXT'）——反而会让这条安全阀检查本身失败退出，但退出原因
        -- 会被错误地记录成"消息太长"而不是"找不到目标账号"，容易误导排查，
        -- 所以这里刻意写得很短。
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '安全阀触发：找不到目标超级管理员，已中止，未删除任何数据';
    END IF;

    START TRANSACTION;

    SET FOREIGN_KEY_CHECKS = 0;

    -- ---------- 清空全部业务表（teacher 表单独处理，保留目标超级管理员这一行） ----------
    DELETE FROM monthly_bill_extra_fee_line_date;
    DELETE FROM monthly_bill_extra_fee_line;
    DELETE FROM monthly_bill_meal_line;
    DELETE FROM monthly_bill_leave_line;
    DELETE FROM monthly_bill;
    DELETE FROM course_consumption_record;
    DELETE FROM course_recharge_record;
    DELETE FROM student_unit_enrollment;
    DELETE FROM student_leave_record;
    DELETE FROM student_meal_record;
    DELETE FROM student_arrival_checkin;
    DELETE FROM student_daily_note;
    DELETE FROM student_share_view_log;
    DELETE FROM class_dismissal;
    DELETE FROM daily_task;
    DELETE FROM task_template;
    DELETE FROM student;
    DELETE FROM class_billing_rate;
    DELETE FROM teaching_unit;
    DELETE FROM audit_log;
    DELETE FROM institution_renewal_record;
    DELETE FROM institution;
    DELETE FROM teacher WHERE phone <> '13842805660';

    SET FOREIGN_KEY_CHECKS = 1;

    -- ---------- 重建机构 + 第一个管理员账号（于老师），供后续批量导入脚本锚定机构 ----------
    INSERT INTO institution (name) VALUES ('力达托管');
    SET v_institution_id = LAST_INSERT_ID();

    -- 密码哈希对应明文 888888（BCrypt, rounds=10，和后端
    -- Spring Security BCryptPasswordEncoder 默认强度一致，和批量导入脚本的
    -- 初始密码保持统一），首次登录会被强制要求改密。
    INSERT INTO teacher (institution_id, phone, name, password_hash, role, must_change_password)
    VALUES (v_institution_id, '13942605943', '于老师',
            '$2a$10$l8PTbztVbrYF9rKAzcgWMecUuS91fyGtixBh9VlVcXDNYJK6ZfinO',
            'ADMIN', TRUE);

    COMMIT;
END$$
DELIMITER ;

CALL tuoguan_reset_except_platform_admin();
DROP PROCEDURE tuoguan_reset_except_platform_admin;

-- ---------- 执行后校验（人工核对这几条输出，确认符合预期） ----------
SELECT 'teacher（应为 1 行：平台超级管理员）' AS check_item;
SELECT id, phone, name, role, institution_id FROM teacher WHERE institution_id IS NULL;

SELECT 'institution（应为 1 行：力达托管）' AS check_item;
SELECT id, name FROM institution;

SELECT '力达托管的管理员账号（应为 1 行：于老师，待后续批量导入脚本追加张老师等其余账号）' AS check_item;
SELECT id, phone, name, role FROM teacher WHERE institution_id IS NOT NULL;

SELECT '以下各表应全部为 0' AS check_item;
SELECT
    (SELECT COUNT(*) FROM student) AS student_count,
    (SELECT COUNT(*) FROM teaching_unit) AS teaching_unit_count,
    (SELECT COUNT(*) FROM class_billing_rate) AS class_billing_rate_count,
    (SELECT COUNT(*) FROM student_unit_enrollment) AS student_unit_enrollment_count,
    (SELECT COUNT(*) FROM monthly_bill) AS monthly_bill_count,
    (SELECT COUNT(*) FROM course_consumption_record) AS course_consumption_record_count,
    (SELECT COUNT(*) FROM course_recharge_record) AS course_recharge_record_count,
    (SELECT COUNT(*) FROM audit_log) AS audit_log_count,
    (SELECT COUNT(*) FROM task_template) AS task_template_count;
