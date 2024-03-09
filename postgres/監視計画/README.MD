# 監視計画

## 監視項目の選定

### 監視項目は大きく分けて2つ

- サーバーに問題が起きていないか
    - CPU/メモリ/HDD/ネットワークが正常に動作しているか、さらにサーバログに異常を知らせる通知はないかを確認する
        - これらの項目は、OSやストレージに付随するコマンドで監視する。具体的に`sar/iostat/vmstat/top/netstat`コマンドなども用いて定期的に収集する
- PostgreSQLに問題が起きていないか
    - PostgreSQLのプロセスやディスク容量、稼働状況などを確認する
    - 以下の項目に対して、SQLやOSに付属するコマンドで定期的に必要な情報を収集する
        - 必要なプロセスは正常に動作しているか
            - OSのpsコマンド、PostgreSQLのpg_stat_activityビューやpg_stat_archiverビュー、pg_stat_progress_vacuumビューを参照し、動作しているバックエンドプロセス、バックグラウンドプロセスの数や状態を取得する
            - psqlを用いてPostgreSQLに接続できるか確認する。ストリーミングレプリケーションを使用した冗長化構成の場合は、pg_stat_replicationビュー、pg_stat_replication_slotsビューを参照し、レプリケーション状態に異常がないかを確認する
        - ディスク容量に問題はないか
            - OSのdfコマンドやduコマンド、PostgreSQLのpg_database_size関数、pg_total_relation_size関数などディスク容量のデータベース、テーブル/インデックスのサイズを測定する。また、テーブルやインデックスが不必要に肥大化していないか、pg_stat_user_tablesビューやpg_stat_user_indexesビューも確認する
        - 想定通りの性能が出せているか
            - 想定よりも時間のかかったクエリを出力するように設定する。サーバーログの設定はpostgresql.congのlog_min_duration_statement
            - 以下は主な稼働統計情報ビューと確認すべきポイント

            | 可動統計情報ビュー | 対象 | 内容 | 該当例 |
            | --- | --- | --- | --- |
            | pg_stat_databese | データベース | コミット/ロールバック数 | xact_commit, eact_rollcack |
            | pg_stat_databese | データベース | データベースのキャッシュヒット率 | blks_read, blks_hit |
            | pg_stat_databese | データベース | デッドロック発生有無 | deadlocks |
            | pg_statio_user_tabels | テーブルとインデックス | テーブルのキャッシュ | heap_blks_read, heap_blks_hit |
            | pg_statio_user_tabels | テーブルとインデックス | インデックスのキャッシュヒット率 | idx_blks_read, idx_blks_hit |
            | pg_stat_activity | 稼働中のバックエンドプロセス | トランザクション実行時間 | backend_start, xact_start, query_start |
            | pg_stat_activity | 稼働中のバックエンドプロセス | 実行中のクエリ | wait_event_type, wait_event, state, query |
        - PostgreSQLのサーバログに異常を知らせる通知はないか
            - デフォルトでは最低限の情報しか出力しないようになっているため、必要な設定を施した上で定期的にログを確認する

## サーバーログの設定

### ログをどこに出力するか

- 以下postgresql.confのログ出力に関連するパラメータ

| パラメータ名 | 説明 |
| --- | --- |
| log_destination | ログの出力先(stderr, csvlog, syslog, eventlog) |
| logging_collector | stderr, csvlogの内容をファイルに保存するかどうか |
| log_directory | ログファイルを格納するディレクトリ |
| log_filename | ログファイル名。strftimeで標準的に扱われるエスケープシーケンス(%)を利用できる |

### ログに何を出力するか

- log_line_prefixののエスケープシーケンス例
    - log_line_prefixにはエスケープシーケンスを利用できる。

    ```sql
    log_line_prefix='(user:%u access to database:%d at [%m])'

    出力例
    (user: postgres access to database: postgres at [2022-06-18 01:38:35.627 JST])
    ```

- ログ出力条件に関するpostgresql.conf

| パラメータ名 | 説明 | 設定値(レベルが低い順) | デフォルト値 |
| --- | --- | --- | --- |
| client_min_messages | クライアントに送信するレベル | DEBUG5~DEBUG51 < LOG < NOTICE < WARNING < ERROR < FATAL < PANIC | NOTICE |
| log_min_messages | サーバログに書き込むレベル | DEBUG5~DEBUG51 < INFO < NOTICE < WARNING < ERROR < LOG < FATAL < PANIC | WARNING |
| log_min_error_statement | エラー原因のSQLを書き込むレベル | DEBUG5~DEBUG51 < INFO < NOTICE < WARNING < ERROR < LOG < FATAL < PANIC | ERROR |
- ログ内容出力に関するpostgresql.confのパラメータ

| パラメータ名 | 説明 |
| --- | --- |
| log_checkpoints | チェックポイントに関する情報の出力有無(on/off)。チェックポイントにどれくらいの時間がかかったのか、どの程度書き出したかなどの情報 |
| log_connections
log_disconnections | サーバへの接続/切断に関する情報の出力有無(on/off)。誰がどこから接続してきたかの情報 |
| log_lock_waits | ロック獲得のために一定時間以上待たされたデータベース、テーブル、接続などの情報 |
| log_autovacuum_min_duration | 設定した自動バキュームの実行時間(秒数)を超えた場合に実行内容を出力する(0は全て出力、-1は出力しない)デフォルトは-1 |
| log_line_prefix | 各ログ行の先頭に出力する情報。記述にはエスケープシーケンスを用いることが可能 |
| log_statement | どの種類のSQLの内容を出力するか指定する(ddl, mod, all, none(デフォルト)) |
| log_min_duration_statement | 指定した時間(ミリ秒)以上かかったSQLの内容を一部出力する(0は全て出力、-1(デフォルト)出力しない) |
| log_min_duration_sample | 指定した時間(ミリ秒)以上かかったSQLの内容を一部0は全て出力、-1(デフォルト)出力しない)
log_min_duration_statementより優先度が低いため、log_min_duration_sampleより大きい設定値を指定した場合は本設定は無視される |
| log_statement_sample_rate | log_min_duration_sampleによる出力量を指定する(1.0は出力対象のメッセージを全て出力する。0は出力しない) |
| log_transaction_sample_rate | 全てのトランザクションをログ出力する際に出力量を指定する(1.0は出力対象のメッセージを全て出力する。0は出力しない) |
- log_line_prefixのエスケープシーケンス

| パラメータ名 | 説明 |
| --- | --- |
| %u | ユーザ名 |
| %d | データベース名 |
| %r | ホスト名/IPアドレス、ポート番号 |
| %p | プロセス識別子(PID) |
| %m | ミリ秒付きタイムスタンプ |

### ログをどのように保持するか

- 適切なタイミングでログをローテーション(循環)する設定が必要
- log_destinationをsyslogに設定している場合、syslogのローテート機能を使う
- postgresqlが独自でファイルに出力している場合以下の設定を変更してローテーションする

| パラメータ名 | 説明 |
| --- | --- |
| log_rotation_age
log_rotation_size | 指定した時間(分)またはサイズでログファイルを循環する。両方とも指定可能 |
| log_rotation_on_rotation | ローテーション時に同じ名前のログファイルが存在する場合に、切り詰める/上書きする(on),追記するか(off)を設定 |
- ローテーション機能を有効にするには上記に加え、ローテーション時に異なるファイル名になるよう、log_filenameを以下のように設定する
