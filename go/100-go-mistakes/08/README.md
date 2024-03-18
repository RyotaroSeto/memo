# 並行処理:基礎編

## チャネルとミューテックスの使い分けに悩む

- 並列ゴルーチン間の同期
  - ミューテックスによって達成されるべき。`データの同期とか?`
- 並行ゴルーチンの場合(強調や所有権の移転)
  - チャネルによって達成されるべき。`シグキルとか?`

## 競合問題を理解していない

以下のような書き方だと`i`がどんな数字になるか不明確である。

```go
	i := 0
	var wg sync.WaitGroup
	wg.Add(2)
	go func() {
		defer wg.Done()
		i++
	}()
	go func() {
		defer wg.Done()
		i++
	}()

	wg.Wait()
	fmt.Println(i)
```

### 解決策

アトミックをインクリメントするようにする。
アトミックな操作には割り込みができないので、同時に 2 つのアクセスを防げる。

```go
	var i int64

	var wg sync.WaitGroup
	wg.Add(2)
	go func() {
		defer wg.Done()
		atomic.AddInt64(&i, 1)
	}()

	go func() {
		defer wg.Done()
		atomic.AddInt64(&i, 1)
	}()

	wg.Wait()
	fmt.Println(i)
```

その他解決策として排他制御を利用する。こちらの方が良い。`atomic`はスライスやマップなどには使えないから

```go
	i := 0
	mutex := sync.Mutex{}

	var wg sync.WaitGroup
	wg.Add(2)
	go func() {
		defer wg.Done()
		mutex.Lock()
		i++
		mutex.Unlock()
	}()

	go func() {
		defer wg.Done()
		mutex.Lock()
		i++
		mutex.Unlock()
	}()

	wg.Wait()
	fmt.Println(i)
```

他の方法としては以下。同じメモリ位置を共有しないようにし、代わりにゴルーチン間で通信させる。

```go
	i := 0

	var wg sync.WaitGroup
	wg.Add(2)
	ch := make(chan int)

	go func() {
		defer wg.Done()
		ch <- 1
	}()

	go func() {
		defer wg.Done()
		ch <- 1
	}()

	i += <-ch
	i += <-ch

	wg.Wait()
	fmt.Println(i)
```

### 競合のまとめとして

- データの競合は複数のゴルーチンが同時に同じメモリ位置にアクセスし、少なくとも 1 つのゴルーチンが書き込みを行っているときに発生する

## 作業負荷の種類による並行処理の影響を理解していない

- 作業負荷の種類を知ることは、並行アプリケーション設計する際に重要
- ほとんどの場合、ベンチマークによって仮定を検証する必要がある。
- CPU の速度
  - マージソートアルゴリズムを実行する場合。この作業負荷は CPU バウンドと呼ばれる
  - 作業負荷、最適なゴルーチン数は利用可能なスレッド数に近い値
- I/O の速度
  - REST 呼び出しやデータベースのクエリを行う場合。この作業負荷は I/O バウンドと呼ばれる
  - 作業負荷は外部システムに依存
- 利用可能メモリ量
  - この作業負荷はメモリバウンドと呼ばれる。
  - ここ数十年の間でメモリが安価になったことを考えると、メモリバウンドは現在では最も稀

### 作業負荷が I/O バウンドの場合、プールの大きさ値は外部システムに依存する

- スループットを最大化したい場合、外部システムはどれくらいの同時アクセスに対応できるか

### 作業負荷が CPU に依存している場合、GOMAXPROCS に依存するのがベストプラクティス

- `GOMAXPROCS`は実行中のゴルーチンに割り当てられる OS スレッド数を設定する環境変数
- デフォルトでは、論理 CPU の数に設定されている
- `GOMAXPROCS`の値を更新するために、`runtime.GOMAXPROCS(int)`関数が使える。引数に`0`を指定して呼び出すと値は更新されずに現在の値が返却

## Go のコンテキストを誤解する

- `context.Context`型は重要な概念
- **Context は API の境界を超えて、デッドライン、キャンセル通知、他の値を伝える**
- ユースケース
  - タイムアウト
  - キャンセルシグナル
- kafka メッセージを発行するために Sarama などを使用してブローカーにメッセージを発行するための関数呼び出し想定
- タイムアウトを受け取るために`ctx`を return している

```go
type publisher interface {
    Publish(ctx context.Context, position flight.Position) error
}

type publishHandler struct {
    pub publisher
}

func (h publishHandler) publishPosition(position flight.Position) error {
    // ↓4秒後にタイムアウトするコンテキストを生成する
    ctx, cancel := context.WithTimeout(context.Background(), 4*time.Second)
    defer cancel()
    return h.pub.Publish(ctx, position) // 生成したコンテキストを返す
}
```

### キャンセルシグナル

ファイルから読み取り続け、更新をみるウォッチャー。提供されたコンテキストの有効期限が切れたり、キャンセルされたりすると`ctx`で渡ってきて close される

### コンテキスト値

- 他コンテキストの使用例として、キーバリューのリストを運ぶこと。

バリューを伝えるコンテキストは以下

```go
ctx := context.WithValue(parentCtx, "key", "value")
```

異なるパッケージの 2 つの関数が同じ文字列値をキーとして使うことで、衝突する可能性ある。衝突すると、後者が前者の値を上書きする。したがって、コンテキストのキーを扱う際のベストプラクティスは下記のように公開されていない独自型を作成すること

```go
type key string

const myCustomKey key = "key"

func f(ctx context.Context) {
    ctx = context.WithValue(ctx. myCustomKey, "foo")
}
```

どのコンテキストを使うか迷った時は`context.Background()`で空のコンテキストを渡すのでなく、`context.TODO()`を使うべき。
