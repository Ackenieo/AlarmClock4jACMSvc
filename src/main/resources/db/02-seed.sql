SET NAMES utf8mb4;

-- 演示商品：价格刻意定低，方便沙箱真实付款与退款验证
-- 显式写 id，便于测试文档里固定引用 productId=1
INSERT INTO mall_product (id, name, description, category, price, original_price, cover_url, word_count,
                          skin_style, on_sale, sort_no, create_time, update_time, deleted)
VALUES (1, '考研核心词汇', '5500 词，按真题词频排序，支持闹钟提醒背词', 'WORD_BOOK', 6.66, 9.90, NULL, 5500,
        NULL, 1, 10, NOW(), NOW(), 0),
       (2, '雅思高频词库', '3800 词，听说读写分科标注', 'WORD_BOOK', 5.99, 8.00, NULL, 3800, NULL, 1, 20, NOW(),
        NOW(), 0),
       (3, '四六级核心词', '4500 词，覆盖四级与六级高频词表', 'WORD_BOOK', 4.50, 6.00, NULL, 4500, NULL, 1, 30,
        NOW(), NOW(), 0),
       (4, 'GRE 红宝书精编', '8200 词，含词根词缀拆解', 'WORD_BOOK', 3.88, 5.00, NULL, 8200, NULL, 1, 40, NOW(),
        NOW(), 0),
       (5, '少儿启蒙词库', '800 词，图文卡片模式', 'WORD_BOOK', 0.01, 2.00, NULL, 800, NULL, 1, 50, NOW(), NOW(),
        0),
       (6, '极简白皮肤', '高对比度白底，清晨不刺眼', 'APP_SKIN', 0.01, 1.00, NULL, NULL, 'minimal_white', 1, 110,
        NOW(), NOW(), 0),
       (7, '深夜蓝皮肤', '夜间低蓝光配色', 'APP_SKIN', 1.99, 3.00, NULL, NULL, 'midnight_blue', 1, 120, NOW(),
        NOW(), 0),
       (8, '闹钟红皮肤', '强提醒红色主题', 'APP_SKIN', 2.50, 3.50, NULL, NULL, 'alarm_red', 1, 130, NOW(), NOW(),
        0),
       (9, '森林绿皮肤', '自然系护眼绿', 'APP_SKIN', 3.33, 4.00, NULL, NULL, 'forest_green', 1, 140, NOW(), NOW(),
        0),
       (10, '赛博霓虹皮肤', '暗色霓虹渐变', 'APP_SKIN', 1.00, 2.00, NULL, NULL, 'cyber_neon', 1, 150, NOW(),
        NOW(), 0);
