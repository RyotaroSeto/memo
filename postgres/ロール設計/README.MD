# ロール設計

## publicロール

- PostgreSQLでデフォルトで存在し、削除できないpublicロール
- PostgreSQLが作成したロールは全てpublicロールを継承する。
- publicスキーマを自由に利用できないよう、publicロールからpublicスキーマに対する権限を剥奪すべき。

## 定義済みロール(Predefined Roles)

- 定義済みロールとは？
    - PostgreSQLにはスーパーユーザしか許可されていない操作やデータがいくつか存在し、その操作の中にはDBのメンテナンス処理として実行するものも含まれている。しかし、これら全ての操作においてスーパーユーザ権限を付与することは過剰。
    - そこで、スーパーユーザ権限の一部を一般ユーザに持たせることができるよう権限群が提供されるようになった。それが定義済みロール
    - PostgreSQL14からは、参照用ユーザや更新用ユーザを作成する際に便利なpg_read_all_data,pg_write_all_dataが新たに実装された(その他11種類ある)
        - pg_read_all_dataは複数の業務用スキーマを用意している場合において、全データに参照権限を持たせる場合に有効な権限。通常テーブルなどのデータベースオブジェクトに対するアクセス権限はGRANTコマンドで個別に指定する必要があるが、このロールを付与することで個別に設定する手順を省略できる
        - pg_write_all_dataの利用はデータの更新を許可するためのロールだが、Updateなどの参照権限が必要なSQLはpg_write_all_data権限だけでは権限不足しており、処理に失敗する。そのため設定した権限に過不足ないか動作角煮することをおすすめ

## ロールの設計方針

- セキュリティ対策の一般的な考え方に、職務分掌と最小権限の原則があり、データベースのセキュリティ対策もこの原則に従うことがおすすめ
- PostgreSQLは、デフォルトでスーパーユーザ属性を持つロールが作成され、このロールは`postgres`という名前で作成される。このロールでの実システムの運用は避けるべき。職務分掌として運用に必要な分のロールを新たに作成する
- PostgreSQLにはスーパーユーザしか許可されていない操作があるがその操作は定義済みロールをGRANTすることで部分的に操作可能にすべき。定義済みロールでも操作できないケースのみスーパーユーザロールで作業する。
- 必要と想定されるユーザの分類
    - 管理者ユーザー
        - システム管理ユーザ(スーパーユーザ)
    - 一般ユーザー
        - アプリケーションユーザ
            - アプリケーションがデータベースを操作する際に使用
        - 保守メンテナンスユーザ
            - サービス開始後、保守担当者がデータベースのメンテナンス処理を行う際に使用
- ロールの属性変更はALTER ROLE、権限変更はGRANTコマンドで行う。権限はデータベースオブジェクト単位(データベース、スキーマ、テーブルなど)に設定する必要がある。
- 変更可能な属性

| SUPERUSER | スーパーユーザ属性の有無を定義する |
| --- | --- |
| CREATEDB | データベースの作成に関する属性を定義する |
| CREATEROLE | 新しいロールを作成する属性を定義する |
| INHERIT | 属するロールの権限を継承するかを定義する |
| LOGIN | データベースへのログイン属性を定義する |
- 変更可能な権限

