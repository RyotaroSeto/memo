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
