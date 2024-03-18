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
```

## Go 1.22のServeMuxとchiの違い
- ルーティング判定条件
  - chiは完全一致
  - http.ServeMuxは前方一致
    - Go1.22からPathの末尾に{$}を指定することで完全一致となる
- Valueのパターンマッチ
  - chiは以下のようにパターンを指定できる。http.ServeMuxではできない
  - 正規表現
    - `/date/{yyyy:\\d\\d\\d\\d}/{mm:\\d\\d}/{dd:\\d\\d}`
    - `/date/2017/04/01`
  - 部分一致
    - `/user-{userID}`
    - `/user-12345`
  - 複数のValue
    - `/{yyyy}-{mm}-{dd}`
    - `/2024-10-10`
  - GraphQLだとValueのパターンマッチを使う機会がないのでhttp.ServeMuxに以降できるが、RESTだと厳しい
- ルーティングやミドルウェアの設定方法
  - chiではUseによるミドルウェアの設定。Routeによるネスト
  - https://github.com/go-michi   michiを作った。(100%ServeMux互換)