| オブジェクト | 権限名 | 制御する内容 |
| --- | --- | --- |
| データベース | CREATE | データベース内での新たなオブジェクト作成を許可する |
| データベース | CONNECT | データベースに接続することを許可する |
| データベース | TEMPORARY | データベース内で一時テーブル作成を許可する |
| スキーマ | CREATE | スキーマ内に新しいオブジェクト作成を許可する |
| スキーマ | USAGE | スキーマ内に含まれるオブジェクトへのアクセスを許可する |
| テーブル | SELECT | テーブル、ビューにSELECTすることを許可する |
| テーブル | INSERT | テーブル、ビューに新しい行をINSERTすることを許可する |
| テーブル | UPDATE | テーブル、ビューの列のUPDATEすることを許可する |
| テーブル | DELETE | テーブル、ビューの列のDELETEすることを許可する |
| テーブル | TRUNCATE | テーブル、ビューのTRUNCATEを許可する |
| テーブル | REFERENCES | テーブルの列を参照する外部キー制約作成を許可する |
| テーブル | TRIGGER | テーブル、ビューでのトリガ作成を許可する |
- 監査のためのロールの分離
    - いざ監査を行う場合に誰が何をしていたのか特定するためにロールを分けておく必要がある。どの時間にどのロールがどこにアクセスしていたのかログに残せますが、ロールを分けず全て同じロールで処理を行っていた場合、該当時間の作業者を特定できない。

## ロール設計のサンプル

- 管理者ユーザーとしてsuperuser属性を持つadminユーザーを作成

```sql
	testdb=# CREATE ROLE admin WITH LOGIN superuser;
```

- アプリケーション用ユーザは更新用と参照用を作成。
- デフォルトのpostgresユーザは使用できないようログイン属性を外す。
- 最初にシステムの運用で使用するログイン権限を持つロールを作成。
    - この時、CREATEROLE属性は安易に一般ユーザに設定しないことをおすすめ。CREATEROLE属性を持つユーザは新たなロールで作成できる。

```sql
testdb=# CREATE ROLE app_user_w WITH LOGIN;
testdb=# CREATE ROLE app_user_r WITH LOGIN;
testdb=# CREATE ROLE hosye_user WITH LOGIN;
```

- システムの運用に必要なオブジェクトを操作する権限をロールに付与する
    - 注意点として業務用スキーマへの権限とスキーマ内に作成されたオブジェクトの権限が独立している点。業務用テーブルにアクセスするためには、テーブルへの権限に加え作成先のスキーマへのUSAGEが必要になる

```sql
testdb=# GRANT CONNECT, TEMPORARY ON DATABASE testdb TO app_user_w;
testdb=# GRANT CONNECT, TEMPORARY ON DATABASE testdb TO app_user_r;
testdb=# GRANT CREATE, CONNECT, TEMPORARY ON DATABASE testdb TO hosye_user;
testdb=# GRANT USAGE ON SCHEMA gyomu_schema TO app_user_w;
testdb=# GRANT USAGE ON SCHEMA gyomu_schema TO app_user_r;
testdb=# GRANT USAGE, CREATE ON SCHEMA gyomu_schema TO hosye_user;
testdb=# GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA gyomu_schema TO app_user_w;
testdb=# GRANT SELECT ON ALL TEBLES IN SCHEMA gyomu_schema TO app_user_r;
```

- デフォルトで作成されるpublicスキーマで自由にデータベースオブジェクトを作成できないようPUBLICロールからCREATE権限、USAGE権限を剥奪

```sql
testdb=# REVOKE ALL ON SCHEMA public FROM PUBLIC;
```

- システム運用で使用しないpostgresロールの利用を停止する（DROP ROLE削除できないため）postgres利用停止は運用のために作成したsuperuser属性を持つadminロールを使用してpostgresロールにNOLOGIN属性を付与

```sql
testdb=# ALTER ROLE postgres WITH NOLOIGIN;
```

- ロールが設計通りの属性、権限になっていうか確認するコマンド

```sql
testdb=# \du
testdb=# \dn+
testdb=# \l
```

### 鉄則

- 最低限のロールの設計として、管理者ユーザと一般ユーザは分離する
- スーパーユーザ属性を持つロールは無闇に作成しない。定義済みロールも活用し最低限の権限付与にとどめられるか検討する
- CREATEROLE属性は任意の定義済みロール付与したロールを作成できる強力な権限なので一般ユーザには付与しない。
- 権限はオブジェクト単位で設定が必要であり、データベースに対する権限を付与しても属性するスキーマやテーブルへの権限は自動で付与されない
