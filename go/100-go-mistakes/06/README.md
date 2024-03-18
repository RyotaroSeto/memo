# 関数とメソッド

## どちらのレシーバー型を使うべきかわかっていない

### 値レシーバ
ポインタにしない、ただの値のコピー

### ポインタレシーバ
ポインタにする。メソッドの値の変更は呼び出し元も変更される(オブジェクトのアドレスをメソッドに渡しているため)

### レシーバがポインタでなければならない時
メソッドがレシーバを変更する必要がある場合

### レシーバがポインタであるべき時
レシーバが大きなオブジェクトである場合。ポインタを使用することで、大きなコピーが作成されないため、呼び出しを効率的に行える。

### レシーバが値でなければならない時
レシーバの不変性を強制する場合、レシーバがマップ、関数、チャネルの場合

### レシーバが値であるべき時
レシーバが変更する必要のないスライスの場合、レシーバが小さな配列や可変なフィールドを持たず必然的に値型であるtime.Timeのような構造体の場合、レシーバがint,float64,stringといった基本データの場合

レシーバが値であっても以下のような場合は値が変わる。このパターンがあるが明確に`customer`オブジェクト全体が可変であることを強調するには、ポインタレシーバを使う方がよい
```go
type customer struct {
	data *data
}
type data struct {
	balance float64
}
func (c customer) add(operation float64) {
	c.data.balance += operation
}
func main() {
	c := customer{data: &data{
		balance: 100,
	}}
	c.add(50.)
	fmt.Printf("balance: %.2f\n", c.data.balance) // 150
}
```

## 名前付き結果パラメータを使わない
インターフェイスの場合可読性が上がるため使うか検討する

## nilレシーバを返す
返す値がnilかnilポインタで`err != nil`がnilポインタの場合エラーの中にはいってしまうので注意

## deferの引数やレシーバの評価方法を無視している
以下のようにすれば`status`は空ではなくなる。またはポインタにする
```go
	var status string
    // defer notify(status)
    // incrementCounter(status) にしたら空になる。なぜなら評価がここで行われているから
	defer func() {
		notify(status)
		incrementCounter(status)
	}()
	if err := foo(); err != nil {
		status = StatusErrorFoo
		return err
	}
	if err := bar(); err != nil {
		status = StatusErrorBar
		return err
	}
	status = StatusSuccess
	return nil
```
