# 並行処理:実践編

## 不適切なコンテキストの伝搬

HTTP リクエストに関連付けられたコンテキストは、さまざまな状況でキャンセルできることを知っておくべき

- クライアントのコネクションが終了した時
- HTTP/2 リクエストの場合、リクエストがキャンセルされた時
- レスポンスがクライアントに書き戻された時

上記で最初の 2 つのケースは正しく動作する。しかし最後のケースの場合、レスポンスがクライアントに書き込まれると、リクエストに関連付けられたコンテキストはキャンセルされる。よって競合状態に直面する

- Kafka 発行後にレスポンスが書き込まれると、レスポンスの返却とメッセージの発行の両方が成功する
- しかし、Kafka 発行前または発行中にレスポンスが書き込まれた場合、メッセージは発行されないはず

後者では HTTP レスポンスを素早く返したので publish の呼び出しはエラーを返す。解決方法の 1 つとして親コンテキストを伝搬させないこと。空コンテキストで publish を呼び出す

```go
// NGパターン
func handler1(w http.ResponseWriter, r *http.Request) {
	response, err := doSomeTask(r.Context(), r)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	go func() {
        // HTTPのコンテキストを使用している
		err := publish(r.Context(), response)
		// Do something with err
		_ = err
	}()

	writeResponse(response)
}
// OKパターン
func handler2(w http.ResponseWriter, r *http.Request) {
	response, err := doSomeTask(r.Context(), r)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	go func() {
        // HTTPリクエストのコンテキストの代わりに空コンテキストを使う
		err := publish(context.Background(), response)
		// Do something with err
		_ = err
	}()

	writeResponse(response)
}
```

もしコンテキストに有用な値が含まれていた場合、HTTP リクエストと Kafka 発行を関連付けられる。理想的には潜在的な親コンテキストのキャンセルから切り離されて、値を伝搬する新たなコンテキストを持ちたい。可能な解決策として、提供されているコンテキストに似た独自の Go のコンテキストを実装し、キャンセルシグナルを伝えないようにする。`context.Context`は以下の 4 つのメソッドを含むインターフェイス

```go
type Context interface {
    DeadLine() (deadline time.Time, ok bool)
    Done() <-chan struct{}
    Err() error
    Value(key any) any
}
```

コンテキストのデッドラインは DeadLine メソッドで管理され、キャンセルシグナルは Done と Err メソッドで管理されている。デッドラインが過ぎた時、あるいはコンテキストがキャンセルされた時、Done はクローズされたチャネルを返し、Err はエラーを返さなければならない。最後に、値は Value メソッドに伝搬される。

以下が親コンテキストからキャンセルシグナルを切り離す独自コンテキスト

```go
type detach struct {
	ctx context.Context
}

func (d detach) Deadline() (time.Time, bool) {
	return time.Time{}, false
}

func (d detach) Done() <-chan struct{} {
	return nil
}

func (d detach) Err() error {
	return nil
}

func (d detach) Value(key any) any {
	return d.ctx.Value(key)
}
func handler3(w http.ResponseWriter, r *http.Request) {
    // ...
	go func() {
		err := publish(detach{ctx: r.Context()}, response)
		// Do something with err
		_ = err
	}()
    // ...
}
```

親コンテキストを呼び出して値を取得する Value メソッドを除いて、他のメソッドはデフォルト値を返すため、コンテキストが期限切れやキャンセルだとみなされることはない。独自コンテキストのおかげで、publish を呼び出せて、かつキャンセルシグナルを切り離せる。これで、publish に渡されたコンテキストは期限切れやキャンセルになることなく、親コンテキストの値を伝搬する。

## ゴルーチンを停止するタイミングを分からずに起動する

### 具体的な例として

newWathcer が外部監視するゴルーチン。以下は main のゴルーチンが終了するとき(OS シグナルが発生するか、決まった量の処理が完了したため終了)、アプリケーションが終了してしまう。したがって watcher が作成した資源はグレースフルにクローズされない。どう防げるか？

```go
package main

func main() {
	newWatcher()

	// Run the application
}

func newWatcher() {
	w := watcher{}
	go w.watch()
}

type watcher struct { /* Some resources */
}

func (w watcher) watch() {}
```

1 つの選択肢として main がリターンした時にキャンセルされるようなコンテキストを newWatcher に渡すこと。

