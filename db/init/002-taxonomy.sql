-- 模块 B：内容分类
-- 根分类的 parent_id 固定为 0，避免 MySQL 自引用外键在根节点上缺少父行的问题；
-- 层级合法性（父节点存在、无自环/祖先环、深度上限）由模块 B 的 Service 层校验。

CREATE TABLE IF NOT EXISTS cms_category (
    id BIGINT NOT NULL AUTO_INCREMENT,
    parent_id BIGINT NOT NULL DEFAULT 0,
    name VARCHAR(64) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_parent_name (parent_id, name),
    KEY idx_category_parent_sort (parent_id, sort_order, id),
    KEY idx_category_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 演示数据：三个根分类及少量子分类，显式指定 ID 便于文章、通报引用。
INSERT IGNORE INTO cms_category (id, parent_id, name, sort_order, enabled) VALUES
    (1, 0, '安全制度', 10, 1),
    (2, 0, '培训指引', 20, 1),
    (3, 0, '安全通报', 30, 1),
    (101, 1, '实验室管理制度', 10, 1),
    (102, 1, '危险化学品管理', 20, 1),
    (103, 2, '设备操作培训', 10, 1),
    (104, 2, '应急处置培训', 20, 1),
    (105, 3, '化学类通报', 10, 1),
    (106, 3, '机电类通报', 20, 1);
