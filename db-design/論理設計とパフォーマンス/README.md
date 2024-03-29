## 論理設計とパフォーマンス

- パフォーマンス向上は正規化とトレードオフ。
- 非正規化は検索パフォーマンスは向上させるが、更新パフォーマンスを低下させる
  - 非正規化した分、更新項目は増える
- 非正規化はデータのリアルタイム性(鮮度)を低下させる
  - 下記のテーブルのパフォーマンス向上後に追加した「商品数」の更新のタイミングじゃ 1 日 1 回か、30 分に 1 回か、即時に反映するかなど。
- 非正規化は後続の工程で設計変更すると出戻りが大きい
  - システムが出来上がり、性能試験を実施したところ性能が出なかったので、テーブル構成を変更したいとなると、アプリケーションも大きく変更しなければならない。

### 受注

| 受注 ID | 受注日     | 注文名 |
| ------- | ---------- | ------ |
| 1       | 2024-01-05 | 加藤   |
| 2       | 2024-01-05 | 藤本   |
| 3       | 2024-01-06 | 三島   |

受注明細

| 受注 ID | 受注日 | 商品名   |
| ------- | ------ | -------- |
| 1       | 1      | マカロン |
| 1       | 2      | 紅茶     |
| 1       | 3      | オイル   |
| 1       | 4      | チョコ   |
| 2       | 1      | 紅茶     |
| 2       | 2      | 日本茶   |
| 2       | 3      | アイロン |
| 3       | 1      | 米       |

- 上記テーブルで「受注日ごとに何個の商品が注文されているか」の回答 SQL

```sql
SELECT 受注.受注日.
       COUNT(*) AS 商品数
  FROM 受注 INNER JOIN 受注明細
　　　　　　　　 ON 受注.受注ID = 受注明細.受注ID
GROUP BY 受注.受注日;

受注日       商品数
----------  ------
2024-01-05     7
2024-01-06     1
```

- しかし、受注明細が増えるにつれてパフォーマンスが悪化する
- 以下のように受注テーブルに商品数の列を追加すれば解決する
  - しかし、更新時における問題が発生するため、トレードオフではある。

| 受注 ID | 受注日     | 注文名 | 商品数 |
| ------- | ---------- | ------ | ------ |
| 1       | 2024-01-05 | 加藤   | 7      |
| 2       | 2024-01-05 | 藤本   | 7      |
| 3       | 2024-01-06 | 三島   | 1      |

- 次のパターンとして「受注日が 2024-01-05~2024-01-06 の期間に注文された商品一覧を出力する」

```sql
SELECT 受注.受注日.
       受注明細.商品名
  FROM 受注 INNER JOIN 受注明細
　　　　　　　　 ON 受注.受注ID = 受注明細.受注ID
WHERE 受注.受注日 BETWEEN '2024-01-05' AND '2024-01-06'

受注ID       商品名
----------  ------
1           マカロン
1           紅茶
1           オイル
1           チョコ
2           紅茶
2           日本茶
2           アイロン
3           米
```

- 性能観点からするとコストが高いため受注明細に受注日を追加

| 受注 ID | 受注日 | 商品名   | 受注日     |
| ------- | ------ | -------- | ---------- |
| 1       | 1      | マカロン | 2024-01-05 |
| 1       | 2      | 紅茶     | 2024-01-05 |
| 1       | 3      | オイル   | 2024-01-05 |
| 1       | 4      | チョコ   | 2024-01-05 |
| 2       | 1      | 紅茶     | 2024-01-05 |
| 2       | 2      | 日本茶   | 2024-01-05 |
| 2       | 3      | アイロン | 2024-01-05 |
| 3       | 1      | 米       | 2024-01-06 |

```sql
SELECT 受注ID.
       商品名
  FROM 受注明細
WHERE 受注日 BETWEEN '2024-01-05' AND '2024-01-06'
```
