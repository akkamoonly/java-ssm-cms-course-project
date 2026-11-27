-- A 账号与权限模块建表与初始化数据
-- 在 001-platform.sql 之后执行；仅需同一数据库，不依赖审计表。
-- 演示账号口令均为 BCrypt 哈希（spring-security-crypto 生成），仅用于本地联调，公开仓库不含真实口令。

CREATE TABLE IF NOT EXISTS lab_type (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_lab_type_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(32) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname VARCHAR(64) NOT NULL,
    lab_type_id BIGINT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username),
    KEY idx_sys_user_lab_type (lab_type_id),
    CONSTRAINT fk_sys_user_lab_type FOREIGN KEY (lab_type_id) REFERENCES lab_type (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(255) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(255) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_permission_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, role_id),
    KEY idx_sys_user_role_role (role_id),
    CONSTRAINT fk_sys_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_sys_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_sys_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role (id),
    CONSTRAINT fk_sys_role_permission_permission FOREIGN KEY (permission_id) REFERENCES sys_permission (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 实验室类别：用于控制定向通报的阅读范围（与内容分类分离建模）
INSERT INTO lab_type (id, code, name, created_at, updated_at) VALUES
    (1, 'ELECTROMECHANICAL', '机电类', '2026-11-27 00:00:00', '2026-11-27 00:00:00'),
    (2, 'OTHER',             '其他类', '2026-11-27 00:00:00', '2026-11-27 00:00:00');

-- 角色：读者、作者、审核员、管理员
INSERT INTO sys_role (id, code, name, description) VALUES
    (1, 'READER',   '读者',   '浏览与检索已发布内容'),
    (2, 'AUTHOR',   '作者',   '撰写、提交与发布文章'),
    (3, 'REVIEWER', '审核员', '审核文章，退回或通过'),
    (4, 'ADMIN',    '管理员', '账号、角色、分类与审计管理');

-- 权限标识：小写点分格式，与 B、C、D 对齐
INSERT INTO sys_permission (id, code, name, description) VALUES
    (1, 'user:manage',      '账号与角色管理', '维护账户、角色分配与实验室类别'),
    (2, 'category:manage',  '分类管理',       '维护内容分类树（B 模块使用）'),
    (3, 'article:create',   '新建与编辑文章', '创建与修改自己的稿件（C 模块使用）'),
    (4, 'article:submit',   '提交审核',       '将稿件提交审核（C 模块使用）'),
    (5, 'article:review',   '审核文章',       '退回或通过待审核稿件（C 模块使用）'),
    (6, 'article:publish',  '发布文章',       '发布审核通过的稿件（C 模块使用）'),
    (7, 'audit.read',       '审计查询',       '查询操作日志（D 模块使用）');

-- 权限矩阵
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
    (2, 3), (2, 4), (2, 6),
    (3, 5),
    (4, 1), (4, 2), (4, 7);

-- 联调演示账号（密码均为演示口令的 BCrypt 哈希，公开仓库不含真实口令）
INSERT INTO sys_user (id, username, password_hash, nickname, lab_type_id, enabled, created_at, updated_at) VALUES
    (1, 'admin',        '$2a$10$s4uT4K.pZUHyIv3Nh6Lo3e9b8ueJ5TwZN/Rm3CS8y72od2xGuewqW', '管理员',     1, 1, '2026-11-27 00:00:00', '2026-11-27 00:00:00'),
    (2, 'author',       '$2a$10$rVy.IVTRXD7BP7NL8pDT0.1dUaXQ3znAXhr28GBXLgX/G/m3ippHm', '演示作者',   1, 1, '2026-11-27 00:00:00', '2026-11-27 00:00:00'),
    (3, 'reviewer',     '$2a$10$EvBFzO5Aoa77S4ybxjVZWusyAe1InVmwVomZX7913To4te9ZA0eLi', '演示审核员', 1, 1, '2026-11-27 00:00:00', '2026-11-27 00:00:00'),
    (4, 'reader_mech',  '$2a$10$xqrogf8Cg93kwnUoa1TgMO15jGAf7RxkKi.tkYZeqBbHsg4L6WbEW', '机电类读者', 1, 1, '2026-11-27 00:00:00', '2026-11-27 00:00:00'),
    (5, 'reader_other', '$2a$10$xqrogf8Cg93kwnUoa1TgMO15jGAf7RxkKi.tkYZeqBbHsg4L6WbEW', '其他类读者', 2, 1, '2026-11-27 00:00:00', '2026-11-27 00:00:00');

-- 演示账号角色绑定：注册新用户默认绑定读者角色，管理员不可通过注册获得
INSERT INTO sys_user_role (user_id, role_id, created_at) VALUES
    (1, 4, '2026-11-27 00:00:00'),
    (2, 2, '2026-11-27 00:00:00'),
    (3, 3, '2026-11-27 00:00:00'),
    (4, 1, '2026-11-27 00:00:00'),
    (5, 1, '2026-11-27 00:00:00');
