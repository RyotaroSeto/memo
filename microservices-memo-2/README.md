## コンテナのメリット

- 可搬性が高い
    - コンテナイメージがあれば、クラウドやローカル環境など場所を問わず実行できる
- APIによる迅速なアプリケーション管理
    - 標準化された仕様(API)でアプリケーションをイメージ化し実行できるため、アプリケーションデプロイの自動化や継続的インテグレーションが実施できる
- 軽量なコンテナイメージ
    - アプリケーションの実行に必要最低限のライブラリしかイメージに含まないため、スピーディに展開できる
- コンテナの再利用
    - 一度作成したコンテナイメージは、異なる環境でも同様の実行結果を再現でき、共有も可能

## コンテナの技術要素

- Linux Kernelの仕組みを活用し、実行プロセスを隔離させ、あたかもアプリケーションが独自のOSで動いている環境を作り出す
    - 実行プロセスをグループ化して、分離された空間の中で実行する(namespace)
    - 実行プロセスが使用するCPU、メモリなどのコンピュートリソースを制限(cgroups)

## コンテナオーケストレーション(k8s)の役割

- 10万個などコンテナがあった場合、手動管理は不可能
- 運用者の負荷を減らし、コンテナを素早くサービスとして提供すること。
    - コンテナのスケジューリング
        - コンテナのあるべき状態を宣言することで、適切なリソース配置を行う
    - インフラの抽象化
        - 各クラウドリソースを抽象化
        - 特定のクラウドに依存することなく、自社のアプリケーションに適した開発ができる
    - コンテナのセルフヒーリング
        - 障害があっても、ユーザーが要求した状態を維持し続ける自動修復機能
        - ノード障害が起きたことを検知し、自発的にコンテナを安定したノード上に対比させる

## k8sの機能

- 仮想マシンや物理マシンをはじめ、複数のノードにわたるコンテナのプロビジョニング
- アプリケーションの実行に必要とされる、リソースの迅速な提供
- アプリケーションのデプロイとアップデートの動的制御
- アプリケーションのデータを管理するストレージのマウントや追加
- 宣言的オブジェクトの管理
- アプリケーションのヘルスチェックとセルフヒーリング

## マネージドサービス型k8s

- コントロールプレーンはクラウド側が管理する
- ユーザーはk8sクラスタのAPIに接続することで、リソースの操作を行える
- コントロールプレーンノードへの接続が管理されている分、k8sそのもののカスタマイズも制限される
- アベイラビリティゾーンなどを利用することでコントロールプレーンノードの可用性が動的に担保される
- 従来のパブリッククラウドのマネージドサービスと連携したサービス提供できる

## セルフサービス型k8s

- k8sをラッピングしたパッケージを、別途用意したインフラ上に展開して利用する形態
- k8s上でコンテナを開発運用するための支援ツールがある
- GUIを利用してk8sの運用管理やアプリケーション開発が行える
- どの環境でも同じ手順でアプリケーション開発や展開できる

## k8sの流れ

1. コントロールプレーン上のkube-apiserverでkubectlの指示を受け取り、オブジェクトをetcdに保存
2. kube-schedulerでノードの割り当てを計算。Podを最適なノードで起動するスケジューラ
3. kube-controller-managerでリソース状態を管理し、要求される各リソース状態を維持するコントローラーマネージャー
4. kubelet ワーカーノードにそれぞれ存在し、コンテナの起動や削除を管理するプロセス
5. kube-proxy Podの仮想IPアドレス(Service)を管理するプロセス
6. CoreDNS クラスタ内のPodの名前解決を行うDNS

## k8sのNamespace

- 機能単位で異なるNamespaceにオブジェクトを作成することで管理が容易
- オブジェクトへのアクセス権はNamespaceで設定できるため、開発チーム毎に異なるNamespaceを使う運用をすると良い

## kube-apiserverの主な機能

- k8sオブジェクトに対する操作を受け付けるRESTAPIサーバー
- kubectlでリクエストを受け取り、HttpHandle→Authentication→Authorization→Admission Control→etcdに保存する
- Authentication(認証)
    - アクセス元の認証が行われる。通信相手は誰なのか。
