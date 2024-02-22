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
