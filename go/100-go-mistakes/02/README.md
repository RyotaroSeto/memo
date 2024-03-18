## init関数
- init関数はエラーを返さないので、エラーを通知する唯一の方法はパニックを起こし、アプリケーションを停止させること。
- init関数が役立つ例として、静的なHTTP設定を行うためにinit関数を使用
```go
func init(){
    redirect := func(w http.ResponseWriter, r *http.Reqest) {
        http.Redirect(w, r, "/". http.StatusFound)
    }
    http.HandleFunc("/blog", redirect)
    http.HandleFunc("/blog/", redirect)

    static := http.FileServer(http.Dir("static"))
    http.Handle("/favicon.ico", static)
    http.Handle("/fonts.css", static)
    http.Handle("/fonts/", static)

    http.Handle("/lib/godoc/", http.StripPrefix("/lib/godoc/", http.HandlerFunc(staticHandler)))
}
```
この例ではinit関数が失敗することはない。

### まとめるとinit関数が次のような問題を引き起こす可能性がある。
- エラー管理を制限することがある。
- テストの実装方法を複雑にする。
- 初期化で状態を設定する必要がある場合、それはグローバル変数を通して行わなければならない。

## ゲッターとセッターの利用
- ゲッターのメソッド名はBalance(GetBalanceではない)
- セッターのメソッド名はSetBalance

## インターフェイス汚染
- Goでインターフェイスが価値をもたらすと考えられる3つのユースケース
  - 共通の振る舞い
    - 複数の型が共通の振る舞いを実装している場合
    ```go
    type Interface interface {
        Len() int           // コレクション内の要素数取得
        Less(i, j int) bool // ある要素が別の要素より前へソートされなければならないか精査
        Swap(i, j int)      // 2つの要素を入れ替える
    }

    func IsSorted(data Interface) bool {
        n := data.Len()
        for i := n-1; i>0; i-- {
            if data.Less(i, i-1) {
                return false
            }
        }
        return true
    }
    ```
    このインターフェイスはインデックスに基づくすべてのコレクションをソートしているための一般的な動作を包含しているため、再利用性がとても高い。
  - 具体的な実装との分離
    ```go
    type CustomerService struct {
    	store mysql.Store // 具体的な実装に依存している
    }
    func (cs CustomerService) CreateNewCustomer(id string) error {
    	customer := Customer{id: id}
    	return cs.store.StoreCustomer(customer)
    }
    ```
    ではどうするか？
    ```go
    type customerStorer interface { // ストレージ抽象化を作成
    	StoreCustomer(Customer) error
    }
    type CustomerService struct {
    	storer customerStorer // CustomerServiceを具体的な実装から分離
    }
    func (cs CustomerService) CreateNewCustomer(id string) error {
    	customer := Customer{id: id}
    	return cs.storer.StoreCustomer(customer)
    }
    ```
  - 振る舞いの制限
    - 設定値を取得することにしか興味がなく、更新を防ぎたい場合`intConfigGetter`インターフェイスを作成する
    ```go
    type IntConfig struct {
    	value int
    }
    func (c *IntConfig) Get() int {
    	return c.value
    }
    func (c *IntConfig) Set(value int) {
    	c.value = value
    }
    type intConfigGetter interface {
	    Get() int
    }
    type Foo struct {
    	threshold intConfigGetter
    }
    func NewFoo(threshold intConfigGetter) Foo {
    	return Foo{threshold: threshold}
    }
    func (f Foo) Bar() {
    	threshold := f.threshold.Get()
    	log.Println(threshold)
    	_ = threshold
    }
    func main() {
    	foo := NewFoo(&IntConfig{value: 42})
    	foo.Bar()
    }
    ```
    - goでのインターフェイスはアーキテクチャに沿ってインターフェイスを作成するのではなく、インターフェイスが必要であると発見したら随時作成するもの。
    - インターフェイスを過剰に使用すると、コードの流れを複雑化してしまう。無駄な間接参照を追加することは、何の価値ももたらさない。それはコードを読み、理解し、推論することを困難にする。