- Authorization(認可)
    - 接続元がだれであるか、そのユーザーがどのグループに所属しているかに基づいて、許可される操作を制御する仕組み
    - RBACの利用が推奨されている
        - 誰がどのリソース(pod,deployment,service,node)に対して、どのような操作(get,watch,create,delete)が実行可能かを定義
        - 「リソース」と「操作」を合わせて「Role」と取り扱う。その後アクセスしたいユーザーとRoleを「RoleBinding」にて関連付ける
- Admission Control
    - APIのリクエスト内容を確認し、リクエストの変更や制御を行う
    - Admission Controller(各フィルタ作業を行う)を呼ぶ。プラグイン型で様々な制御機能や更新機能がある
        - DefaultStorageClass
            - PVCの作成を監視して、デフォルトのストレージクラスを動的に追加
        - Service Account
            - k8s APIにアクセスするためのServiceAccountTokenをPodにマウント
        - Pod Security
            - 要求されたセキュリティコンテキストと利用可能なPodセキュリティポリシーに基づいてPodを制限する
        - ResourceQuota
            - Namespaceのリソース制限を超えていないか制御
        - LimitRange
            - NamespaceのPod単位のリソース制限を超えていないか制御
        - NamespaceLifecycle
            - 存在しないNamespaceや削除中のNamespaceへの指定を回避する
    - Podにサービスアカウントを制定されていなければ、自動的に「default」を割り当て
- APIのカスタマイズ
    - 「Admission Webhooks」を使用するとAdmission Controlをkube-apiserver上で行うのでなく、Webフックによってk8sAPI外部から実行できる。これにより、独自のAdmission Controllerを追加し、個別のAPIリクエストの内容に応じて制限をかけるなどカスタマイズ可能
- APIの操作履歴の保存
    - etcdに保存

## kube-schedulerの主な機能

- Podオブジェクトが新規で作成された時、それをどのワーカーノードに起動するか選択する
- **CPUやメモリなどリソースの空き状況などを考慮して最適なノードを選択するし情報を更新**
- ワーカーノードが指定されていないPodを常に監視している。指定されていないPodを見つけたら、Podに適したノードを選択し、PodオブジェクトのNodeNameフィールドを更新。最後に各ワーカーノードで稼働しているkubeletが、自分が動作しているノードで起動すべきPodを見つけ、そのノード上でPodを起動

## kube-controller-managerの主な機能

- k8sではDeploymentやReplicaSetといったオブジェクトごとにコントローラーが用意されている
- コントローラーとはetcdに保存されたあるべき状態を参照して、それに合致できるように実装の状態を変更するコンポーネント
- kube-controller-managerはこれらの各コントローラーを管理している

## kubeletの主な機能

- 全てのワーカーノードに1つずつ起動しているプロセス。
- etcdに保存されているオブジェクトから自身のノード上で実行されるべきPodを見つけ、実際に起動させる。
- pod内のコンテナが望ましい状態で稼働しているか確認して、それを維持する
    - コンテナのプロセスが停止した時検知して自動的に再起動
    - コンテナに対してヘルスチェックを行い、異常は発見された時の対処

## kube-proxyの主な機能

- 全てのワーカーノード上で稼働している
- Podに対するネットワークアクセスのルーティングを行う
- etcd内のServiceオブジェクトを参照し、その内容に従い、ノード上のネットワーク構成を変更する

## Service

- ClusterIP(デフォルト)
    - クラスタ内部からのみアクセスできるService
- NodePort
    - クラスタを構成する各ノードの特定ポート(NodePort)にServiceを公開する
    - ノードのIPアドレスとポート番号を指定することで、クラスタの外部からServiceにアクセスできる
- LoadBalancer
    - AWSなどクラウドプロバイダが提供するロードバランサーを自動的に作成しServiceを公開する。ロードバランサーにリクエストを送ることでクラスタ外部からServiceにアクセス可能
