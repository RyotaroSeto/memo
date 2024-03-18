# テスト

## テストを分類しない

### テストを分類するための 3 つの方法

- テストファイルのレベルでビルドタグを使う
- 特定のテストを示すために環境変数を使う
- ショートモードを使って、テストの実行時間に基づいて分類する

## -race フラグを有効にしない

### Go にはデータ競合を検出するための標準の競合検出器がある

`go test -race ./...`
でテストを実行。

留意点として以下

- メモリ使用量は 5 倍から 10 倍ほど増える
- 実行時間は 2 倍から 20 倍ほど長くなる

並行コードはデータ競合に対して徹底的にテストされるようにすべき！！！

## テスト実行モードを使わない

### -parallel フラグ

`t.Parallel`を使うと簡単に並列テストが実施できる。

テストを実行する際に`-parallel フラグ`を使って最大数の値を変更できる

```go
go test -parallel=16
func TestFoo(t *testing.T) {
    t.Parallel
}
```

### -shuffle フラグ

テストの実行順序をランダムにできる

ランダムにする理由は、テストを書くときのベストプラクティスはここのテストを独立させること。テストの共有変数や実行順序に依存すべきではない

`go test -shuffle=on -v .`の結果のシード値を渡して実施することによってその失敗した順番が再度再現される。

## テーブル駆動テストを使わない

`t.Parallel()`の注意点として tt をシャドウすること

```go
func TestRemoveNewLineSuffix(t *testing.T) {
	tests := map[string]struct {
		input    string
		expected string
	}{
		`empty`: {
			input:    "",
			expected: "",
		},
	}
	for name, tt := range tests {
		tt := tt // ttをシャドウする
		t.Run(name, func(t *testing.T) {
			t.Parallel()
			got := removeNewLineSuffixes(tt.input)
			if got != tt.expected {
				t.Errorf("got: %s, expected: %s", got, tt.expected)
			}
		})
	}
}
```

## 単体テストをスリープする P299~302 ゴルーチンのテストを勉強してからもう一度みる

- 並行処理のコードはスリープを使用してテストされることが多い
- テストの中で time.Sleep を呼び出すことは成功したり失敗したりなる可能性が高くなる
- そのようなコードは書かないようにする技法について

## テスト用ユーティリティパッケージを使っていない

### httptest パッケージ

サーバーを書くにしてもクライアントを書くにしても以下のパッケージは便利なため使用を検討すること

- `httptest.NewRequest`
- `httptest.NewServer`

### iotest パッケージ

独自の`io.Reader`を実装する場合、`iotest.TestReader`を使ってテストをすることを必ず思い出す。

- リーダーが正しく動作していることをテストしてくれる
  - 読み込んだバイト数を正確に返したり、提供されたスライスを埋めたりといったことができる

`io.Reader`、`io.Writer`を扱うときは`iotest`パッケージを覚えておくべき

## Go テストの機能を全て使いこなしたい

`createCustomer`関数の引数に`t *testing.T`を渡すことによって`err`を返す必要がなくなりテストが簡素化される

### ユーティリティ関数

```go
func TestCustomer1(t *testing.T) {
	customer, err := createCustomer1("foo")
	if err != nil {
		t.Fatal(err)
	}
	// ...
	_ = customer
}

func createCustomer1(someArg string) (Customer, error) {
	customer, err := customerFactory(someArg)
	if err != nil {
		return Customer{}, err
	}
	return customer, nil
}

func TestCustomer2(t *testing.T) {
	customer := createCustomer2(t, "foo")
	// ...
	_ = customer
}

func createCustomer2(t *testing.T, someArg string) Customer {
	customer, err := customerFactory(someArg)
	if err != nil {
		t.Fatal(err)
	}
	return customer
}

func customerFactory(someArg string) (Customer, error) {
	if someArg == "" {
		return Customer{}, errors.New("empty")
	}
	return Customer{id: someArg}, nil
}
```

### 事前準備と事後処理

- 事前準備`setup`と事後処理`teardown`と書く慣習がある

テストの最後に`t.Cleanup`に提供されたクロージャが実行されることで、db 変数のクローズに単体テストが責任を持つ必要がなくなる

```go
func createConnection(t *testing.T, dsn string) *sql.DB {
	db, err := sql.Open("mysql", dsn)
	if err != nil {
		t.FailNow()
	}
	t.Cleanup( // テストの最後に実行される関数を登録する
		func() {
			_ = db.Close()
		})
	return db
}
```

- パッケージごとの事前準備と事後処理を行う場合は、`TestMain`関数を使用する
- この関数は全てのテストを実行するための単一の Run メソッドを公開している`m *testing.M`引数を受け取る。したがって、テストの呼び出しを事前準備と事後処理関数で囲むことができる
- このコードは全てのテストの前に 1 度 MySQL を設定し、テスト終了後に MySQL を終了させる

```go
func TestMain(m *testing.M) {
	setupMySQL()
	code := m.Run()
	teardownMySQL()
	os.Exit(code)
}
```
