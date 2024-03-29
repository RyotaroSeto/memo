# Istio

- モノリシックアプリケーションならシンプルな通信経路のため特に意識しなかったがマイクロサービスアプリケーションは通信経路が複雑である。
- その複雑な通信経路により発生する課題は以下
  - サービス間の Discovery
  - Timeout
  - Retry
- サービスが数個であれば個別に対応することが可能だが、サービスが数百、数千と増えるにつれてより複雑性が増すため、難易度も高くなる
- サービスの開発者はビジネスロジックの実装に集中することが理想であり、アプリケーション上記のような課題を解決する実装を避けたい
- これを実現する方法が Servie Mesh の Istio

## Istio is 何？

- Istio は data plane と control plane という 2 つのコンポーネントから構成されている
- data plane では Sidecar pattern を用いてアプリケーションと一緒に Envoy Proxy のコンテナをデプロイ
  - この Envoy proxy がアプリケーションのすべてのトラフィックをインターセプトすることでサービス間の通信における機能を提供
- control plane では設定を管理し、data plane からメトリクスを受け取りながら Envoy proxy が提供する API を介して動的に設定を変更
  - data plane はサーバーを再起動することなく処理を変更することができる

## Istio が解決する課題

### Traffic Control

### Reliability

### Security

### gRPC Load Balancing

**課題**

- 現在 Kubernetes は Service resource (L4 Load Balancer) しか提供していないため、HTTP/2 で通信を行う gRPC は Load Balancing ができない。

**解決策 1**