- ExternalName
    - クラスタ内のDNSに任意のホスト名のエイリアスを作成するService。対象ホストのホスト名をクラスタ外のシステムにすることで、クラスタ内のコンテナから見るとServiceにアクセスする感覚で外部システムに接続できる

## Ingress

- 特徴
    - ServiceがL4に対しL7
    - HTTPとHTTPSのエンドポイントを公開し、他のプロトコルは対応しない
    - ServiceのLoadBalancerタイプと異なり、1つのエンドポイントから複数のServiceに転送できる
- Ingressを利用して外部からリクエストを受け付けるには、以下が必要
    - トラフィックを受け付けてServiceに転送するロードバランサー
    - そのロードバランサーのプロビジョニングや設定を行うIngressコントローラー

## [Gateway API](https://kubernetes.io/docs/concepts/services-networking/gateway/)

- Ingressの後継
- Ingressには以下の課題がある
    - トラフィックの配送ルールとロードバランサーの設定が1つのマニフェストに詰め込まれており、前者はアプリ開発者、後者はクラスタ管理者の関心ごとだが1つのマニフェストにまとまってしまっている
    - 対応しているプロトコルがHTTPとHTTPSのみ
    - Ingressでは対応していないものが多い。標準外の機能は全てアノテーションで表現する必要がある

## ConfigMap

- コンテナから利用する設定情報(環境変数や設定ファイル)を管理
- ConfigMapとdeploymentは同じNamespaceに
- ConfigMapとして保存した設定値をコンテナの環境変数として利用するにはDeploymentでspec.containers[].env[]の配下にConfigMap情報を参照するための内容を記述する
- ConfigMapに保存した情報は、読み取り先頭volumeとしてコンテナにマウントしてアプリから利用する ※このやり方を行う場合deploymentに.envは指定しなくてよい
    - 以下2つの設定をDeploymentに記述する
        - spec.volumes[]
        - spec.containers[].volumeMounts[]

## Secret

- 基本的にConfigMapと同じだが、パスワードなどの機密情報を管理
- secretの値はbase64エンコードする
- SecretのデータはNode上の永続ディスクには保存されない
- kubectlコマンドの出力結果においてSercretの内容がマスクして表示される
    - ※kubectl get secret 名 -o yamlだと表示される
- Secretの種類
    - Opaque
        - 汎用的なタイプ
    - kubernetes.io/tls
        - TLS証明書
    - kubernetes.io/dockerconfigjson
        - コンテナレジストリ認証情報
    - kubernetes.io/service-account-token
        - サービスアカウントトークン

## アプリを k8s で運用する考慮点

- 設定情報を環境変数から読み込めるようにする
- ログを標準出力、標準エラー出力にする
    - omcat は`System.out.Println()`を使うと Java の標準出力ストリームを使って「./catalina-base/logs/catalina.out」というファイルに書き込まれる
    - k8s で大量のアプリがそれぞれのファイルにログ出力していると、回収や管理が大変手間。ログ分析も難しい
    - Tomcat を使用していて Java の標準出力ストリームからログを出力している場合、アプリケーションをコンテナとしてビルドする一工夫で標準出力、標準エラー出力をログの出力先にすることが可能
- アプリケーションをステートレスに実行する
    - インスタンス毎に異なる状態でないと、インスタンス追加、再起動などできない。

## Dockerfileのベストプラクティス

- ADDよりもCOPYを使う
    - COPYはローカルにあるファイルやディレクトリをコンテナ内にコピーに留まるのに対し、ADDはCOPYの動作に加え、コピー元がリモートのURLの場合はそれをダウンロードしたり、gzipなどの圧縮形式のファイルの場合には自動的に展開してコンテナに配置する
    - ADDはCOPYよりもエラーが発生しやすい。
    - ファイルのダウロードや解凍が行われていることが想像しにくいため予備知識を持たない人にとってはトラブルシュートが難しい