```go
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	newWatcher(ctx)
func newWatcher(ctx context.Context) {
	w := watcher{}
	go w.watch(ctx)
}
```

コンテキストがキャンセルされたら、watcher 構造体はその資源をクローズする必要がある。しかし、上記で watch にその時間があることを保証できるか。間違いなくその時間はなく、それは設計上の欠陥。
問題はゴルーチンを停止させるのに、キャンセルシグナルを使用したこと。資源がクローズされるまで、親ゴルーチンを待たせていないため、待たせるようにする。

watcher に資源をクローズするタイミングを知らせるのでなく、アプリケーションが終了する前に資源がクローズされることを保証する。

```go
func main() {
	w := newWatcher()
	defer w.close() //　closeメソッドの呼び出しを遅延させる

	// Run the application
}

func newWatcher() watcher {
	w := watcher{}
	go w.watch()
	return w
}

func (w watcher) watch() {}

func (w watcher) close() {
	// Close the resources
}
```

## select とチャネルを使って、決定的な動作を期待する

- listing1 だと結果が 0,1,2,disconnection, return や disconnection, return など曖昧
- listing2 にすることで全ての messageCh を出力してから disconnection, return する
  - select 分での default ケースが選択されるのは、他のケースがどれも一致しない場合のみ。
  - messageCh にメッセージが残っている限り、select は常に default よりも最初のケースを優先する

```go
func main() {
	messageCh := make(chan int, 10)
	disconnectCh := make(chan struct{})

	go listing2(messageCh, disconnectCh)

	for i := 0; i < 10; i++ {
		messageCh <- i
	}
	disconnectCh <- struct{}{}
	time.Sleep(10 * time.Millisecond)
}
func listing1(messageCh <-chan int, disconnectCh chan struct{}) {
	for {
		select {
		case v := <-messageCh:
			fmt.Println(v)
		case <-disconnectCh:
			fmt.Println("disconnection, return")
			return
		}
	}
}
func listing2(messageCh <-chan int, disconnectCh chan struct{}) {
	for {
		select {
		case v := <-messageCh:
			fmt.Println(v)
		case <-disconnectCh:
			for {
				select {
				case v := <-messageCh: // 残りのメッセージを読み込む
					fmt.Println(v)
				default:
					fmt.Println("disconnection, return")
					return
				}
			}
		}
	}
}
```

**これは複数のチャネルから受信する場合に 1 つのチャネルから残りの全てのメッセージを確実に受信するための方法**

- select を複数のチャネルで使い、複数のケースがある場合、ソースコード上に書かれた順で最初のケースが自動的に選択されるわけではないことに注意。Go はランダムに選択するのでどのケースが選択されるわけではない。
- この動作を克服するためには、単一の生産者ゴルーチンの場合、バッファなしチャネルか 1 つだけのチャネルだけを使う
  - 複数の生産者ゴルーチンの場合、内側の select と default を使って優先順位を処理できる

## 通知チャネルを使わない

ある接続断が発生したときに通知するチャネルを作った。

`disconnectCh := make(chan bool)`

- このチャネルが API とやりとりする。ブーリアンチャネルなので ture か false のどちらか。
- true は何を伝えるのか明らかだが、false は何を意味するか。
  - 接続を切れていないことを意味するのか？この場合どのくらいの頻度で接続が切れていないというシグナルを受信するのでしょうか?再接続したという意味か？
- false を受信することを期待すべきか?おそらく true メッセージだけを受信することを期待すべき。
  - もしそうなら、ある情報を伝えるために特定の値は必要ないことになり、。データがないチャネルが必要。
  - それを扱う慣用的な方法は、空構造体のチャネルである`chan struct{}`
- Go は空構造体はフィールドを持たない構造体。アーキテクチャに関係なく以下の大きさは 0 バイト
  - なぜ空インターフェイス(`var i interface{}`)を使わないかは 0 バイトではないから。

```go
var s struct{}
fmt.Println(unsafe.Sizeof(s)) // 0
```

## nil チャネルを使っていない

以下はチャネルのゼロ値は nil なので nil。ゴルーチンは永遠に待たされる

```go
var ch chan int // ←nil チャネル
<- ch
```

2 つのチャネルを 1 つのチャネルにマージする関数を作るとする。

