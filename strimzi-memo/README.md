## 1.1 Kafkaの機能

- 極めて高いスループットと低遅延でデータを共有するマイクロサービスおよびその他のアプリケーション
- メッセージの順序の保証
- データストレージからのメッセージの巻き戻し/再生によるアプリケーションの状態の再構築
- Key-Value ログの使用時に古いレコードを削除するためのメッセージ圧縮
- クラスタ構成における水平方向のスケーラビリティ
- フォールト トレランスを制御するためのデータの複製
- 大量のデータをすぐにアクセスできるように保持

## **1.2. Kafka の使用例**

- イベント駆動型アーキテクチャ
- アプリケーションの状態の変更をイベントのログとしてキャプチャするイベント ソーシング
- メッセージブローカリング
- Web サイトのアクティビティの追跡
- メトリクスによる運用監視
- ログの収集と集計
- 分散システムのログをコミットする
- アプリケーションがリアルタイムでデータに応答できるようにするストリーム処理

## **1.3. Strimzi が Kafka をサポートする方法**

- **Strimzi はKubernetes上でKafkaを実行するためのコンテナー イメージとオペレーターを提供する**
- オペレーターは以下のプロセスを簡素化する
    - Kafka クラスターのデプロイと実行
    - Kafka コンポーネントのデプロイと実行
    - Kafka へのアクセスの構成
    - Kafka へのアクセスの保護
    - Kafka のアップグレード
    - ブローカーの管理
    - トピックの作成と管理
    - ユーザーの作成と管理

## **2. KafkaのStrimziデプロイメント**

- Kafka コンポーネントは、Strimzi ディストリビューションを使用して Kubernetes にデプロイするために提供されている。 Kafkaコンポーネントは通常、可用性を確保するためにクラスターとして実行される
- Kafka コンポーネントを組み込んだ一般的なデプロイメントは以下
    - ブローカーノードの**Kafkaクラスター**
    - **複製された ZooKeeper インスタンスのZooKeeper**クラスター
    - 外部データ接続用の**Kafka Connectクラスター**
    - セカンダリ クラスター内の Kafka クラスターをミラーリングするための**Kafka MirrorMakerクラスター**
    - 監視用に追加の Kafka メトリクス データを抽出する**Kafka Exporter**
    - Kafka クラスターに対して HTTP ベースのリクエストを行うための**Kafka ブリッジ**
    - **クルーズ コントロール**によるブローカー ノード間のトピック パーティションの再バランス
- これらのコンポーネントのすべてが必須ではないが、少なくともKafkaとZooKeeperは必要。 MirrorMakerやKafka Connect など、一部のコンポーネントは Kafka なしでデプロイできる。

## **2.1. Kafka コンポーネントのアーキテクチャ**

- ブローカーは、構成データの保存とクラスターの調整にZooKeeper を使用する。Kafka を実行する前に、ZooKeeperクラスターを準備する必要がある
- Apache ZooKeeper
    - ブローカーとコンシューマーのステータスを保存および追跡するクラスター調整サービスを提供するため、Kafka の中核的な依存関係。 ZooKeeper はコントローラーの選択にも使用される。
- Kafka Connect
    - *コネクタ*プラグインを使用して Kafka ブローカーと他のシステムの間でデータをストリーミングするための統合ツールキット
    - Kafka Connect は、コネクタを使用してデータをインポートまたはエクスポートするために、データベースなどの外部データソースまたはターゲットとKafkaを統合するためのフレームワークを提供
- Kafka MirrorMaker
    - データ センター内またはデータセンター間で 2 つの Kafka クラスター間でデータをレプリケートする
    - MirrorMaker は、ソース Kafka クラスターからメッセージを取得し、ターゲットKafka クラスターに書き込みむ
- Kafka Bridge
    - HTTP ベースのクライアントを Kafka クラスターと統合するためのAPIを提供