- レイヤーのキャッシュを意識する
    - コンテナイメージの特徴として、複数のレイヤーの積み重ねの構造を持っている。Dockerfileの一つ一つのインストラクションはそれぞれコンテナイメージの1レイヤーに対応しており、ビルドの実行プロセスの中でインストラクションが解釈され、処理が実行するたびにレイヤーが積み重ねられる。一度作られたレイヤーはビルド環境にキャッシュされる
    - キャッシュのコツ
        - コンテナへのファイルのコピーを複数のCOPYインストラクションに分けることにより、レイヤーキャッシュの効果が得られる。Dockerfileではインストラクション毎に対応するレイヤーが一つ作成される。COPYインストラクションで作成されたレイヤーは、ファイルの内容が変更されると次回のビルド時に再作成されるため、キャッシュが効かない。
        - 例えば`COPY . .`のように全てのファイルが1つのCOPYイントラクションでコピーしていると、それに含まれるファイルうちどれか1つ変更されると、次回のビルドで前ファイルのコピーが行われてしまう
        - レイヤーのキャッシュが一度無効になると、それ以降のインストラクションでは必ずレイヤーの再作成が行われてしまう
        - `RUN`インストラクションを使ってアプリケーションをビルドする際は、依存モジュールの`RUN`とアプリケーションビルドの`RUN`を分けて実行し、依存モジュールの方を先に書くことがおすすめ。なぜなら、アプリケーション開発の場面では、アプリケーションの依存モジュールよりもアプリケーション本体のコードを頻繁に変更するため
    - キャッシュによる弊害(意図せずキャッシュが使われないように)
        - ADDおよびCOPY
            - インストラクションの文字列が一致し、かつコピー元の内容に変更がない場合にキャッシュが利用される
        - ADDおよびCOPY以外
            - インストラクションの文字列が一致した場合にキャッシュが利用される
        - 下記の書き方には問題がある。
        - RUN apt-get updateで一度レイヤーが作成されると、それ以外のビルドでキャッシュされてしまい、apt-get updateの結果は最初に実行した際にキャッシュされたまま更新されない
        
        ```docker
        FROM ubuntu:22.04
        
        RUN apt-get update
        RUN apt-get install -y ncal
        ```
        
        - そのため、**apt-get updateとapt-get installを&&**で繋ぐか、ヒアドキュメントにまとめて1つのRUNインストラクションで実行する。更にapt-get installコマンドでパッケージのバージョンを明記するようにする(versionを明記することで新しいパッケージに更新したいときに確実に新しいレイヤーで作成されるため)
        
        ```docker
        FROM ubuntu:22.04
        
        RUN <<EOF
          RUN apt-get update
          RUN apt-get install -y ncal=12.1.7+nmu3ubuuntu2
        EOF
        ```
        
- ベースイメージを注意して選択する
- コンテナを軽量に保つ
    - 不要なパッケージやファイルが含まれないようにして、イメージの容量をできる限り小さく保つ。pull,起動、更新が早くなる。ストレージやネットワーク帯域の節約にもなる
    - 軽量にするには？
        - なるべき品質が高いベースイメージを選択する
        - マルチステージングビルドを活用する
        - 不要なファイルが残らないようにする
            - ADDでアーカイブファイルをダウンロードすると解凍まで自動的に行ってくれるが残った圧縮ファイルは削除されない。
            - そのためwgetやtarを組み合わせたワンライナーをRUNで実行する
        
        ```docker
        RUN wget https://github/download -O - \ # -O -でアーカイブをファイルシステムに保存するのでなく、標準出力で流す
            | tar xzf - \
            && mv hugo /usr/local/bin \
            && rm LICENSE README.md
        ```
        
- 非rootユーザーで実行する
    - groupadd,useraddコマンドでユーザーとグループを作成し、USERで実行ユーザーを指定

## k8sのみでのBlue/Green方法

Blue/Green環境に相当するDeploymentオブジェクトをそれぞれデプロイして起き、Serviceでのトラフィックの切り替えを行う。Serviceはラベルセレクタによってリクエストの配送先となるPodを決定する。その仕組みを利用して新旧Podに異なるラベルを設定し、サービスラベルセレクタを書き換えて配送先のバージョンを切り替えるapp=v1をapp=v2に切り替える。

新バージョンをリリース前にテストするには、動作確認用のトラフィックを新バージョンだけに送れるようにする。これには、テスト用のトラフィックのためのServiceを新たに作成し、新バージョンに配送されるように設定しておく。

