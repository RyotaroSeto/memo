# go version 1.22

## for文のループ変数
### ループ変数のスコープの変更
- エスケープされるような場合はイテレーションごとに変数を作成

## range over integer
### for range文で整数が使用可能に
```go
func main(){
  for i := range 10 {
    fmt.Println(10 - i)
  }
}
```

## Go command
### ワークスペース単位のvendoring
- go work vendorコマンドでvendoringが可能
### モジュール外でのgo getが非サポート
### go mod initが他のツールからのインポートをサポートしなくなった
### go test -coverがテストがないパッケージのカバレッジサマリーを表示するようになった
### cgoが無効の場合に外部のリンカを呼び出すビルドはエラーになるようになった

## Trace
### トレーサーの大幅改善
- トレースツールのWeb UIの改善
- この改善によりFlight recorderなどが実装可能に

## Vet
### ループ変数の指摘がされなくなる
- loopvarの変更により不要になった
### 3つの新しいルールの追加
- missing values after append
- deferring time.Since
- mismatched key-value pairs in log/slog calls

## Runtime
### 型ベースGCのメタデータヒープオブジェクト近くに配置
- 1-3%のCPUパフォーマンスの改善
- メモリのオーバーヘッドが1%削減

## Compiler
### PGOを用いたビルドで高い割合のコードでdevirtualizationできるようになった
- PGO
- devirtualization: インタフェースのメソッド呼び出しの具象型のメソッド呼び出しに変換する
- PGOを有効にすると実行時に2~14%の改善がみられる
### Devirtulizationとインライン展開を交互に行うようになりより最適化されるようになった
### 新しいインライン展開のプレビュー

## Linker
### リンカの-sと-wフラグが全てのプラットフォームで一貫した動作になるようになった
- -s disable symbol table
- -w disable DWARF generation

## math/rand/v2
### 新しいmath/randパッケージ

## go/version package
### Goのバージョンがパースできる
- 言語バージョンの取得
- バージョンの比較
- 正しいバージョンか

## Enhanced routing patterns
### http.ServeMuxの改善
- HTTPメソッドでルーティングできる
  - HTTP Handler指定時にHTTP Methodを指定できるようになった
  - 昔は以下だった。chiなど使っていた
```go
// Go1.21前
h := func(w http.AResponseWriterm r *http.Request) {
  if r.Method != "GET" {
    w.WriteHeader(http.StatusMethodNotAllowed)
    return
  }
}
mux := http.NewServeMux()
mux.HandleFunc("/hello", h)

// 以下はchi
h := func(w http.AResponseWriterm r *http.Request) {
  w.Write([]byte("hello"))
}
r := chi.NewRouter()
r.Get("/hello", h)
```
- 以下はGo1.22以降
```go
h := func(w http.AResponseWriterm r *http.Request) {
  w.Write([]byte("hello"))
}
mux := http.NewServeMux()
mux.HandleFunc("Get /hello", h)
```
- パスにワイルドカードが使用できる
  - Pathから値を取得できるようになった
```go
// Go1.21前 無理やり自分で値をパースしていた
h := func(w http.AResponseWriterm r *http.Request) {
  userID := strings.TrimPrefix(r.URL.Path, "/user/")
  w.Write([]byte(userID))
}
mux := http.NewServeMux()
mux.HandleFunc("/hello", h)

// chi
h := func(w http.AResponseWriterm r *http.Request) {
  userID := chi.URLParam(e, "id")
  w.Write([]byte(userID))
}
r := chi.NewRouter()
r.HandleFunc("/user/{id}", h)
```
- 以下はGo1.22以降
```go
h := func(w http.AResponseWriterm r *http.Request) {
  userID := r.PathValue("id")
  w.Write([]byte(userID))
}
mux := http.NewServeMux()
mux.HandleFunc("/user/{id}", h)

## Minor changes to the library
### cmp.Or関数の追加
- cmp.Or
- 引数のうち最初のゼロ値ではない値を返す
### net/http
- ServeFileFS, FileServerFS, NewFileTransportFSが追加
### slices.Deleteが追加
### database/sqlにNull[T]が追加