## 生産者側のインターフェイス
- 顧客データを保存し、取得するためのパッケージを作成します。同じパッケージの中で、全ての呼び出しはインターフェイスを経由する必要がある。
- ほとんどの場合インターフェイスは消費者側にあるべき。(標準パッケージのように使用するべき)

## インターフェイスを返す
- インターフェイスを返すと依存関係ができてしまう
- インターフェイスを返す代わりに構造体を返す

## ジェネリクスをいつ使うべきか混乱する
- **データ構造**
  - 例えば、二分木、リンクリスト、ヒープなどを実装する場合、ジェネリクスを使って要素の型を表せる
- **任意の型のスライス、マップ、チャネルを処理する関数**
  - 例えば、2つのチャンネルをマージする関数は、どのようなチャネル型でも動作する。したがって、型パラメータを使用してチャネル型を表せる。
  ```go
  func merge[T any](ch1, ch2 <-chan T) <- chan T {
    // ...
  }
  ```
- **型ではなく振る舞いを表す**
  - 例えば、sortパッケージには、3つのメソッドを持つsort.Interfaceがある。
  ```go
  type Interface interface {
    Len() int
    Less(i, j int) bool
    Swap(i, j int)
  }
  // 型パラメータを使用すればソート動作をくくり出せる
  type SliceFn[T any] struct { // ← 型パラメータを使う
	S       []T
	Compare func(T, T) bool  // 2つのT要素を比較する
  }
  func (s SliceFn[T]) Len() int           { return len(s.S) }
  func (s SliceFn[T]) Less(i, j int) bool { return s.Compare(s.S[i], s.S[j]) }
  func (s SliceFn[T]) Swap(i, j int)      { s.S[i], s.S[j] = s.S[j], s.S[i] }

  // そしてSliceFn構造体はsort.Interfaceを実装してるので、sort.Sort(sort.Interface)関数を使用してスライスをソートできる。
  func main() {
  	s := SliceFn[int]{
  		S: []int{3, 2, 1},
  		Compare: func(a, b int) bool {
  			return a < b
  		},
  	}
  	sort.Sort(s)
  	fmt.Println(s.S)
    //[1, 2, 3]
  }
  ```

- 逆にジェネリクスを使用しない方が良い例
  ```go
  func foo[T io.Writer](w T) {
    b := getBytes()
    _ , _ = w.Write()
  }
  // この場合,w引数を直接io.Writerにすべき。
  // ジェネリクスは必須ではない。Go1.18まで、ジェネリクスがなしだったため無理に使用しなくてもよい
  // 不必要な抽象化でコードを汚染しないようにし、具体的な問題に解決することに集中すること
  // つまり、時期尚早に型パラメータを使うべきではない。ジェネリクスを使うことを検討するのは、定期的に決まりきったコードを書こうとする時まで待つこと。
  ```

## 型埋め込みで起こり得る問題点を意識しない
- フィールドへのアクセスを簡単にするシンタックスシュガーという役割だけで使うべきではない。(Foo.Bar.Baz()の代わりにFoo.Baz()といったように)
- 外部から隠蔽したいデータ(フィールド)や振る舞い(メソッド)をプロモートすべきではない。例えば構造体内の非公開であるべきロック動作にクライアントがアクセスできるようすべきではない・
```go
// muなしはNG
type InMem struct {
	sync.Mutex
	m  map[string]int
}
// muをつける
type InMem struct {
	mu sync.Mutex
	m  map[string]int
}
```

## 関数オプションパターンを使わない

## ユーティリティパッケージの作成
- 意味のない名前で、common,utilなどのパッケージは作成しない

## コードのドキュメントがない
- 構造体、インターフェイス、関数どれであっても、文章化する。
- 慣習として、各コメントは句点で終わる完全な文であるべき
- 関数を文章化する場合、その関数がどのように行うかではなく、何を意図しているのかを強調する必要がある