## k8sのみでのカナリアリリース方法

旧バージョンのDeploymentと真バージョンでのDeploymentを少ないレプリカ数でデプロイしておき、Serviceから両バージョンのPodにルーティングする。徐々に新バージョンのレプリカ数を増やし、旧バージョンのレプリカ数を減らしていく。Serviceは1つだけだが、新旧両方のPodに同じラベルを付与した上でServiceのラベルセレクタを指定すれば、両バージョンのPodにリクエストが配送される。Serviceにはトラフィックの割合が指定できないため、新旧のPod数を調整することでこれを大替する

毎回レプリカ数を書き換える必要があるため、Argo Rolloutsを使う

## アーキテクチャの変更に伴う更新の場合のデプロイ戦略

- ローリングアップデートなどのデプロイ選択は、1つのサービスを安全に更新することに注目した手法であり、複数のコンポーネントの考慮が必要な更新はそのまま適用できない。
    - サービスの公開インターフェースの変更
    - データベースのスキーマ変更など依存が複数に対して考慮が必要なケース
- 更新手法
    - ストラングラーパターン
        - 既存のサービス呼び出しをHTTPプロキシ等の制御層でラップし、クライアントに対する互換性を制御層で担保しておく。その間に背後のサービスを段階的に回収する
        - 段階的にアプリケーションを移行しながらも、一時的に元に戻すロールバックを実現したり、場合によっては完全に移行を中止することも可能
        - このようにアプリケーションのデプロイとリリースを分離できる点はストラングラーパターンの重要なポイント
        - ストラングラーパターンの制御層には複雑な呼び分けやプロトコル変換が必要なため、L7ロードバランサーを使うことが求められる。nginxやenvoy
        - 公開されたインターフェイスを変更したり、分割して別サービスに切り出す場合
    - ストラングラーパターンのステップ
        - 新しいサービスをデプロイ
            - 移行する機能を新しいサービスに実装し、デプロイする。リクエストがルーティングされるまでリリースされないため、機能が完成する前の段階でデプロイしても問題ない。
        - プロキシを挿入する
            - 制御層となるHTTPプロキシを用意し、移行元のアプリケーションへのリクエストを全てこのプロキシ経由にする。この時点では、リクエストに対する特別な制御は行わず、そのままアプリケーションを通過させる。
        - 新しいサービスにリクエストをルーティングする
            - プロキシの設定を変更してリクエストを新しいサービスにルーティングする。ここで何か問題が発生したら、プロキシの設定を元に戻してロールバックする。
        - 移行元の機能を廃止する
    - 抽象化によるブランチ
        - アプリケーションコード内の機能呼び出しを**抽象クラスやインターフェイスに置き換え**、その実装として新しいサービスの呼び出しを行う
        - アプリケーションの内部実装として作られた機能をサービスとして抽象する
        - 変更の対象はアプリケーション内部に埋め込まれた機能モジュールであい、クライアント(機能の呼び出し元)はそのモジュールを呼び出しているアプリケーションコード。
        - 単一のコードベースに新旧の機能を共存させることのメリット
            - 単一のブランチ上での作業を強制する力が働くため、長命の別ブランチが生まれたり、それをマージする際に発生する問題を回避できる。
            - 単一のアプリケーションインスタンス内に変更前後の機能が組み込まれることで、本番環境上で新機能をテストできる。(このような制御は、フィーチャーフラグを組み込むなどの工夫が必要)
    - 抽象化によるブランチのステップ
        - 抽象を作成する
            - サービスとして切り出したいモジュールに対する抽象を作成し、既存コードは抽象の背後に隠蔽される形に修正する。javaの場合は新たにインターフェイスを定義すると共に、既存コードでそのインターフェイスを実装することを宣言する。抽象化によるブランチでは抽象の背後にある実装を切り替えることで移行やロールバックを行う。
        - 新しい実装を作る
            - 抽象を実装する形で変更後の機能を作成し、デプロイする。この時点では新しい実装はまだ有効になっていない
        - 実装を切り替える
        - 不要になった実装を削除する
    - UI合成
        - WebUIのレイヤーで、ページやページ内のパーツ毎にアクセス先のサービスを切り替える
        - UIのページやパーツ単位で、段階的に新しいサービスに移行する
    - 変更データキャプチャ
        - データの変更を検出する仕組みを導入し、それをトリガーに外部サービスの呼び出しをおこなう
        - データの更新に基づいて外部の機能を呼び出す。移行先の新しいサービスにデータを同期する
        - データストアの変更を検出して別のサービスを呼び出すという特徴から、既存のアプリケーションの実装を変更せずに新機能を追加できる。よって、アプリケーションが複雑化してしまい、そこに変更をくわえるには限界があるという時の助けになる。
        - データストアの変更を検出する方法は、トランザクションログを監視する方法が主流。一般的なデータベースでは、実行されたトランザクションをログに記録しており、これを利用する。この方法は、ファイルとして出力されたログにだけアクセスすればよく、データベースに対してトリガーを組み込むなどの変更を加える必要がない。
        - データの変更を検出したあとは、その情報をイベントとしてメッセージングキューに格納する。コンシューマが取り出してその内容に応じた追加機能を実行する。
        - データストアと新しいサービスの間にキューを挟むことによって、メッセージングキューを用いた非同期システムのメリットを享受できる。例えば、サービスの呼び出しを新たに追加したいという場合でも、コンシューマの追加や修正で対応できるため、データストア側に影響を与えない。また、データの変更が大量に検出された場合は、一時的にキューがイベントを保持することで、新しいサービスを過負荷から保護してくれる。
        - 変更データキャプチャはStrimziやDebeziumを使うことで楽できる
    - アプリケーションでの同期
        - データの書き込み処理を新旧の両データベースに行うように修正し、旧DBのデータを新DBに同期する。データ同期と整合性の確認をとった後、読み取りよりも新DBに向けることでDBを移行する
        - データベースのエンジンや、スキーマを変更する。移行先の新しいサービスにデータを同期する
    - アプリケーションでの同期のステップ
        - データを一括で同期する
            - 移行元のデータを新しいデータストアにバッチ処理などで一括インポートする。
        - 新旧データストアに書き込みを行う
            - 書き込み操作が新旧データストアに対して行われるようアプリを改修する
        - 新しいサービスをデプロイし、読み取り操作をルーティングする
            - 新サービスをデプロイし、読み取り系の機能だけ公開する。読み取り系の操作だけにしている理由として、操作種別毎に段階的にリリースすることで問題があった時の影響を最小限に抑えられる。またこのステップから、読み取り、書き込みの操作種別によって呼び出し先を切り替える制御層が必要。つまり、ストラングラーパターンのステップに沿ってプロキシの挿入など行う
        - 新しいサービスから書き込みをテストする
            - この時の書き込み操作では移行先のデータストアだけでなく移行元にも書き込めるようにし、新旧データの整合性を保つ。これは書き込み操作のリリース後に問題が発生した場合、旧バージョンに切り戻してもデータ不整合が起きないようにするため
        - 全ての操作を新しいサービスに切り替える
        - 移行元のデータストアへの書き込みを停止する
    - データベースラッパー
        - 特定のテーブルなどに対するアクセスをアプリケーションでラップし、データアクセスの互換性をラップ層で担保しながら背後のDBの分割を行う
        - アプリケーションの直接的なクエリ発行からデータベースを分割し、スキーマ変更やDBの分割を円滑に行えるようにする
     