- Kafka Exporter
    - Kafka Exporter は、主にオフセット、コンシューマ グループ、コンシューマ ラグ、トピックに関連するデータを Prometheus メトリクスとして分析用に抽出。

![スクリーンショット 2024-02-16 9 51 24](https://github.com/RyotaroSeto/microservices-memo/assets/70475997/2fb2de9a-dc52-4a3f-9be5-89df97ceb5cb)


## **5. Kafka ブリッジインターフェイス**

- Kafka BridgeはHTTP ベースのクライアントが Kafka クラスターと対話できるようにする RESTful インターフェイスを提供。
- これは、クライアントアプリケーションが Kafka プロトコルを解釈する必要がなく、Strimzi への Web API 接続の利点を提供。

## **5.1. HTTPリクエスト**

- Kafka Bridgeは次のメソッドを使用して Kafka クラスターへの HTTP リクエストをサポート
    - トピックにメッセージを送信
    - トピックからメッセージを取得
    - トピックのパーティションのリストを取得
    - コンシューマを作成および削除
    - コンシューマをトピックにサブスクライブして、コンシューマがそれらのトピックからのメッセージの受信を開始できるようにする
    - コンシューマが購読しているトピックのリストを取得
    - 消費者をトピックから購読解除
    - コンシューマにパーティションを割り当てる
    - コンシューマオフセットのリストをコミット

## **6. Strimzi 演算子**

- Operatorは、Kubernetesアプリケーションをパッケージ化、デプロイ、管理する方法。これらは、Kubernetes API を拡張し、特定のアプリケーションに関連付けられた管理タスクを簡素化する方法を提供
- Strimzi オペレーターは、Kafka デプロイメントに関連するタスクをサポート
- Strimzi カスタムリソースは、展開構成を提供。これには、Kafka クラスター、トピック、ユーザー、その他のコンポーネントの構成が含まれる
- Strimzi オペレーターは、カスタム リソース構成を利用して、Kubernetes 環境内でKafkaコンポーネントを作成、構成、管理する。オペレーターを使用すると、手動介入の必要性が減り、Kubernetes クラスターで Kafka を管理するプロセスが合理化できる
- **StrimziはKubernetesクラスター内で実行されているKafkaクラスターを管理するために次のオペレーターを提供**
    - Cluster Operator
        - Apache Kafka クラスター、Kafka Connect、Kafka MirrorMaker、Kafka Bridge、Kafka Exporter、Cruise Control、および Entity Operator をデプロイおよび管理
    - Entity Operator
        - トピックオペレーターとユーザーオペレーターで構成
    - Topic Operator
        - Kafka トピックを管理
    - User Operator
        - Kafka ユーザーを管理

![スクリーンショット 2024-02-16 10 12 41](https://github.com/RyotaroSeto/microservices-memo/assets/70475997/46184447-fe00-41f3-ada2-072943e13844)


## **6.1.**Cluster Operator

- Strimzi は、Cluster Operator を使用してクラスターを展開および管理する
- デフォルトではStrimzi をデプロイすると単一のCluster Operatorレプリカがデプロイされる
- クラスターはカスタムリソースを使用してデプロイされる。たとえば、Kafkaクラスターをデプロイするには
    - `Kafka`クラスター構成のリソースが Kubernetes クラスター内に作成される
    - Cluster Operator は、リソースで宣言されている内容に基づいて、対応する Kafka クラスターをデプロイ
- Cluster Operator は、`Kafka`リソースの構成を通じて次の Strimzi オペレーターをデプロイすることもできる
    - `KafkaTopic`
        - カスタムリソースを通してトピック管理を提供する
    - `KafkaUser`
        - カスタムリソースを通じてオペレーター スタイルのユーザー管理を提供

![スクリーンショット 2024-02-16 10 21 21](https://github.com/RyotaroSeto/microservices-memo/assets/70475997/e8fbf24f-c173-421e-9345-e386c66124cc)


## **6.2.Topic** Operator

- KafkaTopicリソースを通じて Kafka クラスター内のトピックを管理する方法を提供
- `KafkaTopic`アプリケーションのデプロイメントの一部としてを宣言すると、Topic Operator が Kafka トピックを管理
- Topic Operatorは次のモードで動作する
    - 単方向モード
        - Topic Operatorが`KafkaTopic`リソースを通じてトピックをのみ管理する。このモードは ZooKeeper を必要とせず、KRaft モードでの Strimzi の使用と互換性がある
    - 双方向モード
        - Topic Operatorが`KafkaTopic`を通じて、または Kafka で直接トピックを更新できる。Topic Operatorは両方のソースが更新されて変更が反映されることを保証する。このモードでは、クラスター管理に ZooKeeper が必要

![スクリーンショット 2024-02-16 10 22 23](https://github.com/RyotaroSeto/microservices-memo/assets/70475997/c1dc28b8-e980-4e12-98e8-e753a4c3bc5b)


## **6.3.User** Operator

- User Operatorは`KafkaUser`リソースを通じて Kafka クラスター内のユーザーを管理する方法を提供
- `KafkaUser`リソースを監視し、Kafka ユーザーが Kafka クラスター内で適切に構成されていることを確認することにより、Kafka クラスターの Kafka ユーザーを管理する
- `KafkaUser`作成、削除、または変更されると、User OperatorはKafka ユーザーに対して対応するアクションを実行
- ユーザーの認証および認可メカニズムを指定できる。
    - たとえば、ユーザーがブローカーへのアクセスを独占しないように、Kafka リソースの使用を制御する*ユーザー クォータ*を構成することもできる

## **7.1.カスタムリソース**

- Kafka Topicのカスタムリソース

```yaml
apiVersion: kafka.strimzi.io/v1beta2
kind: KafkaTopic
metadata:
  name: my-topic
  labels:
    strimzi.io/cluster: my-cluster
spec:
  partitions: 1
  replicas: 1
  # ...
```

## **7.2.共通構成**

- ブートストラップサーバー
- CPUとメモリのリソース
- ロギング
- ヘルスチェック
- JVMオプション
- ポッドのスケジューリング
- **一般的な構成を示す YAML の例**

```yaml
apiVersion: kafka.strimzi.io/v1beta2
kind: KafkaConnect
metadata:
  name: my-cluster
spec:
  # ...
  bootstrapServers: my-cluster-kafka-bootstrap:9092
  resources:
    requests:
      cpu: 12
      memory: 64Gi
    limits:
      cpu: 12
      memory: 64Gi
  logging:
    type: inline
    loggers:
      connect.root.logger.level: INFO
  readinessProbe:
    initialDelaySeconds: 15
    timeoutSeconds: 5
  livenessProbe:
    initialDelaySeconds: 15
    timeoutSeconds: 5
  jvmOptions:
    "-Xmx": "2g"
    "-Xms": "2g"
  template:
    pod:
      affinity:
        nodeAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
            nodeSelectorTerms:
              - matchExpressions:
                  - key: node-type
                    operator: In
                    values:
                      - fast-network
  # ...
```

## **7.3. Kafka クラスター構成**

- **Storage**
    - Kafka と ZooKeeper はデータをディスクに保存する
    - Strimzi にはStorageClassを通じてプロビジョニングされたブロック ストレージが必要
    - ストレージのファイルシステム形式は*XFS*または*EXT4*である必要があり、次の 3 種類のデータストレージがサポートされている
        - 一時的 (開発のみに推奨)
            - 一時ストレージには、インスタンスの存続期間中データが保存されます。インスタンスを再起動するとデータが失われる
        - 持続的
            - 永続ストレージは、インスタンスのライフサイクルに関係なく、長期的なデータ ストレージに関連する
        - JBOD
            - JBOD を使用すると、複数のディスクを使用して各ブローカーにコミット ログを保存できる
- **Listeners**
    - クライアントが Kafka クラスターに接続する方法を構成する
    - Kafka クラスター内の各リスナーに一意の名前とポートを指定することで、複数のリスナーを構成できる
    - 次のタイプのリスナーがサポートされている
        - Kubernetes 内でアクセスするための内部リスナー
        - Kubernetes の外部にアクセスするための外部リスナー
    - リスナーの TLS 暗号化を有効にし、[認証を](https://strimzi.io/docs/operators/latest/overview#security-configuration-authentication_str)構成できる
    - 内部リスナーは、`internal`タイプを指定することで Kafka を公開する
        - `internal`同じ Kubernetes クラスター内で接続する
        - `cluster-ip`ブローカーごとの`ClusterIP`サービスを使用して Kafka を公開する
    - 外部リスナーは、外部を指定することで Kafka を公開します
        - route
        - loadbalancer
        - nodeport
        - ingress

**Kafka 構成を示す YAML の例**

```yaml
apiVersion: kafka.strimzi.io/v1beta2
kind: Kafka
metadata:
  name: my-cluster
spec:
  kafka:
    # ...
    listeners:
      - name: tls
        port: 9093
        type: internal
        tls: true
        authentication:
          type: tls
      - name: external1
        port: 9094
        type: route
        tls: true
        authentication:
          type: tls
    # ...
    storage:
      type: persistent-claim
      size: 10000Gi
    # ...
    rack:
      topologyKey: topology.kubernetes.io/zone
    config:
      replica.selector.class: org.apache.kafka.common.replica.RackAwareReplicaSelector
    # ...
```

## **7.4. Kafka ノード プールの構成**

- ノード プールは、Kafka クラスター内の Kafka ノードの個別のグループを指します。ノード プールを使用すると、同じ Kafka クラスター内でノードに異なる構成を持たせることができる

**ノードプール構成を示す YAML の例**

```yaml
apiVersion: kafka.strimzi.io/v1beta2
kind: KafkaNodePool
metadata:
  name: pool-a
  labels:
    strimzi.io/cluster: my-cluster
spec:
  replicas: 3
  roles:
    - broker
  storage:
    type: jbod
    volumes:
      - id: 0
        type: persistent-claim
        size: 100Gi
        deleteClaim: false
```

## **7.8. Kafka ブリッジの構成**

- Kafka Bridge 構成には、接続先の Kafka クラスターのブートストラップ サーバー仕様と、必要な暗号化および認証オプションが必要
- [Kafka Bridge のコンシューマおよびプロデューサの構成は、 「コンシューマ用の Apache Kafka 構成ドキュメント」](https://kafka.apache.org/documentation/#consumerconfigs)および[「プロデューサ用の Apache Kafka 構成ドキュメント」](https://kafka.apache.org/documentation/#producerconfigs)で説明されているように、標準
- Kafka Bridge は、Cross-Origin Resource Sharing (CORS) の使用をサポート。 CORS は、ブラウザーが複数のオリジンから選択されたリソース (たとえば、異なるドメイン上のリソース) にアクセスできるようにする HTTP メカニズムです。 CORS の使用を選択した場合は、Kafka ブリッジを介して Kafka クラスターと対話するための、許可されるリソースのオリジンと HTTP メソッドのリストを定義できます。リストは、`http`Kafka Bridge 構成の仕様で定義されています。

KafkaBridge**構成を示す YAML の例**

```yaml
apiVersion: kafka.strimzi.io/v1beta2
kind: KafkaBridge
metadata:
  name: my-bridge
spec:
  # ...
  bootstrapServers: my-cluster-kafka:9092
  http:
    port: 8080
    cors:
      allowedOrigins: "https://strimzi.io"
      allowedMethods: "GET,POST,PUT,DELETE,OPTIONS,PATCH"
  consumer:
    config:
      auto.offset.reset: earliest
  producer:
    config:
      delivery.timeout.ms: 300000
  # ...
```