```go
// これではch1から全てを受信した後、ch2から受信する。
// つまりch1がクローズされるまでch2の受信が行われない。
// 両方のチャネルから同時で受信したい
func merge1(ch1, ch2 <-chan int) <-chan int {
	ch := make(chan int, 1)

	go func() {
		for v := range ch1 {
			ch <- v
		}
		for v := range ch2 {
			ch <- v
		}
		close(ch)
	}()

	return ch
}

// これは永遠にclose(ch)に到達しない。
func merge2(ch1, ch2 <-chan int) <-chan int {
	ch := make(chan int, 1)

	go func() {
		for {
			select {
			case v := <-ch1:
				ch <- v
			case v := <-ch2:
				ch <- v
			}
		}
		close(ch)
	}()

	return ch
}

// openブーリャンを使用してch1がオープンされているか否か確認できる
// 重大な問題として、2つのチャネルのどちらかがクローズされると、forループがビジーウェイトループとして動作する
// ビジーウェイトループとはもう一方のチャネルが新たにメッセージが受信されなくてもループし続ける
func merge3(ch1, ch2 <-chan int) <-chan int {
	ch := make(chan int, 1)
	ch1Closed := false
	ch2Closed := false

	go func() {
		for {
			select {
			case v, open := <-ch1:
				if !open {
					ch1Closed = true
					break
				}
				ch <- v
			case v, open := <-ch2:
				if !open {
					ch2Closed = true
					break
				}
				ch <- v
			}

			if ch1Closed && ch2Closed {
				close(ch)
				return
			}
		}
	}()

	return ch
}

// ここでnilチャネルを使うべき。nilチャネルからの受信は永遠に待たされる。
func merge4(ch1, ch2 <-chan int) <-chan int {
	ch := make(chan int, 1)

	go func() {
		for ch1 != nil || ch2 != nil {
			select {
			case v, open := <-ch1:
				if !open {
					ch1 = nil
					break
				}
				ch <- v
			case v, open := <-ch2:
				if !open {
					ch2 = nil
					break
				}
				ch <- v
			}
		}
		close(ch)
	}()

	return ch
}

```

## チャネルの大きさに迷う

- バッファ有チャネル
  - `ch1 := make(chan int, 1)`
- バッファ無チャネル

  - `ch1 := make(chan int, 0)`
  - `ch1 := make(chan int)`

- バッファありチャネルは送信元はチャネルがいっぱいではない間はメッセージを送信できる。
- チャネルがいっぱいになると、受信側のゴルーチンがメッセージを受信するまで待たされる。
  例えば以下。

```go
ch3 := make(chan int, 1)
ch3 <-1 // 待たされない
ch3 <-2 // 待たされる
```

### バッファなしチャネルが必要なケース

- 同期が必要な場合。
  - 複数のゴルーチンが既知の状態にあることが保証される。
  - 1 つのゴルーチンはメッセージを受信し、もう 1 つのゴルーチンはメッセージを送信する
  - よってバッファなしチャネルは推定しやすいため、明らかなデッドロックが、バッファ有チャネルではわかりにくいデッドロックになる可能性がある

### バッファありチャネルが必要なケース

- ワーカープールパターンを使う際は、共有チャネルにデータを送信する必要があるため、固定数のゴルーチンを生成することになる。この場合、チャネルの大きさを生成されたゴルーチン数にすることができる
- レートリミットの問題でチャネルを使う場合。リクエスト数を制限することで資源の利用を強制する必要がある場合、制限値に応じてチャネルの大きさを設定すべき。
- 使用する場合は基本的にバッファ 1 を使うこと。他の値を使うかは正確なプロセスを用いて慎重に決めるべき

## 文字列のフォーマットで起こり得る副作用を忘れる

- `UpdateAge1`は`age < 0`の場合、`String`メソッドが呼ばれるためデッドロックされパニックになる
- `UpdateAge2`にすればデッドロックされない。しかし条件によっては、ミューテックスロックの範囲を制限することは簡単ではなし、可能でもない。この状況だと文字列のフォーマットに細心の注意を払わなければならない。
- `UpdateAge3`は`id`フィールドに直接アクセスして顧客 ID を記録するだけなため、デッドロックにならない