## Envoyのトラフィック制御の仕組み

- Listener
    - クライアント(リクエスト元)とのやりとりを担う。クライアントからリクエストを受信し、それに対してレスポンス返却するまでのライフサイクルを管理する。Filter Chainという仕組みでリクエストに対する様々な処理を行う
- HTTP router filter
    - Listenerで動作するFilter Chainの一部で、リクエストの内容を判断して適切なClusterにリクエストを受け渡す役割。リクエストのパスや、HTTPメソッドからルーティング先を決めている
- Cluster
    - リクエストの転送先との通信を管理する構成要素。1つのClusterは複数のリクエスト先との通信を管理し、それらに対するロードバランサーなどを担う。

## 構造化ログのディメンションに付与する代表的なフィールド

- タイムスタンプ
- エラーコード
- メッセージ
- アプリケーションのインスタンスの識別子
- リクエストID
- リクエストを送信したユーザーのID
- リクエストのパス
- 操作種別（例：HTTPメソッド）
- 操作結果（例：HTTPレスポンスコード）

## Pod Start Lifecycle Hook

- メインコンテナが起動した時通常実行される処理とは別に、同じコンテナの中で追加の処理を実行する機能。
- ExecとHTTPの２種類のハンドラーを使用できる
    - Exec
        - コンテナのcgroupsと名前空間の中で、任意のコマンドやスクリプトを実行する。
        - メインコンテナにインストールされていないコマンドは使用できない。
        - Podのリソースはメインコンテナ+その他のため負荷の大きい処理を行う場合は割り当てに注意が必要。
    - HTTP
        - コンテナ上の特定のエンドポイントに対してHTTPリクエストを送信する。リクエスト送信元はkubelet