- [Headless Service](https://kubernetes.io/docs/concepts/services-networking/service/#headless-services) を使用して Client 側で 宛先となる Pod の IP address を管理し、Client-side Load Balancing を行う。
- この方法には以下 2 つの課題がある
  - アプリケーションを開発する際、このライブラリの導入を忘れないようにしなければならない。導入を忘れた場合、うまくリクエストが分散されず、思わぬ障害につながる可能性がある。
  - Go 以外で作成されているサービスへの対応の検討が必要。サービスの開発者が Go 以外を選択した場合、自ら Client Load Balancing を行う方法を検討する必要。

**解決策 2**

- Istio を使う
  - Istio は HTTP/2 の LoadBalancing に対応しているため、サービスは Istio を使用するだけで gRPC Load Balancing を実現できる
  - Load Balancing は Envoy proxy 上で行われるので、サービスがどの言語を使用しているかに依存しない

### Istio Ingress Gateway

- Istio Ingress Gateway は実体は Envoy Proxy
- アプリケーションと一緒にデプロイされる Envoy proxy と同じ control plane となる Istiod と、 API を介してコミュニケーションを行い設定を更新する
- どのようなエンドポイントを公開してどのサービスにリクエストをプロキシするかの情報は Istio の CRD である Gateway, VirtualService を使って行う。stiod はこれらの CRD から情報を収集して、Istio Ingress Gateway へ渡す。
- 各リソースは各サービスの namespace に配置できる。

## Istio Ambient Mesh

- 従来の Istio のサイドカーパターンには以下の問題があった
  - サイドカーとして Envoy Proxy を注入する必要のため、サイドカーのインストールやバージョンアップ時にアプリケーションの Pod の再起動が必要
  - サイドカーは関連するワークロード専用であるため、それぞれの Pod が利用する可能性のある量の CPU とメモリ リソースをプロビジョニングする必要がある。それによりクラスター全体でリソースが効率的に利用されない可能性がある。
  - Istio のサイドカーで通常行われるトラフィックキャプチャと HTTP 処理は、計算量が多く、HTTP の実装に準拠していない一部のアプリケーションを壊す可能性がある

### Ambient Mesh について

**サイドカーアーキテクチャとの違い**

- トラフィックルーティング

  - アンビエントモード、ワークロードは次の 3 つのカテゴリに分類できる

    1. Uncaptured:これはメッシュ機能を有効にしていない標準的な Pod
    2. Captured:これは、ztunnel によってインターセプトされたトラフィックを持つ Pod。ネームスペースに istio.io/dataplane-mode=ambient ラベルを設定することで、Pod をキャプチャできる
    3. Waypoint enabled:これは「キャプチャ」され、ウェイポイント・プロキシがデプロイされた Pod。ウェイポイントは、デフォルトでは同じネームスペース内のすべてのポッドに適用される。オプションで、ゲートウェイの istio.io/for-service-account アノテーションを使用して、特定のサービスアカウントのみに適用するように設定できる。ネームスペースのウェイポイントとサービスアカウントのウェイポイントの両方がある場合、サービスアカウントのウェイポイントが優先される。

  - ztunnel routing

    - Outbound

      - キャプチャされたポッドがアウトバウンドリクエストを行うと、リクエストを転送する場所と方法を決定する ztunnel に透過的にリダイレクトされる。一般的に、トラフィックのルーティングは Kubernetes のデフォルトのトラフィックのルーティングと同じように振る舞う。Service へのリクエストは Service 内のエンドポイントに送られ、Pod IP への直接のリクエストはその IP に直接送られます。
      - ただし、宛先の機能によっては異なる動作が発生する。宛先もキャプチャされているか、そうでなければ Istio プロキシ機能（サイドカーなど）を持っている場合、リクエストは暗号化された HBONE トンネルにアップグレードされる。デスティネーションがウェイポイントプロキシを持つ場合、HBONE にアップグレードされることに加えて、リクエストはそのウェイポイントに転送(forward)される。
      - サービスに対するリクエストの場合、特定のエンドポイントが選択され、そのエンドポイントがウェイポイントを持っているかどうかが判断されることに注意する。しかし、ウェイポイントがある場合、リクエストは選択されたエンドポイントではなく、サービスのターゲットデスティネーションで送信される。

    - Inbound
      - キャプチャされたポッドがインバウンドリクエストを受け取ると、透過的に ztunnel にリダイレクトされる。ztunnel がリクエストを受け取ると、Authorization Policies を適用し、リクエストがポリシーに合う場合のみリクエストを転送する
      - ポッドは HBONE トラフィックまたはプレーンテキストトラフィックを受信できる。デフォルトでは、どちらも ztunnel によって受け入れられる。Authorization Policies が評価される時、プレーンテキストリクエストはピア ID を持たないので、ユーザーは全てのプレーンテキストトラフィックをブロックするために ID (任意の ID または特定の ID) を要求するポリシーを設定できる。
      - デスティネーションがウェイポイントを有効にしている場合、全てのリクエストはポリシーが適用されるウェイポイントを通らなければならない。ztunnel はこれが発生することを確認する。しかし、エッジケースもあり、行儀の良い HBONE クライアント（別の ztunnel や Istio サイドカーなど）はウェイポイントに送信することを知っているが、他のクライアント（メッシュ外のワークロードなど）はウェイポイントプロキシについて何も知らない可能性が高く、リクエストを直接送信する。これらの直接呼び出しが行われると、ztunnel は、ポリシーが適切に実施されるように、それ自身のウェイポイントにリクエストを "ヘアピン "する。

  - waypoint routing
    - ウェイポイントは HBONE リクエストのみを受信する。リクエストを受信すると、ウェイポイントは、自分が管理している Pod か、自分が管理している Pod を含む service をターゲットにしていることを確認
    - Pod への直接リクエストの場合、ポリシーが適用された後、リクエストは単に直接転送される。
    - 例えば、以下のポリシーは、echo サービスへのリクエストが echo-v1 に転送されることを保証する
    ```yaml
    apiVersion: gateway.networking.k8s.io/v1beta1
    kind: HTTPRoute
    metadata:
      name: echo
    spec:
      parentRefs:
        - group: ''
          kind: Service
          name: echo
      rules:
        - backendRefs:
            - name: echo-v1
              port: 80
    ```

- Istio Ambient Mesh は機能を下記のように２つのレイヤーに分割している
  - Secure overlay Layer
    - 各ワーカーノードで起動する共有のエージェント(ztunnel エージェント)が担当
  - L7 Processing Layer
    - waypoint proxy というコンポーネントが担当
- サイドカーとして Envoy を利用する場合よりも、リソースを効率良く利用できることが期待される