```go
type Customer struct {
	mutex sync.RWMutex
	id    string
	age   int
}
func (c *Customer) UpdateAge1(age int) error {
	c.mutex.Lock()
	defer c.mutex.Unlock()

	if age < 0 {
		return fmt.Errorf("age should be positive for customer %v", c)
	}

	c.age = age
	return nil
}
func (c *Customer) UpdateAge2(age int) error {
	if age < 0 {
		return fmt.Errorf("age should be positive for customer %v", c)
	}

	c.mutex.Lock()
	defer c.mutex.Unlock()

	c.age = age
	return nil
}
func (c *Customer) UpdateAge3(age int) error {
	c.mutex.Lock()
	defer c.mutex.Unlock()

	if age < 0 {
		return fmt.Errorf("age should be positive for customer id %s", c.id)
	}

	c.age = age
	return nil
}
func (c *Customer) String() string {
	c.mutex.RLock()
	defer c.mutex.RUnlock()
	return fmt.Sprintf("id %s, age %d", c.id, c.age)
}
```

## append でデータ競合を生み出す

### スライスとマップでのデータ競合

- 少なくとも 1 つのゴルーチンが値を更新している状態でスライスの同じインデックスにアクセスすることは、データ競合。複数のゴルーチンが同じメモリ位置にアクセスしている
- 操作に関係なくスライスの異なるインデックスにアクセスすることは、データ競合ではない。異なるインデックスは異なるメモリ位置を意味する
- 少なくとも 1 つのゴルーチンが更新している状態で、同じマップ（同じキーか違うキーかにかかわらず）にアクセスすることは、データ競合。

  - なぜスライスのデータ構造と違うのかというと、マップはバケット配列であり、各バケットはキーと値の組の配列へのポインタ。

- 並行処理でスライスを扱っている場合、スライスに対して append を使うことはデータ競合が発生しているかもしれないことを覚えおく。スライスがいっぱいの場合 append ではデータ競合はないがそうではに場合、複数のゴルーチンが配列の同じインデックスを更新するためデータ競合になる
- したがって複数のゴルーチンが配列の同じインデックスを更新するためデータ競合になる場合は append は避けるべき

## スライスとマップで誤ったミューテックスを使う

- `AverageBalance1`ではデータの競合が発生する。
  - `c.balances`と同じデータバケットを参照する新たなマップを`balances`に代入している。2 つのゴルーチンは同じデータの集まりに対して操作を行い、そのうちの 1 つはデータの集まりを変更している
  ```go
  // これはs2 := s1が新たなスライスの変数を作成しているが、同じ長さ同じ容量を持ち、同じ基底配列を参照しているため
  s1 := []int{1, 2, 3}
  s2 := s1
  s2[0] = 42
  fmt.Println(s1) // [42, 2, 3]
  ```

```go
type Cache struct {
	mu       sync.RWMutex
	balances map[string]float64
}

func (c *Cache) AddBalance(id string, balance float64) {
	c.mu.Lock()
	c.balances[id] = balance
	c.mu.Unlock()
}

func (c *Cache) AverageBalance1() float64 {
	c.mu.RLock()
	balances := c.balances
	c.mu.RUnlock()

	sum := 0.
	for _, balance := range balances {
		sum += balance
	}
	return sum / float64(len(balances))
}

// 反復処理が重くない場合、解決策として関数全体を保護すべき。
func (c *Cache) AverageBalance2() float64 {
	c.mu.RLock()
	defer c.mu.RUnlock()

	sum := 0.
	for _, balance := range c.balances {
		sum += balance
	}
	return sum / float64(len(c.balances))
}

// 反復処理が軽量でない場合、データのコピー作業し、そのコピーしょりだけ保護する。
// この操作は操作が速くない場合、外部データベースを呼び出す必要がある場合など有効。
func (c *Cache) AverageBalance3() float64 {
	c.mu.RLock()
	m := make(map[string]float64, len(c.balances))
	for k, v := range c.balances {
		m[k] = v
	}
	c.mu.RUnlock()

	sum := 0.
	for _, balance := range m {
		sum += balance
	}
	return sum / float64(len(m))
}
```

## sync.WaitGroup の誤用

### sync.WaitGroup の Add 操作は親ゴルーチンの中で新たにゴルーチンを生成する前に使わなければならない。Done 操作は生成されてゴルーチンの中で行う。