- 具体的なユースケースとして、Webアプリケーションが起動した時にそのアプリに対して暖機運転のためのトラフィックを送る
    - 暖機運転は本体のアプリケーションが起動してから、Init ContainerでなくPod Start Lifecycle Hookを使用する必要がある。

## ヘルスチェック

- 順序はStartup Probe → Liveness  Probe → Readiness Probeの順番
- Startup Probe は一度実行するとそれ以降は実行されない。
- Liveness  Probe/Readiness Probeは一度開始されたらコンテナが停止するまで止まらない。
- Startup Probe
    - アプリケーションの起動が完了しているかを判定するヘルスチェック。
    - 起動に長時間かかるアプリケーションで、Liveness  Probe/Readiness Probeの開始を遅らせておきたい時に利用する。
    - 失敗すると再起動される
- Liveness  Probe
    - アプリケーションが正常に動作しているか判定するヘルスチェック。
    - 失敗判定になるとコンテナが再起動される。
    - アプリケーションはプロセス自体が生存していても、デッドロックなどによって本来の機能を果たせなくなる場合があるため、自動で検出してコンテナを再起動する
- Readiness Probe
    - アプリケーションが通信トラフィックを受け付けられるか判定するヘルスチェック。
    - 成功判定となると、Serviceを経由したトラフィックがPodに届くようになり、失敗判定となると届かなくなる。
- ヘルスチェックの方法は以下４種類ある
    - Exec
        - 指定されたコマンドやスクリプトをコンテナ内に実行
    - TCPSocket
        - コンテナ内の特定のポートに対してTCPソケットを開く。コネクションが確立できた場合、成功
    - HTTPGet
    - GRPC

## kube-proxy

- kubelet同様Podに一つある。kubeletはEndpointSlice(Serviceに対して配送先となっているPodが記録されるオブジェクト)の内容に従ってトラフィックを制御する

## Podの停止

- kubeletはPod内のコンテナが終了する時、Pre Stop Lifecycle Hookを実行する。（Podのyaml に定義した場合実行される。）Pre Stop Lifecycle Hookが終了するまで、コンテナで動いているアプリケーションプロセスに対して何も行われない。この性質を利用して、Pre Stop Lifecycle Hookで一定時間スリープするコマンドを実行し、コンテナの停止が始まるタイミングを遅らせる、という使い方ができる。これはPodがサービスアウトしてトラフィックが停止するまでの時間を確保することで、安全にアプリケーションを終了させるという目的がある。　k8s1.29から使える。これまでは、Exec ActionでSleepコマンドを行っていた。
- Pre Stop Lifecycle Hookが終了すると、kubeletはコンテナに対して、SIGTERMシグナルを送信し、終了となる。
- kube-controller-managerがEndpointSliceから該当のPodを除外する。それを受けて、kube-proxyがiptablesまたはIPVSの設定を変更し、トラフィックがPodに届かない状態になる。

## Podの安定稼働のためのプラクティス

