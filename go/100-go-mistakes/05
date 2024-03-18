# 文字列

## ルーンの概念を理解していない

### 文字セットとエンコーディングの違い
- 文字セット
  - 文字の集合体。Unicodeの文字セットは2の21乗個の文字を含んでいる
- エンコーディング
  - 文字のリストを2進数で翻訳したもの。UTF-8は全てのUnicode文字を可変なバイト数(1バイトから4バイト)でエンコーディングできるエンコーディング規格。

- GoではルーンはUnicodeのコードポイント。
- Unicodeのコードポイントは21ビット。Goではコードポイントを保持するためのルーンはint32のエイリアス

### helloを例に取る
`s := "hello"`
- h,e,l,l,oの5つの文字からなる文字列。これらの単純な文字はそれぞれ1バイトでエンコードされている。そのため`len(s)`は５が返却される
- しかし、1文字は常に1バイトにエンコードされているわけではない。「漢」はUTF-8では、3バイトにエンコードされている。
- 逆にバイトスライスから文字列を作ることもできる。「漢」は`0xE6, 0xB1, 0x89`という3バイトでエンコードされている
- GOで文字列に対して`len`を使うと、ルーン数ではなくバイト数が返却される
```go
	s := "漢"
	fmt.Println(len(s)) // 3

	s = string([]byte{0xE6, 0xB1, 0x89})
	fmt.Printf("%s\n", s) // 漢
```

## 不正確な文字列の反復
- 文字列に対する反復処理はよく使う操作。
  - 文字列の各ルーンに対して操作を行う、特定の部分文字列を検索する独自の関数を実装など。
- `len(hello)`は5を返す
- `len(hêllo)`は6を返す
- これは`ê`が特殊文字列であり1バイトでエンコードできず、2バイト必要だから。

### 文字列のバイト数ではなく、ルーン数を取得したい場合どうしたら良いか？
- 結論エンコーディングに依存する
```go
s := "hêllo"
fmt.Println(utf8.RuneCountInString(s)) // 5
```

- 文字列をルーンのスライスに変換して、反復処理するとルーンを全て表示できる
  - 文字列をルーンのスライスに変換する場合、追加スライスを割り当ててバイト列をルーンに変換する必要があるためオーバーヘッドに注意しなければならない。したがって全てのルーンに反復処理を行いたい場合下記のルーンに変換しないパターンを使うべき。だがルーンのインデックスにはアクセスできないため、アクセスする場合は、ルーンに変換の方を使用する。
```go
s := "hêllo"
for i, r := range s {
	fmt.Printf("position %d: %c\n", i, r)
}
// position 0: h
// position 1: ê
// position 3: l
// position 4: l
// position 5: o
runes := []rune(s) // ルーンに変換
for i, r := range runes {
	fmt.Printf("position %d: %c\n", i, r)
}
// position 0: h
// position 1: ê
// position 2: l
// position 3: l
// position 4: o
```

### Trim関数の誤用
Go開発者はstringパッケージを使用するとき、TrimRightとTrimSuffixを混合しまいがち
- trimRightは与えられた集合に末尾ルーンが含まれていればそのルーンを削除する処理を**繰り返す**
- TrimはtrimRightとTrimの両方を適用する
```go
	fmt.Println(strings.TrimRight("123oxo", "xo")) // 123
	fmt.Println(strings.TrimSuffix("123oxo", "xo")) // 123o
	fmt.Println(strings.TrimLeft("oxo123", "ox")) // 123
	fmt.Println(strings.TrimPrefix("oxo123", "ox")) // o123
	fmt.Println(strings.Trim("oxo123oxo", "ox")) // 123
```

## 最適化されていない文字列の連結
以下は一見問題なさそうに見えるが、反復ごとにsは更新されず、新たな文字列がメモリから再割り当てされ、性能に大きな影響を与える
```go
func concat1(values []string) string {
	s := ""
	for _, value := range values {
		s += value
	}
	return s
}
```

stringパッケージBuilder構造体を使えばこの問題を解決できる。`WriteString`は2つ目の戻り値としてエラーを返すが、実際にはnil以外返す作りになっていないため`_`で問題ない。エラーを返す構造にしている理由として、`strings.Builder`は単一メソッドを含む`io.StringWriter`インターフェイスを実装しておりインターフェイスに準拠するため

```go
func concat2(values []string) string {
	sb := strings.Builder{}
	for _, value := range values {
		_, _ = sb.WriteString(value)
	}
	return sb.String()
}

func concat3(values []string) string {
	total := 0
	for i := 0; i < len(values); i++ {
		total += len(values[i])
	}
	fmt.Println(total)

	sb := strings.Builder{}
	sb.Grow(total)

	for _, value := range values {
		_, _ = sb.WriteString(value)
	}
	return sb.String()
}
```
- 一般的に5個以上の文字列を連結するときは`strings.Builder`による方法の方が性能的に速くなる。
- また最終的な文字列のバイト数が事前にわかっている場合、`Grow`メソッドで内部のバイトスライスを事前に割り当てることも忘れない。この方法の方が圧倒的に性能が良い。

## 無駄な文字列変換
文字列と[]byteのどちらかを使うか決める場合、ほとんどのプログラマーは利便性のため文字列を好む傾向があるがほとんどのI/Oは実際は[]byteで処理されるため、stringを使う場合余分な変換が必要。
- Goでは文字列は不変はなため以下はabcが実行される
```go
b := []byte{"a", "b", "c"}
s := string(b)
b[1] = "x"
fmt.Println(s) //abc
```
- 実際にstringパッケージで公開されている多くの関数の代わりに使えるようにバイトパッケージも同様な関数がある。したがってI/Oを行うかに関係なく、文字列の代わりにバイトを使って処理全体を実装するべき。
