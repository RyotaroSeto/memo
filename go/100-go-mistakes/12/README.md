# 最適化

## CPU キャッシュを理解していない

一度特定のメモリにアクセスすると、近い将来**同じ一が再び参照される**、**近くのメモリ位置が参照される**

再び同じメモリブロックにアクセスすることをキャッシュヒットと呼ぶ。

下記の`sum2`と`sum8`では直感的には`sum8`が 4 倍速そうだが、`sum8`の方が 10%しか速くない。

- 理由はキャッシュラインに関係しており、`sum2`では　 4 回のアクセスのうち 3 回がキャッシュヒット。`sum8`は 1 回アクセスでキャッシュヒットなし。
- よって、この 2 つの関数の実行時間に大差はない。

```go
s := []int64{1,2,3,4,5,6,7,8}

func sum2(s []int64) int64 {
	var total int64
	for i := 0; i < len(s); i += 2 { //2要素ごと反復
		total += s[i]
	}
	return total
}
func sum8(s []int64) int64 {
	var total int64
	for i := 0; i < len(s); i += 8 { //8要素ごと反復
		total += s[i]
	}
	return total
}
```

### 構造体のスライスとスライスの構造体

- 以下の`sumFoo`,`sumBar`を比較すると`sumBar`の方が 20%速い
  - 理由は CPU がメモリからフェッチするキャッシュラインが少ないから。

![ClusterIP](./images/test.png)

```go
type Foo struct {
	a int64
	b int64
}
func sumFoo(foos []Foo) int64 {
	var total int64
	for i := 0; i < len(foos); i++ { // 各Fooを反復処理し、aフィールドを合計する
		total += foos[i].a
	}
	return total
}
type Bar struct {
	a []int64
	b []int64
}
func sumBar(bar Bar) int64 { // 単一の構造体を受け取る
	var total int64
	for i := 0; i < len(bar.a); i++ { // aの各要素を反復処理する
		total += bar.a[i] // totalに加算する
	}
	return total
}
```

### 予測可能性

- `sum2`の方が圧倒的に速く実行される(筆者のマシンでは約 70%)
- ストライド
  - ユニットストライド
    - アクセスしたい値が、全て連続的に割り当てられている。例えば int64 の要素スライス。このストライドは CPU にとって予測可能であり、要素を走査するのに必要なキャッシュラインの数が最小のため、最も効率的
  - 定数ストライド
    - CPU にとってまだ予測可能。例えば、2 つの要素ごとに反復するスライス。このストライドはデータ走査するために多くのキャッシュラインを必要とするので、ユニットストライドより効率的ではない
  - 非ユニットストライド
    - CPU がよそくできないストライド。例えば、リンクリストやポインタのスライスなど。CPU はデータが連続的に割り当てられているのか分からないので、キャッシュラインをフェッチしない。
- `sum2`は定数ストライド。`linkedList`は非ユニットストライド。
  - `linkedList`は私たちはデータが連続的に割り当てられていることがわかっているが、CPU はそのことを知らない。

```go
type node struct {
	value int64
	next  *node
}
func linkedList(n *node) int64 {
	var total int64
	for n != nil {
		total += n.value // 各ノードで反復処理する
		n = n.next // totalに加算する
	}
	return total
}
func sum2(s []int64) int64 {
	var total int64
	for i := 0; i < len(s); i += 2 { // 2要素ごとに反復処理する
		total += s[i]
	}
	return total
}
```

### キャッシュ配置ポリシー

## 偽共有につながる並行コードを書く

- 偽共有
  - キャッシュラインが複数のコアで共有され、少なくとも 1 つのゴルーチンが書き込みを行う場合、キャッシュライン全体が無効化される。これは更新が論理的に独立していても起こる
- `count1`と`count2`では`count2`の方が大幅に高速(筆者のマシンでは 40%)

  - 偽共有を防ぐために 2 つのフィールド間にパディングを追加したため。
  - パディングとは余分なメモリを割り当てる技法。int64 は 8 バイトの割り当てを必要とし、キャッシュラインは 64 バイト長なので、64-8=56 バイトのパディング

- 偽共有が発生するのは、少なくとも 1 つゴルーチンがライターであるときに、1 つのキャッシュラインが 2 つのコアで共有される場合。
- 並行処理に依存するアプリケーションを最適化する必要がある場合、偽共有がアプリケーションの性能を低下させることは知られているため、偽共有が適用されるか否か書くにすべき。

```go
type Input struct {
	a int64
	b int64
}

type Result1 struct {
	sumA int64
	sumB int64
}

func count1(inputs []Input) Result1 {
	wg := sync.WaitGroup{}
	wg.Add(2)

	result := Result1{}

	go func() {
		for i := 0; i < len(inputs); i++ {
			result.sumA += inputs[i].a
		}
		wg.Done()
	}()

	go func() {
		for i := 0; i < len(inputs); i++ {
			result.sumB += inputs[i].b
		}
		wg.Done()
	}()

	wg.Wait()
	return result
}

type Result2 struct {
	sumA int64
	_    [56]byte // パディング
	sumB int64
}

func count2(inputs []Input) Result2 {
	wg := sync.WaitGroup{}
	wg.Add(2)

	result := Result2{}

	go func() {
		for i := 0; i < len(inputs); i++ {
			result.sumA += inputs[i].a
		}
		wg.Done()
	}()

	go func() {
		for i := 0; i < len(inputs); i++ {
			result.sumB += inputs[i].b
		}
		wg.Done()
	}()

	wg.Wait()
	return result
}
```

## 命令レベルの並列処理を考慮しない

## データアライメントを意識していない

- Go の構造体のフィールドを大きい順に並び替えて再構成すると、パディングが抑制できる
- パディングが抑制することは、コンパクトな構造体を割り当てることであり、GC の頻度を減らしたり、空間的局所性を向上させる

## Go の診断ツールを使っていない

ベンチマークの実行次に`-cpuprofile`フラグを使って、CPU プロファイラを有効にできる

```Bash
$ go test -bench=. -cpuprofile profile.out
```
