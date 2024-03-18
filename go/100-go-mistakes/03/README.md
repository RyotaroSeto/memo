# スライス

## 非効率なスライスの初期化
- `convert1`が圧倒的に遅い。`convert2`と`convert3`は若干`convert3`が早い。`convert2`と`convert3`はコードの可読性が高い方を使用すべき。
```go
func convert1(foos []Foo) []Bar {
	bars := make([]Bar, 0)

	for _, foo := range foos {
		bars = append(bars, fooToBar(foo))
	}
	return bars
}
func convert2(foos []Foo) []Bar {
	n := len(foos)
	bars := make([]Bar, 0, n)

	for _, foo := range foos {
		bars = append(bars, fooToBar(foo))
	}
	return bars
}
func convert3(foos []Foo) []Bar {
	n := len(foos)
	bars := make([]Bar, n)

	for i, foo := range foos {
		bars[i] = fooToBar(foo)
	}
	return bars
}
```

## nilスライスと空スライスに混乱する
`b := []string{}`がスライスを要素なしで初期化する場合、使用は避けるべき。代わりに`var b []string`を使う。`b := []string{}`は基本的に初期値を入れるときに使う。

## スライスが空か否かを適切に検査しない
スライスの空判定は`nil`ではなく`len()`を使用して長さ判定する。

## スライスのコピーを正しく行わない
```go
	src := []int{0, 1, 2}
	var dst []int
	copy(dst, src)
	fmt.Println(dst) // []

	src := []int{0, 1, 2}
	dst := make([]int, len(src))
	copy(dst, src)
	fmt.Println(dst) // [1, 2, 3]
```
上記の例ではsrcは長さが3のスライスだが、dstはゼロ値に初期化されているので、長さが0のスライス。よってcopy関数は小さい方の要素数分コピーするので、0になる。つまり、スライスは空。完全にコピーするためには、コピー先のスライスの長さがコピー元のスライスの長さ以上である必要がある。

## スライスのコピーを正しく行わない

# マップ

## 非効率なマップの初期化
マップもスライス同様、詰め込む大きさがわかっていれば`make`関数を使って初期サイズを指定する
```go
m := make(map[string]int, 1_000_000)
```
マップにはスライスのような容量はなく、長さのみ。

## マップとメモリリーク
ブラックフライデーなど数百万人がシステムに接続したとき、ブラックフライデーが終わっても、ピーク時と同じ数のバケットがマップに保持されたまま。この場合、メモリの消費量が大幅に減少せずに、メモリの消費が多くなる。マップが消費するメモリ量を減らすためにサービスを手作業で再起動したくない場合の解決策
- 1.現在のマップのコピーを定期的に再作成すること。
  - 1時間ごとに新たなマップを作成し、すべての要素をコピーして、前のマップを解放する
  - 欠点として、コピー前から次のガベレージコレクションまでの短期間のうち、現在の2倍のメモリを消費する可能性がある
- 2.map型が配列へのポインタを保持するように変更すること。つまり、map[int]*[128]byteとすること
  - かなりの数のバケットを保持することは解決しませんが、各バケットのエントリは128バイトではなく、値ポインタのサイズを確保することになる。(64ビットシステムでは8バイト、32ビットシステムでは4バイト)

| ステップ | map[int][128]byte | map[int]*[128]byte |
| ---- | ---- | ---- |
| 空マップを割り当てる | 0MB | 0MB |
| 100万個の要素を追加する | 461MB | 182MB |
| 全ての要素を取り除いてGCを実行 | 293MB | 38MB |

## 値の比較の誤り
スライス,map型あは==などの演算子で比較できない。構造体や配列、チャネル、インターフェイス、ポインタは可

- このように比較不可能な型は`reflect`パッケージで実行時リフレクションを使う
  - Goではreflect.DeepEqualで2つの値を再起的にたどることで、2つの要素が深く等しいか判断
  - reflect.DeepEqualの注意点
    - 空コレクションとnilコレクションを区別する(2つのアンマーシャル操作の結果を比較したい場合にその違いを区別したい)
    - 性能的に`==`の約100倍遅い性能。(これが本番コードではなく、テストコードの中だけでこの関数の使用を推奨している理由)なので比較したい場合は以下のように独自コードを作る
    - また`byte.Compare`関数を使って2つのbyteスライスを比較できるのでそれを使用する
      ```go
      func (a customer) equal(b customer) bool {
      	if a.id != b.id {
      		return false
      	}
      	if len(a.operations) != len(b.operations) {
      		return false
      	}
      	for i := 0; i < len(a.operations); i++ {
      		if a.operations[i] != b.operations[i] {
      			return false
      		}
      	}
      	return true
      }
      ```
