# helm

## docker composeのデメリット

- コンテナ管理を自分で行う必要がある、プロセスが止まったら再び自分で復旧しなければならない
- 複数のコンテナが一つのホストで立ち上がるため、システムリソースが競合することがある
- 一つのホストで複数のコンテナが立ち上がるため、ポートがバインディングをしないように設計する必要がある

## kubernetesのメリット

- Self Healing(自己回復)
- Immutable Infrastructure(不変なサーバー基盤)
- AutoScaling(自動的な規模の調整)

## kubernetesのデメリット

- 複数のリソースを組み合わせて扱うため、教育コストがある
- yaml形式のファイルで環境定義を記載するため、一つのアプリを定義するために大量のyamlを書く
- 変化が非常に早いため適用が大変

## helmのメリット

- 複雑なyamlを意識せず、主要なアプリをコマンド一つでkubernetes上に展開可能
- パラメータを変えることで、オンプレやクラウドなど環境を合わせられたり、永続化を利用の有無を選択できる
- Chartと呼ばれるフォーマットからアプリのyamlを取得できる
- Chartを自作することで、自作アプリのデプロイもコマンド1つで可能

## [helmのチャートリポジトリ](https://github.com/helm/charts)

- helmチャートはGitHubのプライベートリポジトリにもChartを配置することができる

helm install wordpress-sample bitnami/wordpress

**`helm pull bitnami/wordpress`**

## helm Chartの仕組み

- values.yamlのimageタグの中を変えることでk8sのマニフェストの挙動を変えることが可能
    - values.yamlに記載されていないものを変更したい場合、Chartを自分で編集する必要あり
- values.yamlに書いた変数がtemlates以下のファイルの変数になる

## [k8s PlayGround](https://labs.play-with-k8s.com/)

## [helmのインストール手順](https://helm.sh/docs/intro/install/)

```bash
$ curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3
$ chmod 700 get_helm.sh
$ ./get_helm.sh

# helmを利用するためにTillerサーバーが必要なためTillerを動作させるServiceAccountに権限付与
$ helm init --service-account {serviceAccount名}
$ cat <<EOF>> rbac-config.yaml
apiVersion: v1
kind:ServiceAccount
metadata:
name:tiller
namespace:kubesystem
---
apiVersion:rbac.authorization.k8s.io/v1
kind:ClusterRoleBinding
metadata:
name:tiller
roleRef:
apiGroup:rbac.authorization.k8s.io
kind:ClusterRole
name:clusteradmin
subjects:
 - kind:ServiceAccount
name:tiller
namespace:kubesystem
EOF
# ServiceAccountの作成とRoleBindingを実施
$ kubectl create -f rbac-config.yaml

# helm initコマンドでTiller Podをkube-systemの名前空間のデプロイ
$ helm init --service-account tiller

# kube-systemの名前空間に、Tillerがデプロイされてることを確認
$ kubectl get pods -n kube-system | egrep "NAME|tiller"
```

## helm repo

- Chartリポジトリの追加や削除、更新、一覧表示する
- Incubatorリポジトリは、Helmチャートが本番環境で十分にテストされていない場所。

## helm search

- Chartを検索するコマンド
- 既にChartが公開されていないか手軽に調べるのに使う
- Chartを調べる他の方法として、artifacthubのUIで検索する方法もある

## helm fetch

- Chartをリポジトリからダウンロードするコマンド。

```bash
$ helm fetch stable/mysql
$ ls
mysql-0.1.0.tgz
```

## helm create

- 指定したChartテンプレートを生成する
    - 指定の名前でhelmの雛形が作れる

## helm package

- Chartをアーカイブする

```bash
$ helm package sample/
$ ls
sample-0.1.0.tgz
```

## helm lint

- Chartを構文チェックするコマンド
- 推奨構成についても指摘する

## helm template

- templates配下のyamlファイルを表示する
- templates配下のyamlは変数定義されているため、単体で利用不可
    - values.yamlに記載された値をtemplates配下の yamlに代入し、yamlファイル形式の標準出力を得られる
- helm fetchと組み合わせることで外部公開されているChartのyamlを取得できる

## helm install

- Chartをクラスターにインストールするコマンド
- ReleaseのステータスやインストールされたKubernetesのリソース、NOTESが表示される
- NOTESにはソフトウェアにアクセスするための方法やパスワード情報の取得方法などが記載されている
- `-n`を指定することで、クラスター上の名前空間にインストールするか選択できる

## helm list

- Releaseのリストを表示するコマンド

## helm status

- Releaseのステータスを確認するコマンド

## helm delete

