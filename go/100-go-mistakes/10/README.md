# 標準ライブラリ

## 誤った時間の長さを提供する

### time.NewTicker

以下のコードを実行するとティックが 1 秒ごとに配信されるのでなく、1 マイクロ秒ごとに配信される

`time.Duration`は`int64`型に基づいており、1000 は有効な int64 のため、NewTicker に 1000 ナノ秒。つまり、1 マイクロ秒の時間の長さを与えてしまっている。

```go
ticker := time.NewTicker(1000)
for {
    select{
        case <-ticker.C:
        // 何かの処理
    }
}
```

この場合は以下のように time.Duration の API を使うべき

```go
ticker := time.NewTicker(time.Microsecond)
// あるいは
ticker := time.NewTicker(1000 * time.Nanosecond)
```

## time.After とメモリリーク

- time.After
  - チャネルを返し、指定された時間が経過するのを待ってから、そのチャネルにメッセージを送信する関数
  - 通常、並行処理のコードで使用
  - 「5 秒間このチャネルでメッセージを受け取らなかったら・・を行う」といったシナリオを実装するために使える

しかし多くのコードで time.After の呼び出しをループで行なっていることが多く、メモリリークの根本原因になっている可能性がある

以下は 1 時間以上メッセージを受信しなかった場合、警告ログを記録する。メモリ使用量の問題につながる可能性ある

time.After はチャネルが返却され、time.After で作成された(チャネルを含む)資源は、タイムアウトが終了すると解放され、それまではメモリを使う。time.After の呼び出し 1 回につき約 200 バイトのメモリが使われる。1 時間に 500 万件といった大量メッセージを受信した場合 1GB のメモリを消費することになる

```go
func consumer1(ch <-chan Event) {
	for {
		select {
		case event := <-ch:
			handle(event)
		case <-time.After(time.Hour):
			log.Println("warning: no messages received")
		}
	}
}
```

上記のコードを解決する選択肢として time.After の代わりにコンテキストを使うこと

この方法の欠点はループの反復ごとにコンテキストを再作成しなければならないこと

```go
func consumer2(ch <-chan Event) {
	for {
		ctx, cancel := context.WithTimeout(context.Background(), time.Hour)
		select {
		case event := <-ch:
			cancel() // メッセージを受信したらコンテキストをキャンセルする
			handle(event)
		case <-ctx.Done(): // コンテキストをキャンセルする
			log.Println("warning: no messages received")
		}
	}
}
```

2 つ目の選択肢は time パッケージの time.NewTimer 関数を使うこと

Reset はピープルを新たに割り当てる必要がないため、処理が速く、ガベレージコレクタへの負担も少ない

注.このコード例は無限ループになっておるため、本番レベルのコードではキャンセル可能なコンテキストなどの終了条件を見つけるべき。タイマー作成直後に defer.timer.Stop()など

```go
func consumer3(ch <-chan Event) {
	timerDuration := 1 * time.Hour
	timer := time.NewTimer(timerDuration) // 新たなタイマーを作成する

	for {
		timer.Reset(timerDuration) // 時間の長さをリセットする
		select {
		case event := <-ch:
			handle(event)
		case <-timer.C: // タイマーの期限切れ
			log.Println("warning: no messages received")
		}
	}
}
```

### 一般的に time.After を使う際には慎重になるべき。timeAfter の呼び出しが繰り返される場合(例えば、ループ中、Kafka コンシューマ関数、HTTP ハンドラなどで)、メモリ消費量がピークに達することがある。この場合、time.NewTimer を使用するべき

## よくある JSON 処理の間違い

以下のコードを実行すると`"2023-09-18T10:53:22.966022+09:00"`になる`{"ID":1234,"Time":"2023-09-18T10:53:22.966268+09:00"}`を期待していたと思う

理由は`time.Time`パッケージの中で提供されているマーシャル動作が使われたから。これが、Event をマーシャルすると ID フィールドが無視される理由。

```go
type Event1 struct {
	ID int
	time.Time
}

func listing1() error {
	event := Event1{
		ID:   1234,
		Time: time.Now(),
	}

	b, err := json.Marshal(event)
	if err != nil {
		return err
	}

	fmt.Println(string(b))
	return nil
}
```

解決方法 1 として time.Time フィールドがうめこまれないように名前を追加する

```go
type Event2 struct {
	ID   int
	Time time.Time // time.Timeは埋め込み型ではなくなった
}

func listing2() error {
	event := Event2{
		ID:   1234,
		Time: time.Now(),
	}

	b, err := json.Marshal(event)
	if err != nil {
		return err
	}

	fmt.Println(string(b))
	return nil
}
```

解決方法 2 として、time.Time を埋め込んだままにしておきたい。あるいは埋め込む必要がある場合、Event 専用の json.Marshaler インターフェイスを実装する

この解決策は少し面倒であり、MarshalJSON メソッドが常に Event 構造体に対して最新であることを保証する必要はある

```go
type Event3 struct {
	ID int
	time.Time
}

func (e Event3) MarshalJSON() ([]byte, error) {
	return json.Marshal(
		struct {
			ID   int
			Time time.Time
		}{
			ID:   e.ID,
			Time: e.Time,
		},
	)
}

func listing3() error {
	event := Event3{
		ID:   1234,
		Time: time.Now(),
	}

	b, err := json.Marshal(event)
	if err != nil {
		return err
	}

	fmt.Println(string(b))
	return nil
}
```

### JSON とモノトニッククロック

time.Time 型を含む構造体をマーシャルまたはアンマーシャルする場合、予期せぬ比較エラーが発生する

以下のコードを実行すると`false`になる。m=+0.03454340 以外は同じ

