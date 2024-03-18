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
