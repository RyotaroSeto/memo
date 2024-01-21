## Docker

- dockerimageは**[Trusted Conten](https://hub.docker.com/search?q=)t**マークが付いているものを使用する
- trvyの[githubactions](https://github.com/aquasecurity/trivy-action)
- dockerfileのlintツール[hadolint](https://github.com/hadolint/hadolint?tab=readme-ov-file)  ciに組めばプルリク作成前にチェックできる [webページもある](https://hadolint.github.io/hadolint/)
- dockerの脆弱性診断ツール[dockle](https://github.com/goodwithtech/dockle)
- nodeのdockerfile作成のベストプラクティス
    - package.jsonのdevDependenciesは開発時のみのため本番のコンテナイメージには含めない
    - pnpm-lock.yamlはインストール時変更しないためフラグをつける必要がある
        - pnpm install —prod —frozen-lockfile
    - アプリケーションコードのみが変更された場合、パッケージの再インストールをさせない。
        - Lockファイルを先にコピーしパッケージの再インストールをしないことで、コンテナイメージ内のファイル変更量を抑えキャッシュを使えるようにし、ビルド時間短縮
        
        ```docker
        COPY .npmrc package.json pnpm-lock.yaml .pnpmfile.cjs ./
        pnpm install —prod —frozen-lockfile
        ```
        
    - イメージの作成やコンテナの起動に関係あいパッケージのインストールの際に生成されるlogファイルやnode_modulesはコピーする必要がないため、.dockerignoreしておく
- node.jsならprocess.envで環境変数を取得できるが、クライアント側で実行しているJavascriptからこれをそのまま参照できない。そのため、APP_ENVという環境変数を設定し、getInitalPropsを使って値をクライアント側に渡す必要がある。その上でk8sにenv name:APP_ENVのように指定して、クライアントに環境変数を渡す

## PodDisruptionBudgetによる安全なメンテナンス

- k8sを運用する中で、Podが稼働しているノードをメンテナンスしたい場合。その際はkubectl drainを用いて、ノード上のPodを退避させる必要がある。例えばノードが3台あって、レプリカ数2のアプリの場合で、1つのノードに pod2つ配置されている状況でその1つのノードをdrainしたらアプリが停止してしまう。
- 上記の状況を避けるために、`PodDisruptionBudget` を定義することで最低限必要なPod数を1と設定した場合、上記の状況でpod1つはdrainで退避されなくなる。

## PriorityClassによる起動優先順位の定義

- `PriorityClass`を設定することで、Podの起動優先度順位を定義できる。
- 優先度は`kubectl get priorityclass`、`kubectl describe pod`で確認できる
- Kubernetesのクラスター内で重要なコンポーネント(kube-apiserverやkube-controller-manager)がリソース不足時に追い出されないようにするために使う。

## 各種Probeによるヘルスチェックの実現

liveness-readiness-startup-probesについては[こちら](https://kubernetes.io/ja/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/)

## preStopによる安全な停止タイミング

- podが何かの理由で停止される場合、pod内のアプリにSIGTERMが送信される。その後、アプリが終了しなかったら、30秒待ってSIGKILLが送信される。この処理は非同期で行われるため、Service配下から外れていないにも関わらず、SIGTERMによってPodが停止される可能性がある。
- preStopで設定した処理はPod内のアプリにSIGTERMが送られる前に実行される。preStopでsleepする設定を入れておくことで、Podを通常通り、稼働させたままServiceの対象から外れるまで待たせられる
- ****コンテナライフサイクルフックについては[こちら](https://kubernetes.io/ja/docs/concepts/containers/container-lifecycle-hooks/)**

## GracePeriodによる停止期間の設定

- SIGATERMが送られて30秒待った場合SIGKILLが送信されるが、この30秒は変更できる。
- `.spec.terminationGracePeriodSeconds`で設定できる
- 終了するまで30秒以上時間を置きたい場合、prestopでsleepさせてもいいが、`terminationGracePeriodSeconds`を超えた場合はpreStopの処理が終わり、`terminationGracePeriodSeconds`が強制的に2に設定され、結果として2秒でSIGKILLされる。これを避けるために`terminationGracePeriodSeconds`をデフォルトの30秒よりも大きな値にしておく

## 分散スケジューリングの実現

- 特定のnodeでPodを起動させたい場合、nodeSelectorまたはより柔軟な設定が可能なnodeAffinityを使う
- podAntiAffinityを使うと、Podどうしが偏らずに分散して異なるnodeに配置できる。
- 特定のPod同士を同じnodeに配置したい場合はpodAffinityを使う
- クラスターが複数のゾーンで構成されている場合、ゾーンごとに均等にPodが配置されていることが望ましい。topologySpreadConstraintsを設定すればPodの配置の偏りを少なくできる。

## k8sのバッチ処理

- JobやCronJobがあるが、これらは並列処理や依存関係を考慮した処理はできず、より柔軟な処理を行うためにはArgo WorkflowsやVolcanoなどを使う
- Jobはワンショットなスクリプトやツールをコンテナとして起動するのに適している。
    - 失敗した時のリスタート条件や回数など指定できる
- jobの自動削除
    - jobから作成されたPodを自動削除できる。
    - 通常Jobによって作成されたPodは失敗するか成功するまで動き続けるが、いつ終了するか不明なJobを実行する際、意図せず長時間計算リソースを占領するかもしれない。このような自体を避けるために自動削除がある
- job自身の自動削除
    - 終了されたjobは自動で削除されることなく、終了済Podとともに残り続ける。
    - 終了済のJobとPodが残り続けることでログやステータスを確認し続けることが可能だが、放置し続けると、kube-apiserverの負荷が上昇し、クラスタ全体のパフォーマンス劣化する可能性あり。`ttlSecondsAfterFinished`を設定すればjob終了後の生存時間を設定できる
- Podの失敗条件設定
    - Jobは失敗したPodをbackofflimitで設定した値に到達するまで再作成するが、podが終了した原因によってはpodを再作成したくない場合もある
    - どのような状態を失敗とみなすか重要で、コンテナの終了コード(Exit Code)や、ノードがdrainされたことによるPodのevictionなどdistruptionにも続いた失敗条件の設定できる
    - FailJob: backoffLimitの値に関わらずJobを失敗とする
    - Ignore: 失敗したPodの数がbackoffLimitに到達したかどうかを判定する際に、失敗とみなさない
    - Count: podFailurePolicyを設定しない場合と同様の挙動を取る
    - より細かいpodの失敗条件制御
        - PreemptionByKubeScheduler: より高い優先度が設定されたPriorityClassを持つPodがデプロイされた際に、kube-schedulerによってPodがpreemptionされた場合
        - DeletionByTaintManager: Podが実行中のNodeにTaintsが追加され、Podが合致するTolerationsを持っていない際に、Taint ManagerにPodが削除された場合
        - EvictionByEvictionAPI: Nodeがdrainされた際、kube-apiserverにpodがevictionされた場合
        - DeletionByPodGC: すでに存在しないNodeにPodが紐づいている際に、Podがgarbage collectionによって削除された場合
        - TerminationByKubelet: Nodeの計算リソースが圧迫されている際などにkubeletによってPodがevictionされた場合
- cronJob
    - cronJobに指定した時刻はkube-controller-managerのタイムゾーンに依存する。デフォルトはUTCのため工夫する必要がある。k8sv1.25からspec.timezoneでタイムゾーン指定できる
    - 並列実行の挙動をspec.concurrencyPolicyで制限できる
        - Allow: 前のJobと並列に作成されることを許可する
        - Forbid: 前のJobが実行されていた場合、そのJobが成功か失敗するまで次のJobはスケジューリングしない
        - Replace: 前のJobが実行されていた場合、そのJobを中断して次のJobを作成する
    - jobsHistoryLimitでJobの履歴がどのように保存するか制御できる

## Argo Workflowsによる柔軟なバッチ処理実現

- Jobの成果物を別のJobが利用するような依存関係、並列実行しているタスクのうち、一部にだけ依存関係を持たせるような高度なJobの並列実行を行いたい場合、Argo Workflowsで実現可能
- 複数のステップから構成されるバッチ処理を一連のタスクとして定義でき、有向非巡回グラフを用いてタスク間の依存関係を定義できる
- 複数のソフトウェアを組み合わせることなく、CI/CDパイプラインを構築できる
- Workflowsのリソースを作成して使用可能
- Wrokflow Temlateリソースでさまざまなjobのステップを作成できる
    - HTTP Template
        - HTTPリクエストに対する入力と出力の操作をテンプレートで定義
    - Container Set Template
        - 1つのPodに複数コンテナを内包し、なおかつそのコンテナ間に依存関係を定義できる。コンテナ間でemplyDirのような一時的な領域と共有可能
    - Data Sourcing & Transformations Template
        - データの整形処理を定義できる。データの入出力先にはHTTP、Git、S3など指定可能
    - Inline Template
        - WorkflowTemplateを入れ子で定義可能
- action後のpodの作成、削除、更新など可能
- argsで文字列や数値、ファイルなども外から受け取れる
- deamonでDBやHTTPサーバーなどjob上でデーモンとして起動して他のjobで参照できる
- DAGで複数のタスクに依存する処理が定義可能
- whenで実行制御できる
- 実行のたびにWorkflowで使用するパラメータを変更したい場合。例えばリリースに対してWorkflowを実行する時、そのリリースのタグなどをパラメータとして使用するなど
    - `argo submit ./workflow.yaml —parameter os=windows`用に渡せる
- バッチ処理の完了のステータスをメールやスラックなどで通知したいケース
    - exit handlerを利用することで可能
- workflowはworkflowATemplateにより切り出して参照可能**riorityClassによる起動優先順位の定義**

## データベースのコンテナ化

- StatefulSetを使う。またPodDisruptionBudgetを使用してdrain処理時もpodが一定動作している状態を保つ必要がある
- StatefulSet以外の方法としてOperatorを用いる方法がある。大規模な環境ではノード数やデータ容量が大きくなるため、operatorの利用を検討した方が良い(データのバックアップやリストア、データベース自体のアップグレードを自動で行えるため)
- mysql,postgres,redis,mongoDB,TiDB,YugabyteDBなどには[サードパーティのOperator](https://operatorhub.io/)がある
    - ※newSQLをkubernetesで構築するには選択肢は一つでOperatorを使用するにかない
- yugabyteは[これ](https://github.com/yugabyte/yugabyte-operator)か[これ](https://docs.yugabyte.com/preview/deploy/kubernetes/) [cockroachDB](https://github.com/cockroachdb/cockroach)もpostogres互換性 [citus](https://github.com/citusdata/citus)も
- StatefuleSetではPodにHeadless Serviceを用いた一意なホスト名が付与されるため、StatefuleSet経由で作成したPodへは$(StatefulSet名)-${インデックス番号}.${Service名}.${Namespace}.svc.cluster.localのような形式でアクセス可能。そのためコンテナ同士が強調して動作する必要があるユースケースに対応可能
- スケールインやスケールアウトする時、StatefuleSetではスケールアウトの際は作成したPodがReadyになるまで次のPodを作成せず、スケールインの際は削除中のpodが完全に削除されるまで次のPodの削除処理に着手しない
    - [Non Gracefule node shutdown](https://qiita.com/ysakashita/items/1a7fdf1688819a0a4580)を使うとノードに割り当てられているPodを強制削除できる。(Statefulesetは処理中にReadyになるか完全に削除されるまで次の処理が始まらないため、ノード障害が発生した際に新しいPodが起動できなくなる場合がある)
- 永久データ保管に使用されるボリュームでPV,PVC,StorageClassを介して操作できる
- エフェメラルボリュームは永続データ保管とは対照的に一時的なデータ置き場。
    - emptyDirなどはPodの削除と同時にボリュームが削除される
- Generic Ephemeral VolumeはPVC経由で作成したボリュームをエフェメラルボリュームとして使用する方法。
- ボリュームのバックアップ
    - CSI Driverがボリュームスナップショット機能に対応している場合VolumeSnapshotを作成することで、PVCに対するスナップショットを作成できる
- ボリュームのアクセスモード
    - ReadWriteOnce
        - 単一のノードからのみ読み書き可能
    - ReadOnlyMany
        - 読み取り専用ではあるが複数のノードから読み取り可能
    - ReadWriteMany
        - 複数ノードから読み書き可能
    - ReadWriteOncePod
        - 単一Podから読み書きのみ可能

## k8sにおける負荷分散

- Serviceの種類
    - ClusterIP
        - k8Sクラスタ内部で疎通可能なIPアドレスを割り振り、アプリに公開
    - NodePort
        - 各ノードのポート番号と	ClusrIPを紐づけてアプリを公開
    - ExternalName
        - 作成したServiceリソースと入力したDNSを紐づける
    - LoadBalancer(サービスへのトラフィックを分散するための一般的なリソース)
        - オンプレミス環境で一般的なのは、Ingress機能を提供するL7ロードバランサーがPod(LB pod)として起動し、このPodをService (type: LoadBalancer)として外部に公開する方法。Service type:LoadBalancerとして公開され、L4ロードバランサーによって処理された通信がNodePortやClusterIPによって、L7ロードバランサーPodに転送し、L7ロードバランサーPodがHTTP/HTTPSのリクエスト内容を認識し、トラフィックを宛先のServiceに転送
        - ここまでする必要があるか？？調べる
        - 設定値に基づいて利用しているクラウドまたは自身が用意したロードバランサをプロビジョンしアプリと紐づけて公開する
        - オンプレでのロードバランサの選択肢としてMetalLB,Octavia Ingress Contollerがある
- Ingressのコントローラーの種類
    - Ingress Nginx Controller
        - 利用実績多い
    - Traefik Kubernetes Ingress provider
        - Traefikをデータプレーンに活用したIngressコントローラー。エンドポイントの一覧とステータスやパス設定を含む各エンドポイントの詳細情報、有効になっているTraefikの機能一覧など確認できる
    - Emissary-ingress
        - Envoy Proxyをデータプレーンに活用したIngressコントローラー。通常のIngressリソースに加え、どのポートとプロトコルで接続を受け付けるかを記述したListener、ホスト名とURLを記述したMappling、ホスト名を定義したHostの3つのカスタムリソースを使える
    - Contour
        - Envoy Proxyをデータプレーンに活用したIngressコントローラー。通常のIngresリソースへの対応に加え、HTTPProxyというカスタムリソースを用意しており、そのカスタムリソースを使って重みつきルーティンやCookieの書き換え機能などできる
    - HAProxy Kubernetes Ingress Controller
        - HAProxyをデータプレーンに活用したIngressコントローラ。Backend、Default、Globalカスタムリソースを使って、通信の振り分け方法やHAProxy本体を設定変更できる
    - ロードバランサ本体もPodで動作するため、導入時はそれを考慮した十分なリソース量が割り当てられているか確認する
    - 一般的にロードバランサにはCORSの設定やバックエンドとの通信プロトコルの指定といった機能が用意されている。これらを設定するにはアノテーションを利用する
- Gateway API
    - ロードバランサの作成削除など操作はクラスタ運用担当者、どのリクエストをどのサービスで受け付けるかはアプリケーション開発者が担当。しかしIngressではそれらが1つに集約している。それらを分割したもの。
    - HTTPRouteを使うとトラフィックの分割もできるので、カナリアやブルーグリーンデプロイも実現可能
    - Cookieについての転送条件の指定などもある
    - 現時点ではNginx Kubernetes GatewayやIstioなどがGatewayAPIに対応している.対応表は[こちら](https://gateway-api.sigs.k8s.io/implementations/#nginx-gateway-fabric)
- gRPCの負荷分散
    - IngressNginxControllerではIngressリソースにnginx.ingress.kubernetes.io/backend^protocol:”GRPC”アノテーションを指定してHTTP2機能を有効にできる
- 送信元IPアドレスの特定方法
    - クラスタ外部からのPod宛のリクエストがノードに到達した際、そのノードに存在する対象のPodにそのままリクエストが転送されるのでなく、再度送信先Podが存在するノード間で通信が割り振られる。その後送信元IPアドレスは最初に到達したノードのIPアドレスに変更される。つまり、リクエストがPodに到達した際、リクエストの送信元IPアドレスはリクエストを送信したクライアントのIPアドレスではなく、リクエストを中継したIPアドレスになる、
    - 送信元IPアドレスを保持する方法として、service.spec.externalTrafficPolicyの値をlocalにすると、送信元IPアドレスの書き換えを防げる
    - CNIプラグインにより送信元IPアドレスを保持する方法もある。
        - CiliumやCalico
    - Ingressリソースを使って送信元IPアドレスを保持する方法もある
        - ロードバランサにXFFの付与を設定する
        - ConfigMapの設定ファイルに記述する方法やIngressのアノテーションに記述する方法もある

## マニフェストのCI

- マニフェストのシンタックスの確認
    - [kubeconform](https://github.com/yannh/kubeconform)で確認できる
- マニフェストが特定のポリシーに合致しているかの確認
    - [conftest](https://github.com/open-policy-agent/conftest)や[GateKeeper](https://github.com/open-policy-agent/gatekeeper)など
        - 他にも、requests.cpuが設定されているか、Deploymentに対応するHPAがあるか、Serviceに紐づいているPodにはLifecycle.prestopにSleepが設定されているかなどベストプラクティスのチェックができる
- 廃止・削除されたAPI Versionを利用しているか確認
    - [pluto](https://github.com/FairwindsOps/pluto)を使用することで自動的に検知できる

## アプリケーションコードのCI

- アプリケーションのテスト
- コンテナイメージのビルド
- タグの付与
- 脆弱性スキャン、SBOMの生成
    - trivyを使う
    - OSパッケージ(rpm,deb)やアプリパッケージ(pip,npm,gomod)などの部品表(SBOM)を保存しておく。SBOMが取得できていれば、脆弱性が新しく発見された場合に対象となっているパッケージを含むコンテナイメージを抽出することで、特定できる
    - trivyはSBOMを取得できる
    - 脆弱性スキャンはコンテナレジストリ側で定期的に実行できる
    - タイミングとして、コンテナイメージビルド時のCI時やコンテナレジストリにプッシュ後など大別できる。CIのタイミングで実施する場合はPull Request時のチェックで失敗させることでアプリ開発者が修正しやすい環境を整えられる
- コンテナレジストリへのプッシュ
    - コミットハッシュごとにイメージをプッシュしていくと、コンテナレジストリのストレージ費用が爆発的に増加するため、コミットハッシュごとのコンテナイメージのプッシュはブランチへのマージコミットごとに行う工夫が必要
    - 保存されているコンテナイメージを自動的に削除する機能がレジストリによってある
        - 直近でプッシュされたN個のコンテナイメージ以外削除するなどルールを設ける
        - コンテナレジストリも[事例あり](https://tech.pepabo.com/2021/03/30/create-original-github-actions/)

## GitOpsとCIOpsの違い

- 両方とも「コード」と「コンフィグ」用のリポジトリを用意し、それぞれCIを実施
- 違いは「コンフィグ」用のリポジトリに保存されているマニフェストをKubernetesにデプロイする部分
    - CIOpsの場合
        - Kubernetesクラスタ外にあるJenkins、Github ActionsといったCIツールでデプロイする
    - GitOpsの場合
        - Kubernetes上にDeploy Agent(Argo)を配置し、リポジトリからマニフェストの取得とKubernetesクラスタに対する適応を行う
- なぜ「GitOps」が推奨されているか？
    - ツールに与えるKubernetesクラスタの操作権限の範囲を狭められる
        - CIOpsの場合、すべてのマニフェストをKubernetesに適用するため、マニフェストを適用するCIツールにはすべてのNamespaceですべての操作が可能な強い権限を付与する必要がある。強い権限をKubernetesクラスタ外に用意する必要があり、CIツールにアクセスできるユーザやCIツールのジョブを定義するユーザが、クラスタに対するすべての権限を持ってしまう。クラスタ外からKubernetesAPIを叩ける用にする必要もある(KubernetesAPIにグローバルIPアドレスを付与したり、CIツールから疎結合のあるネットワークを構成するなど)
        - GitOpsはArgoCDにGitリポジトリに対するRead権限のみ与えればOK
    - 複雑性が避けられる
        - 組織が運用するKubernetesクラスタは開発用、ステージング用、プロダクション用がある。CIOpsの場合CI側でデプロイ先のクラスタを制御する必要があり、管理が大変。
- なお、複数のクラスタを一元的に管理する場合、CIOpsの方が適している場合もある。GitOpsのArgoCDも複数クラスタを管理する場合は、クラスタ外にあるArgoCDから複数のクラスタを管理するアーキテクチャになる

## コンフィグ用リポジトリのブランチ戦略

- 環境ごとにブランチを切り替えるパターン
- 環境ごとにディレクトリを切り替えるパターン
    - デメリットとしてステージング用のディレクトリとプロダクション用のディレクトリで差分が発生した場合に検知しづらい
    - 実際にはkustomizeのbaseディレクトリやHelmのChartやデフォルトのvalues.ymalなどのすべての環境で共通の設定は別で配置し、各ディレクトリから参照する形を取る
    - 例えばstg,prdディレクトリに配置されるkustomization.yamlは以下
    
    ```yaml
    resources:
    - ../../base
    patches:
    - ./stg-patches.yaml
    ```
    

## Helm

- Helmで利用可能なChartは[Artifact Hub](https://artifacthub.io/)で検索可能
- helmではChartのインストールと同時にそれをカスタマイズできるよう、valuesと呼ばれる設定値を変更できるようになっている。設定可能な値は、Artifact Hubから確認できる
    - 各Chartには依存するChartが指定されていることもある。例えばbitnami/wordpressのChartではbitnami/mariadb Chartが依存するChartとして指定されている
- Helm Chartには、Chart VersionとApp Versionがある
    - ChartVersionはテンプレートファイルの設定やvaluesで設定可能な値などによって変更されていく値
    - AppVersionはそのアプリケーション自体のバージョン
- Helmでは汎用的に利用可能なパッケージを作成しvaluesで個々の環境に合わせた設定を埋め込める
    - したがって、自分たちのアプリケーションでHelmを利用する場合、各アプリケーションチームやサービスチームが利用するための雛形のChartを作成するとよい
    - Chartを作る場合、helm create {app_name}で雛形ディレクトリができる
    - helmの構文は[こちら](https://helm.sh/docs/chart_template_guide/)参照
    
    ```yaml
    |-Chart.ymal
    |-charts
    |-templates
    ||-NOTES.txt
    ||-_helper.tpl
    ||-deployment.yaml
    ||-hpa.yaml
    ||-ingress.yaml
    ||-service.yaml
    ||-serviceaccount.yaml
    ||-tests
    |||-test-connection.yaml
    |
    |-values.yaml
    ```
    

## Argo CD

- GitOpsの同期設定にはApplicationリソースを用いる。
    - .spec.sourceに適用するマニフェストのリポジトリとパス、リビジョンを指定。リビジョンにはブランチのほかタグも指定できる
    - プライベートリポジトリの場合、事前にリポジトリの認証情報の登録を行う必要あり
    - マニフェスト生成ツールごとの設定もでき、例えばHelmの場合は[こちら](https://argo-cd.readthedocs.io/en/stable/operator-manual/declarative-setup/)
    - spec.destinationsには適用先のクラスタとNamespaceを指定する。ArgoCDが起動しているクラスタ以外のクラスタを対象にする場合は、事前にクラスタの認証情報を登録する必要あり
    - リポジトリとの自動同期の設定.spec.syncPolicy.automated
        - selfHealをtrueにすると自動的に変更時は同期される 推奨
        - pruneをtrueにするとリポジトリからリソースが削除された時リソースを自動削除
    - 他にもApplication同期時のオプション
        - 適用時のバリデーション
        - Server-Side-Applyの有無
        - Namespaceの自動作成の有無
            - 同一のNamespaceに対してApplicationをデプロイするケースではNamespaceがそれぞれのApplicationに含まれていると重複の警告が生じる、そのためNamespaceは自動生成推奨
        - Replaceの利用
- Applicationの同期設定を実行する時の手順、フェーズ
    - pre-sync,sync,post-sync,sync-failの4フェーズある。
    - Applicationの同期処理の前後や失敗時に任意の処理を実行するためのリソースフックと呼ばれる機能がある
        - リソースフックは、同期前にDBをマイグレーションしたり、同期後にテストをしたり、同期失敗時にロールバック処理をしたりするJobを作成する時に利用する
        - [リソースフック](https://argo-cd.readthedocs.io/en/stable/user-guide/resource_hooks/)でJobリソースを作成する時はアノテーションを付与する
    - waveを指定すると各フェーズでリソースを適用する順番を制御できる
- App of Apps
    - Applicationリソース自体もGitOpsで管理することが一般的。このことをApp of Apps
    - Applicationリソースのリポジトリの追加によって、自動的に新たなアプリケーションをKubernetesに追加できる。クラスタの再作成時にはApplicationリソースを目止めている大元の	Applicationリソースのみ(applications.yaml)をKubernetesに適用して、システム全体を復元する
    - App of Appsのディレクトリ構成 **[Helm Example](https://argo-cd.readthedocs.io/en/stable/operator-manual/cluster-bootstrapping/#helm-example)**
    
    ```yaml
    ├── Chart.yaml
    ├── templates
    │   ├── guestbook.yaml
    │   ├── helm-dependency.yaml
    │   ├── helm-guestbook.yaml
    │   └── kustomize-guestbook.yaml
    └── values.yaml
    ```
    
- GitOpsの状態検知と通知
    - ArgoCDにはApplicationの状態を通知する拡張コンポーネントの[ArgoCD Notifications](https://argocd-notifications.readthedocs.io/en/stable/)がある
- 複数クラスタや複数アプリケーションの効率的な管理
    - ArgoCD [ApplicationSet](https://argo-cd.readthedocs.io/en/stable/user-guide/application-set/)という複数クラスタに対して同一のApplicationをデプロイしたり、特定のクラスタに対して、さまざまなアプリケーションをデプロイしたりなど、複数のApplicationを効率的に管理するためのコンポーネントある
    - これによりmanifestsディレクトリ以下に新たに管理対象のmanigestを追加した時、Applicationリソースを作成することなく自動的にArgoCDで管理させることができる
    - 特定のラベルを持つクラスタに対してのみApplicationリソースを生成、ラベルやアノテーションの情報をApplicationリソースの名前やvaluesに利用したりできる
- KustomizeやHelmはイメージにないふうされているものもあるため、バージョンが固定になる。バージョンによって利用できるCLIオプションやプラグインが異なることもあり、バージョンを切り替えたいケースは以下3手順
    - argocd-repo-serverにバイナリをマウントする
    - argocd/argocd-cm ConfigMapにバイナリのパスを設定する
    - Applicationリソースごとに特定のバージョンを利用するよう設定する

## External Secrets

- kubernetesのOperatorとして動作する
- インストール方法はHelmのChartがある
- Secret Storeは参照したい秘匿情報が格納されている場所(AWS Sercrets Managerなど)をプロバイダとして定義する
- 複数のNamespaceにSecretを生成する必要がある場合はClusterExternalSecretを定義する
- `SecretStore`でプロバイダを定義して、`ExternalSecret`で参照するSecretStoreを記載
- Ownerを設定することでExternalSecretが削除された場合に生成していたSecretも削除される
- Mergeを設定するとSecretは生成せず、すでに存在するSecretに対して値を書き込む
- Kubernetesクラスタをマルチテナントで使用している場合の2パターン
    - 各Namespaceで個別にSecretStoreを管理する方法
    - 各Namespace内でSecretStoreの作成を許可せずに、クラスタ管理者が管理するClusterSecretStoreを各Namespaceから参照して使用する方法
    - どちらが良いとは言えないが、組織形態によって判断するべき。例えば、アプリを運用する組織と秘匿情報を管理する組織が分離している婆はClusterSecretStoreのパターンの方が良き
- SecretStoreに保存された値がJSON形式だった場合、`remoteRef`で特定の箇所から値を取得できる

## ExternalDNS

- serviceやingressを作成すると、IPアドレスが自動で払い出される。IPアドレスのままだと扱いにくい場合があるため、ExternalDNSを使う。
- KubernetesのServiceやIngressをDNSプロバイダに対して同期するツール。Kubernetesのクラスタで動作するPODで、外部に公開するKubernetesのアプリケーションとDNSプロバイダーを同期する　　おそらく外部のDNSプロバイダと連携するもの。Route53など
- helmChartも用意されている

## Cert Manager

- 証明書を発行するバックエンドから証明書を発行更新する作業を自動的に行う
- AdmissionWebhookで使用する証明書の発行やリソースへの埋め込みを特定のアノテーションに付与することでCert Managerに移譲も可能
- HTTP-01とDNS-01チャレンジがサポートされている
- Issuer
    - Lets EncreyptやZeroSSLなどの亜k吸えす情報に加えHTTP-01でチャレンジするWebサーバの使用、DNS-01へあのアクセス情報を記述する
    - Issuerはnamespaceごと、ClusterIssuerはクラスタごと
    - Issuerを作成すると、証明書の発行時にACMEサーバからのリクエストを受け付けるIngressリソースとPodを作成する。Ingressは証明書のドメインからIngressのIPアドレスを解決できるようにしておく必要がある。IngressのIPアドレスをインターネットから解決できるように動的にDNSサーバに登録するにはExternalDNSを利用する
    - 自己署名証明書を発行する場合、.spec.acmeを指定せず、.spec.selfSignedを指定する
        - 自己署名証明書はKubernetes上のサービスメッシュにおけるmTLSにおいて、クライアント側の証明書を発行する時に有用。Lets EncryptやZeroSSLでもクライアント証明書の発行は可能だが、発行できる数に制限がかかっているケースもあり、クライアント側のアプリケーションがスケールした場合に発行が追いつかない可能性がある。
        - 自己署名証明書を利用してmTLSを行う場合、クライアント証明書の発行に利用したルート証明書をサーバ側にインストールする必要がある
- Certificate
    - 証明書に必要なドメインや有効期限、暗号化方式などを記述する
    - mTLSで使用するクライアント証明書を発行する場合、spec.usagesでclient authを指定することでクライアントの証明でも使用可能な証明書が発行される
    - Certificateで作成された証明書はCert Managerが更新処理を実施し、成功すればSecretリソースが更新される。これにより、ユーザーは証明書の更新管理をCert Managerに譲渡できる
    - 証明書の秘密鍵などを外部に漏洩してしまった場合は自動更新を待たずすぐに更新すべきなので、kubectlプラグインで手動更新する。
    - Cert Managerを利用すれば、Ingress用の証明書の発行や更新を自動化することも可能。アノテーションにissureを指定する。
    - またAdmissionWebConfigurationリソースのチェックや変更を行うことができ、「Certificateリソースから作成された証明書のSecretリソースを手動で確認し、AdmissionWebConfigurationマニフェストのcaBundleにコピーする」といった手間削減できる