- Webアプリケーションを安定的に動作させるには、Podの起動と停止を安全に行うことと、意図せずPodが再度起動される可能性を減らすことを目指す。
- 起動直後のアプリは、動作し始めて一定時間経過しているアプリに比べるとパフォーマンスが低い。利用として、キャッシュ、ヒープメモリが最大量まで確保されていない、JITコンパイルが十分に進んでいないなどがある。本来のパフォーマンスを発揮できないPodに対してトラフィックをかけるとトラブルが発生するかもしれない。
    - Podにトラフィックが行われるのは、Readiness Probeが成功となり、Serviceがサービスインされた時。つまりこの段階でアプリが本来の性能を発揮できるよう準備する必要がある。
    - Readiness Probeが開始される前の段階でアプリの準備のために使用できる機能は、Init ContainerとPost Start Lifecycle Hookがある。これをうまく利用する。
        - メインコンテナが起動していないとできない動作はPost Start Lifecycle Hookが行う。例えば、暖機運転は起動済みのアプリケーションに一定の負荷をかける行為が必要なためPost Start Lifecycle Hookでおこなう。
        - メインコンテナ内でインストールしておくパッケージはInit Containerを利用すべきl。
    - 暖機運転はExecハンドラーを利用して、メインコンテナ内のアプリに対してcurlコマンドでリクエスト送信する。Post Start Lifecycle Hookはメインコンテナの起動と共に実行されるが、その時点でアプリケーション本体の起動が完了しているかはわからない。そこでアプリケーションの適当なエンドポイントに定期亭にリクエストを送り、正常な応答が帰るのをまってから、暖機運転リクエストを送信する。 ※暖機運転により起動までに時間はかかるようになる。並列に暖機運転リクエストを送信するなど工夫して起動までの時間を縮められる
    
    ```yaml
    until curl http://localhost:8080/hello; do
      sleep 1
    done
    for i int $(seq 10000); do
      curl http://localhost:8080/hello
    done
    ```
    
    - Podを停止する際はPre Stop Lifecycle Hook でSleepコマンドを実行する。（Podはアプリケーションに対して問答無用にSIGTERMを送るから）またアプリ内ではGraceful Shutdownを実装しておく。
    - Graceful Shutdownに時間がかかりすぎると、レスポンスを返し終えてないにも関わらず、SIGKILLシグナルで強制停止することがある。そのため、「spec.terminationGraacePeriodSecond」フィールドでSIGKILLまでの時間を伸ばすことを検討する

## Liveness Probeの設定

Liveness Probeを正しく理解するためにはPodの「spec.restartPolicy」 の違いを知っておくべき。常に再起動する「Always(デフォルト)」、異常終了の時だけ再起動する「OnFailure」、再起動しない「Never」。つまり、コンテナが異常終了した場合の再起動はLiveness Probeの役割ではない。Liveness Probeは「プロセスは正常動作しているが、アプリケーションとしての機能を果たしていない状況」を復旧するために利用する。

具体的な例として、デッドロックによってアプリの動作が停止している状況。デッドロックは再起動によって復旧可能であるため、まさにLiveness Probeが適している。

逆にLiveness Probeの設定例として適さないのは、例えば、文字列のレスポンスを返すだけのエンドポイントを呼び出す設定することなど。

## Podのresource管理とチューニング

- request
    - そのコンテナのために確保されるリソース量。
- limit
    - requestsによる確保量を上回ってリソースを消費する場合の上限値。ノードのリソースに空きがあれば、最大でlimitsに指定したリソースを使用できる。
- メモリの使用量がlimitに収まるようにして、その上で負荷試験を行ってメモリ消費の傾向を確認しながらパラメータ調整を行う。あとはGoのプラクティスを調べる。

## その他Podが停止する場合と対策

API-initiated Eviction(k8sバージョンアップ時)とプリエンプション時にpodは停止される。

この対策にPodDisruptionBudgetという機能で対策できる。

稼働中のPodを再スケジュールする時、同時に停止できる数に制約を設ける機能。これを行うことで、全体でPodを停止せずに全てPod移行できる。