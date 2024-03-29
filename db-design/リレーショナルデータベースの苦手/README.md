## リレーショナルデータベースの苦手なこと

- tree(木)構造が苦手
- 木構造とは？
    - 会社の上司、社員構造みたいなもの。社長がトップにいて、その下に部下がたくさんいる構造
- リレーショナルデータベースで木構造を表すとなると隣接リストモデルが採用される
    - しかし、更新や検索のクエリが極めて複雑になり、パフォーマンスも悪いという欠点がある
    - これらの問題を解決するために生まれたものが、入れ子集合モデル
- 入れ子集合モデル
    - 入れ子集合モデルは、数学の集合みたいに親が子を円でくくる感じ。
    - テーブル定義は、円の左端と右端の座標のような感じ。
    - メリット
        - SQLが隣接リストモデルと比べてシンプルになる
    - デメリット
        - 更新処理など部下の追加では既存の社員の座標を広める必要がある
        - **入れ子区間モデル**を採用すれば、あまり無理に既存の社員の更新をしなくても大丈夫にはなる
- 経路列挙モデル
    - ディレクトリのように階層構造を定義する
    - テーブル定義では一カラムに全ての階層をいれる
    - メリット
        - 検索のパフォーマンスがよい。ユニークインデックスによる高速検索可能
    - デメリット
        - カラム文字列がかなり長くなる可能性ある
        - 同じ階層のものが把握できない。ただし番号を使うことで補える
        - しかしパスに番号を使うと削除追加などが複雑になる
        - 検索SQLはかなり実装依存になる
    - 更新が少なく、大量データの高速な検索に向いている
- **入れ子区間モデルが一番よさそう**
