insert into users (nickname, registered_timestamp)
values
	('サンプルA', CURRENT_TIMESTAMP),
	('サンプルBB', CURRENT_TIMESTAMP),
	('サンプルCCC', CURRENT_TIMESTAMP),
	('サンプルDDDD', CURRENT_TIMESTAMP),
	('サンプルEEEEE', CURRENT_TIMESTAMP);

insert into contents (title, published_timestamp)
values
	('舞台 薔薇とお侍', CURRENT_TIMESTAMP),
	('舞台 髑髏城の八人', CURRENT_TIMESTAMP),
	('舞台 ロックなマクベス', CURRENT_TIMESTAMP),
	('ライブ ONE-STYLE ネタ収録ライブ', CURRENT_TIMESTAMP),
	('映画 きっと、なんとかなる', CURRENT_TIMESTAMP),
	('映画 シン・ナントカマン', CURRENT_TIMESTAMP);

insert into ticket_orders (user_id, content_id, created_timestamp)
values
 (1, 1, CURRENT_TIMESTAMP),
 (2, 3, CURRENT_TIMESTAMP),
 (1, 2, CURRENT_TIMESTAMP),
 (3, 3, CURRENT_TIMESTAMP),
 (4, 6, CURRENT_TIMESTAMP),
 (5, 6, CURRENT_TIMESTAMP),
 (4, 4, CURRENT_TIMESTAMP),
 (4, 5, CURRENT_TIMESTAMP),
 (2, 4, CURRENT_TIMESTAMP);