listing1 が NG。listing2 と listing3 は OK

```go
func listing1() {
	wg := sync.WaitGroup{}
	var v uint64

	for i := 0; i < 3; i++ {
		go func() {
			wg.Add(1)
			atomic.AddUint64(&v, 1)
			wg.Done()
		}()
	}

	wg.Wait()
	fmt.Println(v)
}

func listing2() {
	wg := sync.WaitGroup{}
	var v uint64

	wg.Add(3)
	for i := 0; i < 3; i++ {
		go func() {
			atomic.AddUint64(&v, 1)
			wg.Done()
		}()
	}

	wg.Wait()
	fmt.Println(v)
}

func listing3() {
	wg := sync.WaitGroup{}
	var v uint64

	for i := 0; i < 3; i++ {
		wg.Add(1)
		go func() {
			atomic.AddUint64(&v, 1)
			wg.Done()
		}()
	}

	wg.Wait()
	fmt.Println(v)
}

```

## sync.Cond の存在をわすれている

### listing1 の問題点として、各リスナーゴルーチンが寄付目標達成まで検査ループし続けて、そのため多くの CPU サイクルを浪費し、CPU 使用率をとても高くしている。

$10 goal reached
$15 goal reached

```go
func listing1() {
	type Donation struct {
		mu      sync.RWMutex
		balance int
	}
	donation := &Donation{}

	// リスナーゴルーチン
	f := func(goal int) {
		donation.mu.RLock()
		for donation.balance < goal { //goalに達したか検査する
			donation.mu.RUnlock()
			donation.mu.RLock()
		}
		fmt.Printf("$%d goal reached\n", donation.balance)
		donation.mu.RUnlock()
	}
	go f(10)
	go f(15)

	// 更新ゴルーチン
	go func() {
		for { // balanceを加算し続ける
			time.Sleep(time.Second)
			donation.mu.Lock()
			donation.balance++
			donation.mu.Unlock()
		}
	}()
}
```

### 各リスナーゴルーチンは共有されたチャネルから受信する。一方更新ゴルーチンは残高が更新されるごとにメッセージを送信する

だがこの解決策だと以下のようになることがある

チャネルに送信されたメッセージは、1 つのゴルーチンによってのみ受信されます。1 つ目のゴルーチンが 2 つ目のゴルーチンより先にチャネルから受信した場合、$10 メッセージは最初のゴルーチンでなく 2 つめのゴルーチンが受信

- $11 goal reached
- $15 goal reached

```go
func listing2() {
	type Donation struct {
		balance int
		ch      chan int
	}

	donation := &Donation{ch: make(chan int)}

	// Listener goroutines
	f := func(goal int) {
		for balance := range donation.ch {
			if balance >= goal {
				fmt.Printf("$%d goal reached\n", balance)
				return
			}
		}
	}
	go f(10)
	go f(15)

	// Updater goroutine
	for {
		time.Sleep(time.Second)
		donation.balance++
		donation.ch <- donation.balance
	}
}
```

解決策として、`sync.Cond`を使用する

リスナーゴルーチンでは寄付金の残高が達成されるまで待機状態をループしている。ループないでは条件が満たされるまで wait メソッドを使用している

実際に wait の実装は以下

1. ミューテックスをアンロックする
2. ゴルーチンを一時停止して通知をまつ
3. 通知が到着するとゴルーチンをロックする

この実装は残高が更新されることに基づいており、リスナーゴルーチンでの条件変数は新たな寄付が行われるごとに目が冷まし、寄付目的が達成されたか検査する。よって繰り返しの検査で CPU サイクルを消費するビジーループを防いでくれる。

`sync.Cond`を使う際の欠点として`Broadcast`を使用すると現在その条件で待っている全てのゴルーチンがおこされる。もし待っているゴルーチンがいなければ通知は失われる

複数のゴルーチンに通知を繰り返す送る場合`sync.Cond`が正