Go では 2 つのクロックを 2 つの異なる API に分ける代わりに、time.Time にはウォール時間とモノトニック時間の両方が含まれる、time.Now()を使って、ローカルタイムを取得すると両方の時間を持つ time.Time が返される。逆に JSON をアンマーシャルすると time.Time フィールドにはモノトニック時間ではなく、ウォール時間だけ含まれる

```go
fmt.Println(event1.Time) // 2021-01-10 17:13:08.856861 +0100 CET m=+0.03454340
fmt.Println(event2.Time) // 2021-01-10 17:13:08.856861 +0100 CET
```

```go
type Event struct {
	Time time.Time
}
func listing1() error {
	t := time.Now()
	event1 := Event{
		Time: t,
	}

	b, err := json.Marshal(event1)
	if err != nil {
		return err
	}

	var event2 Event
	err = json.Unmarshal(b, &event2)
	if err != nil {
		return err
	}

	fmt.Println(event1 == event2) // falseになる
	return nil
}
```

解決策 1 として`==`の代わりに`Equal`メソッドを使う

```go
	fmt.Println(event1.Time.Equal(event2.Time))
```

しかしこの場合比較しているのは time.Time フィールドだけであり、親の Event 構造体ではない

2 つ目の方法として Truncate メソッドを使用してモノトニック時間を取り除く

```go
type Event struct {
	Time time.Time
}
func listing2() error {
	t := time.Now()
	event1 := Event{
		Time: t.Truncate(0),
	}

	b, err := json.Marshal(event1)
	if err != nil {
		return err
	}

	var event2 Event
	err = json.Unmarshal(b, &event2)
	if err != nil {
		return err
	}

	fmt.Println(event1 == event2)
	return nil
}
```

ここまで見てきたように、マーシャル処理とアンマーシャル処理は必ずしも対象ではないことを覚えておくこと

## よくある SQL の間違い

### sql.Open は必ずしもデータベースへのコネクションを確立しているわけではないので確認する際は Ping 関数を使う

### コネクションプールについて忘れている

コネクションプールを作成した場合、置き換えができる 4 つの利用可能な設定パラメータがあることを覚えておく

- SetMaxOpenConns
  - データベースへの最大オープンコネクション数
  - 本番レベルのアプリケーションでは重要。デフォルトが無制限のため、使われるデータベースが処理できる範囲に収まるよう設定する
  - この数が大きすぎると、Mysql に過剰な負荷がかかり、パフォーマンスが低下する可能性があるため、この数は適切に設定する必要がある
- SetMaxIdleConns
  - アイドル状態の最大コネクション数
  - アプリケーションがかなりの数の同時リクエストを生成する場合、この数の値を大きくする必要がある。そうしないと、アプリケーションで再接続が頻繁に発生するかもしれない
  - アイドルコネクションの数が少なすぎると、新しい接続を作成するために無駄な時間がかかり、パフォーマンスに悪影響を与える可能性がある
  - 一方、アイドル接続数が多すぎると、メモリの消費量が増え、サーバーのパフォーマンスが低下する可能性がある
  - 適切なアイドルコネクション数は負荷テストやベンチマークを実施して決定する必要がある
- SetConnMaxIdleTime
  - コネクションが終了するまでのアイドル状態の最大時間
  - アプリケーションがリクエストの爆発に直面するかもしれない場合に重要。
  - アプリケーションが平穏な状態に戻ったときに作成された接続が最終的に解放されるようにしたい
- SetConnMaxLifetime
  - コネクションをクローズする前にオープンしたままにできる最大時間
  - 負荷分散されたデータベースサーバーに接続する場合に役立つ。
  - アプリケーションがコネクションを長時間使わないようにしたいわけである

DB チューニング指標としては以下

- コネクション数を減らして、コネクションを貼る負荷を軽減したい
- コネクションが増えすぎて、DB 側に負荷がかかり過ぎるの抑止したい
- アイドルしたまま使われないコネクションは減らして、コネクションをアイドルしておく負荷を軽減したい
- コネクションのライフタイムを設定して適切に切断する事で負荷軽減および接続したままにしておく事で発生する問題を減らしたい。

### プリペアドステートメントを使わない

以下の点でプリペアドステートメントを使用した方が良い

- 効率性
  - ステートメントを再コンパイルする必要がない
- セキュリティ
  - SQL インジェクションのリスク軽減

```go
stmt, err := db.Prepare("SELECT * FROM ORDER WHERE ID = ?")
```

## デフォルトの HTTP クライアントとサーバーを使う

### HTTP クライアント

本番レベルのアプリケーションでは、デフォルトの実装に頼るのは良くない。

- デフォルトのクライアントはタイムアウトを指定していない。
  - 終了しないリクエストによってシステム資源の枯渇がもたらされるといった問題につながる

HTTP リクエストの 5 つのステップ

1. TCP コネクションを確立するためにダイアルする
2. TLS ハンドシェイクを行う
3. リクエスト送信する
4. レスポンスヘッダーを読み込む
5. レスポンスボディを読み込む

```go
client := &http.Client{}
resp, err := client.Get("https://golang.org")

resp, err := http.Get("https://golang.org")

	client := &http.Client{
		Timeout: 5 * time.Second, // 全体的なリクエストタイムアウト
		Transport: &http.Transport{
			DialContext: (&net.Dialer{
				Timeout: time.Second, // ダイアルのタイムアウト
			}).DialContext,
			TLSHandshakeTimeout:   time.Second, // TLSハンドシェイクのタイムアウト
			ResponseHeaderTimeout: time.Second, // レスポンスヘッダーのタイムアウト
		},
	}
```