- Releaseをクラスターから削除するコマンド

## helm upgrade

- Releaseを更新するコマンド

## helm plugin

- 標準のコマンドだけでは更新前と更新後の差分をきれないため、helm-diffをインストールするコマンド

## 現在のcontext確認

- `kubectl config get-contexts`

## helm Chart操作

- values.yamlの値を変更するには2つの方法がある
    - --setフラグを使ってコマンドラインで変更する
    - -fまたは—valuesフラグを使ってファイル指定で変更する
        - helm install --values values.yaml bitnami/mysql --generate-name
        - helm install test bitnami/mysql --values values.yaml
        
        - dev環境用の設定値でinstall
        - helm install -f values.yaml -f dev.yaml myChart/
        <img width="480" alt="helm" src="https://github.com/RyotaroSeto/microservices-memo/assets/70475997/8b73de5a-60ee-4250-82db-2f52d9783e06">

```bash
$ helm create mychart # 雛形作成
$ helm install mychart ./mychart # インストール
$ helm install mychart ./mychart --debug --dry-run # デバッグ
```
- yaml ファイルの中の「name」部分をハードコーディングするのは良くない
- 「name」は Release ごとにユニークであるべき。

## 組み込み変数一覧
<img width="540" alt="helmchart" src="https://github.com/RyotaroSeto/microservices-memo/assets/70475997/cdaef48d-fc1c-4ba2-9baa-2e3ad0c772bf">

- 利用頻度が多いのは「Release」,「Chart」,「Values」
- 以下のようにquote functionをつけると出力される文字列がダブルクォーテーションになる
    - つけないとダブルクォーテーションなし
    - helmには60を超えるfunctionがある
    - 「upper」で大文字になる
    - 「default」を使うことでデフォルト値を設定できる
        - 「default」は繰り返し使用すべきでない。ただし、values.yamlで宣言できない変数に使うには最適
    - if / elseによる条件分岐
        - 例えばPersistentVolumeを使用する場合、PVを宣言し、使わない場合emptyDirを使うなどの条件分岐ができる
        - helmにはホワイトスペース(空白)を扱うツールがある。「{{-」や「-}}」のように二重波括弧にダッシュをつけることでホワイトスペースを扱える。
            - 「{{-」で左側の改行文字を取り除く
            - 「-}}」で右側の改行文字を取り除く
    - withによる範囲指定
        - 変数のスコープを変更できる。
        - withを使ったことで「.drink」を呼び出す階層が浅くなる
        - 「title | quote」でTitle Case(先頭を大文字)にし、ダブルコーテーションがつく
        - また「tuple」functionを使って簡単に反復処理ができる
    - rangeによるループ
    - defineによる変数定義
    - templateで定義した変数をインポート
    - includeによる変数読み込み
    - 変数をtemplate内で代入する仕組み
        - 「$変数名」と「:=」を利用することでtemplate内で変数を代入できる
            - rangeと組み合わせると便利「$index」は予約後で0から始まりループを繰り返すたびにひとつずつインクリメントされる。rangeを使ってkey,valueが取得できる

```yaml
metadata:
  name: {{ .Release.Name }}-configmap
data:
  myvalue: 'Hello World'
  drink: {{ .Values.favorite.drink }} # coffee
  food: {{ quote .Values.favorite.food }} "pizza"
  food2: {{ .Values.favorite.animal | quote }} # "cat"
  animal: {{ .Values.favorite.animal | upper | quote }} # "CAT"
  animal2: {{ .Values.favorite.animalc | default "dog" | quote }} # "dog"
  {{ if eq .Values.favorite.drink "coffee" }}mug: true{{ end }} # mug: true
  {{- if eq .Values.favorite.drink "coffee" }}
  mug: true # mug: true
  {{- end }}
  {{- with .Values.favorite }}
  drink: {{ .drink | default "tea" | quote }}
  food: {{ .food | upper | quote }}
  {{- end }}
  toppings: |-
    {{- range .Values.pizzaToppings }}
    - {{ . | title | quote }}
    {{- end }}
  sizes: |-
    {{- range tuple "small" "medium" "large" }}
    - {{ . }}
    {{- end }}
  {{- $relname := .Release.Name -}}
  {{- with .Values.favorite }}
  release: {{ $relname }} # mychart
  {{- end }}
```

- Named Template
    - template Nameがグローバルスコープである
    - もし同じ名前のふたつのtemplateを宣言した場合、あと勝ちで最後に読み込まれた方で上書きされる
    - サブChart内のtemplateも親のChartと一緒にコンパイルされるため、templateにはChart独自の名前をつけて重複しないようにする
