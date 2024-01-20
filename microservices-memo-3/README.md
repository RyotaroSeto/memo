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
