## データ型

- char型
    - 固定長データ型。末尾に空白が存在することを前提として使用
- varchar型
    - 可変長データ型。
- text型
    - 最大1GBまで格納可能
- postgresのconcat関数を使うと文字列連結できる

```sql
SELECT CONCAT('Hello ', 'world'); # 'Hello world'
```