```go
func listing3() {
	type Donation struct {
		cond    *sync.Cond
		balance int
	}

	donation := &Donation{
		cond: sync.NewCond(&sync.Mutex{}),
	}

	// Listener goroutines
	f := func(goal int) {
		donation.cond.L.Lock()
		for donation.balance < goal {
			// ロックアンロック間で条件(balanceの更新)を待つ
			donation.cond.Wait()
		}
		fmt.Printf("%d$ goal reached\n", donation.balance)
		donation.cond.L.Unlock()
	}
	go f(10)
	go f(15)

	// Updater goroutine
	for {
		time.Sleep(time.Second)
		donation.cond.L.Lock()
		donation.balance++ // ロックアンロック間で条件(balanceの更新)をインクリメントする
		donation.cond.L.Unlock()
		donation.cond.Broadcast() //条件が成立した(balanceが更新された)ことをブロードキャストする
	}
}
```

## errgroup を使わない

複数のゴルーチンを生成してエラーを集約する方法を再実装するコードベースは一般的。

- sync サブリポジトリは便利なパッケージである errgroup を含んでいる
  - ゴルーチン呼び出し中にエラーが 1 つ発生した場合、そのエラーを返したい.
  - 複数のエラーが発生した場合は、そのうちの 1 つだけをかえすようにする

標準的な並行処理だけを使うと以下

```go
type (
	Circle struct{}
	Result struct{}
)
func handler1(ctx context.Context, circles []Circle) ([]Result, error) {
	results := make([]Result, len(circles))
	wg := sync.WaitGroup{} // 生成した全てのゴルーチンを待つためにウェイトグループを作成
	wg.Add(len(results))

	for i, circle := range circles {
		i := i
		circle := circle
		go func() {
			defer wg.Done()

			result, err := foo(ctx, circle)
			if err != nil {
			}
			// ?
			results[i] = result
		}()
	}

	wg.Wait()
	// ...
	return results, nil
}
func foo(context.Context, Circle) (Result, error) {
	return Result{}, nil
}
```

しかし以下にはまだ取り組んでいない重要な課題がある。もし foo がエラーを返したらどのように処理すべきか。代表的なのは以下。

- results スライスのように、ゴルーチン間で共有されるエラーのスライスを保持できる。各ゴルーチンはエラー発生次にこのスライスに書き込む。エラーが発生したか否かを判断するには親ゴルーチンでこのスライスを反復処理する。
- 共有されたミューテックスを介してゴルーチンからアクセスされるエラー変数を 1 つ保持できる
- エラーのチャネルを共有し、親ゴルーチンがそのエラーを受信して処理する

どの選択肢を選んでも複雑になるため errgroup パッケージが生まれた

```go
func handler2(ctx context.Context, circles []Circle) ([]Result, error) {
	results := make([]Result, len(circles))
	g, ctx := errgroup.WithContext(ctx) // 親コンテキストを渡して*errgroupを作成

	for i, circle := range circles {
		i := i
		circle := circle
		g.Go(func() error {
			result, err := foo(ctx, circle)
			if err != nil {
				return err
			}
			result[i] = result
			return nil
		})
	}
	if err := g.Wait(); err != nil {
		return nil, err
	}
	return results, nil
}
```

## sync パッケージの型をコピーする

- `Increment1`だとデータ競合が発生する
- sync パッケージの型はコピーすべきではない。
- 代替策として`Increment2`。レシーバー型に変更することで、Increment が呼ばれたときに Counter はコピーされない。したがって内部のミューテックスもコピーされない。

```go
func main(){
	go func() {
		counter.Increment1("foo")
	}()
}
type Counter struct {
	mu       sync.Mutex
	counters map[string]int
}
func (c Counter) Increment1(name string) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.counters[name]++
}
func (c *Counter) Increment2(name string) {
	// Same code
}
func NewCounter() Counter {
	return Counter{counters: map[string]int{}}
}
```

- それか値レシーバを残したい場合、Counter の mu フィールドの型をポインタに変更すること。
  - したがって mu はポインタなので、sync.Mutex 自身のコピーではなく、ポインタがコピーされる。

```go
type Counter2 struct {
	mu       *sync.Mutex
	counters map[string]int
}
func NewCounter2() Counter2 {
	return Counter2{
		mu:       &sync.Mutex{},
		counters: map[string]int{},
	}
}
```

よって sync パッケージを扱うとき以下を注意する

- 値レシーバでメソッドを呼び出す(上記で説明した通り)
- sync パッケージ型を引数として関数を呼び出す
- sync パッケージ型のフィールドを含む引数で関数を呼び出す
